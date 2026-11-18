package com.aminfathi.nava.network

import com.aminfathi.nava.domain.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.util.concurrent.TimeUnit

class PortalException(val key: String): Exception(key)

/** Authenticated calls for shared inquiries. Bodies are not logged. */
class TourismClient {
    private val http = OkHttpClient.Builder().callTimeout(40, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val media = "application/json; charset=utf-8".toMediaType()

    fun base(raw: String): String? {
        val value = raw.trim().trimEnd('/')
        if (value.isEmpty()) return null
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        val host = uri.host ?: return null
        val loopback = host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2"
        if (uri.scheme == "https" || (uri.scheme == "http" && loopback)) return value
        return null
    }

    fun register(base: String, email: String, password: String, name: String) = auth(base, "register", email, password, name)
    fun login(base: String, email: String, password: String) = auth(base, "login", email, password, "")
    private fun auth(base: String, path: String, email: String, password: String, name: String): PortalSession {
        val body = buildJsonObject { put("email", email); put("password", password); put("displayName", name) }
        val raw = call(base, null, "api/auth/$path", body.toString())
        val obj = json.parseToJsonElement(raw).jsonObject
        return PortalSession(obj.string("token"), obj.string("role"), obj.string("displayName"))
    }
    fun hospitals(base: String, token: String) = json.decodeFromString<List<Hospital>>(call(base, token, "api/hospitals", null))
    fun clinicians(base: String, token: String) = json.decodeFromString<List<Clinician>>(call(base, token, "api/doctors", null))
    fun stays(base: String, token: String) = json.decodeFromString<List<Stay>>(call(base, token, "api/stays", null))
    fun rates(base: String, token: String) = json.decodeFromString<FxTable>(call(base, token, "api/rates", null))
    fun requests(base: String, token: String) = json.decodeFromString<List<CareRequest>>(call(base, token, "api/requests", null))
    fun submit(base: String, token: String, request: CareRequest, summary: CareSummary?): CareRequest {
        val body = buildJsonObject {
            put("id", request.id); put("pathway", request.pathway.name); put("hospitalId", request.hospitalId)
            put("doctorId", request.doctorId); put("note", request.note)
            put("documentIds", JsonArray(request.documentIds.map { JsonPrimitive(it) }))
            put("includeSummary", request.includeSummary); put("authorized", request.authorized)
            if (summary != null && request.includeSummary) put("summary", json.parseToJsonElement(json.encodeToString(summary)))
        }
        return json.decodeFromString(call(base, token, "api/requests", body.toString()))
    }
    fun transition(base: String, token: String, id: String, action: String) =
        json.decodeFromString<CareRequest>(call(base, token, "api/requests/$id/transition", """{"action":"$action"}"""))
    fun message(base: String, token: String, id: String, text: String): CareRequest {
        val body = buildJsonObject { put("body", text) }
        return json.decodeFromString(call(base, token, "api/requests/$id/messages", body.toString()))
    }
    fun upload(base: String, token: String, requestId: String, documentId: String, name: String, mime: String, bytes: ByteArray) {
        val file = bytes.toRequestBody(mime.toMediaType())
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("document_id", documentId)
            .addFormDataPart("file", name, file).build()
        val request = Request.Builder().url("$base/api/requests/$requestId/documents").header("Authorization", "Bearer $token").post(body).build()
        execute(request)
    }
    private fun call(base: String, token: String?, path: String, body: String?): String {
        val builder = Request.Builder().url("$base/$path")
        if (token != null) builder.header("Authorization", "Bearer $token")
        if (body == null) builder.get() else builder.post(body.toRequestBody(media))
        return execute(builder.build())
    }
    private fun execute(request: Request): String {
        val response = http.newCall(request).execute()
        val text = response.body?.string().orEmpty()
        if (response.isSuccessful) return text
        val key = when (response.code) {
            401 -> "portal_sign_in"
            403 -> "portal_forbidden"
            else -> "portal_unavailable"
        }
        throw PortalException(key)
    }
    private fun JsonObject.string(name: String) = this[name]?.jsonPrimitive?.content.orEmpty()
}

data class PortalSession(val token: String, val role: String, val displayName: String)
