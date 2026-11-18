package com.aminfathi.nava

import com.aminfathi.nava.network.*
import com.aminfathi.nava.ai.Prompts
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Test
import org.junit.Assert.*

class OpenAIProviderTest {
    @Test fun modelDiscoveryDoesNotTransmitPatientData()=runBlocking {
        FakeTransport().use { server ->
            server.enqueue(FakeResponse().setBody("""{"data":[{"id":"synthetic-model"}]}"""))
            val api=OpenAIProvider.forTest(server)
            assertEquals(listOf("synthetic-model"),api.models())
            val request=server.takeRequest();assertEquals("GET",request.method);assertEquals(0L,request.body?.contentLength() ?: 0L)
        }
    }
    @Test fun storageIsDisabledAndNoProviderConversationIsCreated()=runBlocking {
        FakeTransport().use { server ->
            server.enqueue(FakeResponse().setBody("""{"status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":"{}"}]}]}"""))
            val api=OpenAIProvider.forTest(server)
            api.structured("synthetic","test","fictional",Prompts.extractionSchema)
            val request=server.takeRequest();val body=Json.parseToJsonElement(Buffer().also { request.body!!.writeTo(it) }.readUtf8()).jsonObject
            assertEquals(false,body["store"]!!.jsonPrimitive.boolean)
            assertNull(body["previous_response_id"]);assertNull(body["conversation"])
            assertEquals("/v1/responses",request.url.encodedPath)
        }
    }
    @Test fun streamRequiresCompletionEvent()=runBlocking {
        FakeTransport().use { server ->
            server.enqueue(FakeResponse().setBody("data: {\"type\":\"response.output_text.delta\",\"delta\":\"Partial\"}\n\n"))
            val api=OpenAIProvider.forTest(server)
            try { api.stream("test","test","fictional",{}) {};fail("Must reject truncated output") }
            catch(e:ProviderException) {assertEquals("network",e.category)}
        }
    }
    @Test fun streamCombinesMultilingualDeltas()=runBlocking {
        FakeTransport().use { server ->
            val data=listOf("سلام ","دوست عزیز").joinToString("") { "data: "+buildJsonObject{put("type","response.output_text.delta");put("delta",it)}+"\n\n" }+"data: {\"type\":\"response.completed\"}\n\n"
            server.enqueue(FakeResponse().setBody(data))
            val api=OpenAIProvider.forTest(server);val deltas=mutableListOf<String>()
            val result=api.stream("test","test","fictional",{}) {deltas+=it}
            assertEquals("سلام دوست عزیز",result);assertEquals(2,deltas.size)
        }
    }
    @Test fun authorizationRunsBeforeNetwork()=runBlocking {
        FakeTransport().use { server ->
            val api=OpenAIProvider.forTest(server)
            try { api.structured("test","test","fictional",Prompts.extractionSchema) {throw IllegalStateException("No consent")};fail("must block") }
            catch(_:IllegalStateException) {assertEquals(0,server.requestCount)}
        }
    }
    @Test fun documentReadingSendsImagesWithoutStore()=runBlocking {
        FakeTransport().use { server ->
            server.enqueue(FakeResponse().setBody("""{"status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":"{\"ocr\":\"AMH 1.2\",\"description\":\"Laboratory sheet\"}"}]}]}"""))
            val api=OpenAIProvider.forTest(server)
            val text=api.readImages("synthetic","copy","description_language: fa",listOf(byteArrayOf(1,2,3)),Prompts.documentSchema)
            assertTrue(text.contains("AMH"))
            val body=Json.parseToJsonElement(Buffer().also { server.takeRequest().body!!.writeTo(it) }.readUtf8()).jsonObject
            assertEquals(false,body["store"]!!.jsonPrimitive.boolean)
            val image=body["input"]!!.jsonArray.first().jsonObject["content"]!!.jsonArray[1].jsonObject
            assertEquals("input_image",image["type"]!!.jsonPrimitive.content)
            assertTrue(image["image_url"]!!.jsonPrimitive.content.startsWith("data:image/jpeg;base64,"))
        }
    }
    @Test fun authenticationErrorDoesNotLeakResponseBody()=runBlocking {
        FakeTransport().use { server ->
            server.enqueue(FakeResponse().setResponseCode(401).setBody("SYNTHETIC_SECRET_IN_ERROR"))
            val api=OpenAIProvider.forTest(server)
            try {api.models();fail("must fail")}
            catch(e:ProviderException) {assertEquals("auth",e.message);assertFalse(e.message!!.contains("SECRET"))}
        }
    }
}

private class FakeResponse {
    var code=200;var body=""
    fun setBody(value:String)=apply {body=value}
    fun setResponseCode(value:Int)=apply {code=value}
}
private class FakeTransport:Interceptor,java.io.Closeable {
    private val queued=java.util.ArrayDeque<FakeResponse>()
    private val requests=java.util.ArrayDeque<Request>()
    val requestCount get()=requests.size
    fun enqueue(response:FakeResponse) {queued.add(response)}
    fun takeRequest():Request=requests.removeFirst()
    override fun intercept(chain:Interceptor.Chain):Response {
        val request=chain.request();requests.add(request);val next=queued.removeFirst()
        return Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(next.code).message("Synthetic")
            .body(next.body.toResponseBody()).build()
    }
    override fun close() {}
}
