package com.aminfathi.nava.network

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ProviderException(val category:String, val retryable:Boolean=false): IOException(category)
class OpenAIProvider private constructor(private val baseUrl:String, private val key:()->String, private val testInterceptor:Interceptor?=null) {
    constructor(key:()->String):this("https://api.openai.com/v1/",key)
    companion object { internal fun forTest(interceptor:Interceptor)=OpenAIProvider("https://api.openai.com/v1/",{ "synthetic-key" },interceptor) }
    private val client=OkHttpClient.Builder().connectTimeout(20,TimeUnit.SECONDS).readTimeout(75,TimeUnit.SECONDS)
        .callTimeout(150,TimeUnit.SECONDS).apply { testInterceptor?.let {addInterceptor(it)} }.retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()
    private val json=Json { ignoreUnknownKeys=true }
    fun cancel()=client.dispatcher.cancelAll()
    private fun request(path:String, body:JsonObject?=null):Request {
        val token=key(); if(token.isBlank()) throw ProviderException("key")
        return Request.Builder().url(baseUrl+path).header("Authorization","Bearer $token")
            .apply { if(body!=null) post(body.toString().toRequestBody("application/json".toMediaType())) }.build()
    }
    private suspend fun <T> execute(request: Request, authorize:suspend ()->Unit={}, consume:(Response)->T):T = coroutineScope {
        var attempt=0
        while(true) {
            ensureActive(); authorize()
            val call=client.newCall(request)
            val cancelJob=launch { try { awaitCancellation() } finally { call.cancel() } }
            try {
                return@coroutineScope withContext(Dispatchers.IO) {
                    call.execute().use { response ->
                        if(!response.isSuccessful) throw ProviderException(when(response.code) {
                            401,403->"auth"; 429->"quota"; 400,404,422->"model"; else->"network"
                        },response.code==429 || response.code>=500)
                        consume(response)
                    }
                }
            } catch(e:ProviderException) {
                if(!e.retryable || attempt>=2) throw e
                delay((1L shl attempt)*1200L); attempt++
            } catch(e:IOException) {
                currentCoroutineContext().ensureActive()
                throw ProviderException("network")
            } finally { cancelJob.cancel() }
        }
        @Suppress("UNREACHABLE_CODE") error("unreachable")
    }
    suspend fun models():List<String> = execute(request("models")) { r ->
        json.parseToJsonElement(r.body!!.string()).jsonObject["data"]!!.jsonArray
            .mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }.sorted()
    }
    private fun body(model:String,instructions:String,input:String,schema:JsonObject?,stream:Boolean)=buildJsonObject {
        put("model",model); put("store",false); put("stream",stream); put("max_output_tokens",4096)
        put("instructions",instructions)
        putJsonArray("input") { add(buildJsonObject { put("role","user"); put("content",input) }) }
        if(schema!=null) putJsonObject("text") { putJsonObject("format") {
            put("type","json_schema"); put("name","clinical_extraction"); put("strict",true); put("schema",schema)
        } }
    }
    private fun textOutput(response:Response):String {
        val result=json.parseToJsonElement(response.body!!.string()).jsonObject
        if(result["status"]?.jsonPrimitive?.content != "completed") throw ProviderException("format")
        return result["output"]?.jsonArray.orEmpty().flatMap { it.jsonObject["content"]?.jsonArray.orEmpty() }
            .filter { it.jsonObject["type"]?.jsonPrimitive?.content=="output_text" }
            .joinToString("") { it.jsonObject["text"]!!.jsonPrimitive.content }.ifBlank { throw ProviderException("format") }
    }
    suspend fun structured(model:String,instructions:String,input:String,schema:JsonObject,authorize:suspend ()->Unit={}):String =
        execute(request("responses",body(model,instructions,input,schema,false)),authorize) { textOutput(it) }
    suspend fun readImages(model:String,instructions:String,input:String,images:List<ByteArray>,schema:JsonObject,authorize:suspend ()->Unit={}):String {
        require(images.isNotEmpty() && images.size<=3)
        val payload=buildJsonObject {
            put("model",model); put("store",false); put("stream",false); put("max_output_tokens",8000)
            put("instructions",instructions)
            putJsonArray("input") { add(buildJsonObject {
                put("role","user")
                putJsonArray("content") {
                    add(buildJsonObject { put("type","input_text"); put("text",input) })
                    images.forEach { bytes -> add(buildJsonObject {
                        put("type","input_image")
                        put("image_url","data:image/jpeg;base64,"+java.util.Base64.getEncoder().encodeToString(bytes))
                    }) }
                }
            }) }
            putJsonObject("text") { putJsonObject("format") {
                put("type","json_schema"); put("name","document_reading"); put("strict",true); put("schema",schema)
            } }
        }
        return execute(request("responses",payload),authorize) { textOutput(it) }
    }
    suspend fun stream(model:String,instructions:String,input:String,authorize:suspend ()->Unit,onText:(String)->Unit):String =
        execute(request("responses",body(model,instructions,input,null,true)),authorize) { r ->
            val source=r.body!!.source(); val output=StringBuilder(); val event=StringBuilder(); var completed=false
            fun consumeEvent() {
                if(event.isEmpty()) return
                val data=event.toString().trim(); event.clear()
                if(data=="[DONE]") return
                val obj=runCatching { json.parseToJsonElement(data).jsonObject }.getOrElse { throw ProviderException("format") }
                when(obj["type"]?.jsonPrimitive?.content) {
                    "response.output_text.delta"->{ output.append(obj["delta"]!!.jsonPrimitive.content); if(output.length>5000) throw ProviderException("format"); onText(output.toString()) }
                    "response.completed"->completed=true
                    "response.failed","response.incomplete","error","response.refusal.delta"->throw ProviderException("format")
                }
            }
            while(!source.exhausted()) {
                val line=source.readUtf8Line() ?: break
                if(line.isBlank()) consumeEvent() else if(line.startsWith("data:")) {
                    if(event.isNotEmpty()) event.append('\n'); event.append(line.removePrefix("data:").trimStart())
                    if(event.length>1000000) throw ProviderException("format")
                }
            }
            consumeEvent()
            if(!completed || output.isEmpty()) throw ProviderException("network")
            output.toString()
        }
    suspend fun validateModel(model:String) {
        val schema=json.parseToJsonElement("""{"type":"object","properties":{"ok":{"type":"boolean"}},"required":["ok"],"additionalProperties":false}""").jsonObject
        val reply=structured(model,"Return JSON with ok=true. Synthetic capability check; no health data.","Connectivity test",schema)
        if(json.parseToJsonElement(reply).jsonObject["ok"]?.jsonPrimitive?.booleanOrNull!=true) throw ProviderException("model")
        stream(model,"Respond with the single word OK.","Synthetic streaming capability test",{}) {}
    }
}
