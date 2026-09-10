package com.thelightphone.helpyytasks.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.ContentConvertException
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * The six calls /api/phone exposes.
 *
 * baseUrl and token are both read lazily on every call, so settings entered
 * after construction take effect without rebuilding the client — the graph is
 * built before the user has filled in Settings.
 */
class BoardClient(
    private val baseUrl: suspend () -> String,
    private val token: suspend () -> String,
    engine: HttpClientEngine,
) : BoardSource {

    private val http = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    override suspend fun list(): List<PhoneTask> =
        parseBody<TaskListResponse>(request { http.get("${baseUrl()}/api/phone/tasks") { auth() } }).tasks

    override suspend fun listParked(): List<PhoneTask> =
        parseBody<TaskListResponse>(
            request { http.get("${baseUrl()}/api/phone/tasks?parked=1") { auth() } },
        ).tasks

    override suspend fun listDone(): List<PhoneTask> =
        parseBody<TaskListResponse>(
            request { http.get("${baseUrl()}/api/phone/tasks?done=1") { auth() } },
        ).tasks

    override suspend fun detail(id: String): PhoneTaskDetail =
        parseBody(request { http.get("${baseUrl()}/api/phone/tasks/$id") { auth() } })

    override suspend fun move(id: String, column: String): PhoneTaskDetail =
        patchTask(id, PatchTaskRequest(column = column))

    override suspend fun setParked(id: String, parked: Boolean): PhoneTaskDetail =
        patchTask(id, PatchTaskRequest(parked = parked))

    override suspend fun create(title: String, column: String, parked: Boolean): PhoneTaskDetail =
        parseBody(
            request {
                http.post("${baseUrl()}/api/phone/tasks") {
                    auth()
                    contentType(ContentType.Application.Json)
                    setBody(CreateTaskRequest(title, column, parked))
                }
            },
        )

    override suspend fun complete(id: String): PhoneTaskDetail =
        parseBody(request { http.post("${baseUrl()}/api/phone/tasks/$id/complete") { auth() } })

    private suspend fun patchTask(id: String, patch: PatchTaskRequest): PhoneTaskDetail =
        parseBody(
            request {
                http.patch("${baseUrl()}/api/phone/tasks/$id") {
                    auth()
                    contentType(ContentType.Application.Json)
                    setBody(patch)
                }
            },
        )

    /**
     * Decodes the response body, mapping a shape the server sends that this
     * client's models do not expect (a wrong field type, an unforeseen
     * variant) onto BoardError.Server rather than letting the deserialiser's
     * exception escape uncaught and take the whole tool down. A malformed
     * response is a server-side problem the same way a 5xx is.
     */
    private suspend inline fun <reified T> parseBody(response: HttpResponse): T =
        try {
            response.body()
        } catch (e: ContentConvertException) {
            throw BoardError.Server(response.status.value)
        }

    private suspend fun io.ktor.client.request.HttpRequestBuilder.auth() {
        header(HttpHeaders.Authorization, "Bearer ${token()}")
    }

    /** Maps transport failures and error statuses onto BoardError. */
    private suspend fun request(block: suspend () -> HttpResponse): HttpResponse {
        val response = try {
            block()
        } catch (e: IOException) {
            throw BoardError.Offline(e)
        }
        return when (response.status.value) {
            in 200..299 -> response
            400 -> throw BoardError.Rejected(errorMessage(response))
            401 -> throw BoardError.Unauthorized
            404 -> throw BoardError.NotFound
            else -> throw BoardError.Server(response.status.value)
        }
    }

    /** The API always answers errors in JSON, never HTML — see docs/phone-api.md. */
    private suspend fun errorMessage(response: HttpResponse): String = try {
        response.body<Map<String, String>>()["error"] ?: "rejected"
    } catch (e: Exception) {
        "rejected"
    }

    fun close() = http.close()
}
