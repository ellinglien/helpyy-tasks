package com.thelightphone.helpytasks

import com.thelightphone.helpytasks.data.BoardClient
import com.thelightphone.helpytasks.data.BoardError
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private class Recorder {
    val calls = mutableListOf<Triple<HttpMethod, String, String?>>()
}

private fun client(
    status: HttpStatusCode,
    body: String,
    rec: Recorder? = null,
) = BoardClient({ "https://example.invalid" }, { "tok" }, MockEngine { req ->
    rec?.calls?.add(Triple(req.method, req.url.encodedPath + (req.url.encodedQuery.let { if (it.isEmpty()) "" else "?$it" }), req.headers[HttpHeaders.Authorization]))
    respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
})

class BoardClientTest {

    private val listBody = """
        {"tasks":[
          {"id":"a","title":"right now","column":"asap","labels":["nickel"]},
          {"id":"b","title":"queued","column":"go","labels":[]}
        ]}
    """.trimIndent()

    @Test
    fun `list parses tasks in server order`() = runTest {
        val tasks = client(HttpStatusCode.OK, listBody).list()
        assertEquals(listOf("right now", "queued"), tasks.map { it.title })
        assertEquals(listOf("asap", "go"), tasks.map { it.column })
        assertEquals(listOf("nickel"), tasks[0].labels)
    }

    @Test
    fun `list sends the bearer token to the right path`() = runTest {
        val rec = Recorder()
        client(HttpStatusCode.OK, listBody, rec).list()
        assertEquals(HttpMethod.Get, rec.calls.single().first)
        assertEquals("/api/phone/tasks", rec.calls.single().second)
        assertEquals("Bearer tok", rec.calls.single().third)
    }

    @Test
    fun `listParked asks for the parking lot`() = runTest {
        val rec = Recorder()
        client(HttpStatusCode.OK, listBody, rec).listParked()
        assertEquals("/api/phone/tasks?parked=1", rec.calls.single().second)
    }

    @Test
    fun `detail parses the wide shape`() = runTest {
        val body = """
            {"id":"a","title":"report","column":"asap","labels":[],
             "body":"notes here","nextAction":"pull box office","waitingOn":null,
             "effort":null,"suggestedSubtasks":["one","two"],"parked":false,
             "updatedAt":"2026-09-09T12:00:00.000Z"}
        """.trimIndent()
        val d = client(HttpStatusCode.OK, body).detail("a")
        assertEquals("notes here", d.body)
        assertEquals("pull box office", d.nextAction)
        assertEquals(listOf("one", "two"), d.suggestedSubtasks)
        assertEquals(false, d.parked)
    }

    @Test
    fun `detail tolerates a server that omits optional fields`() = runTest {
        val body = """{"id":"a","title":"t","column":"go","labels":[],"parked":false}"""
        val d = client(HttpStatusCode.OK, body).detail("a")
        assertEquals("", d.body)
        assertEquals(null, d.nextAction)
        assertEquals(emptyList(), d.suggestedSubtasks)
    }

    @Test
    fun `move patches column`() = runTest {
        val rec = Recorder()
        val body = """{"id":"a","title":"t","column":"asap","labels":[],"parked":false}"""
        client(HttpStatusCode.OK, body, rec).move("a", column = "asap")
        assertEquals(HttpMethod.Patch, rec.calls.single().first)
        assertEquals("/api/phone/tasks/a", rec.calls.single().second)
    }

    @Test
    fun `setParked patches parked`() = runTest {
        val body = """{"id":"a","title":"t","column":"go","labels":["someday"],"parked":true}"""
        val d = client(HttpStatusCode.OK, body).setParked("a", true)
        assertEquals(true, d.parked)
    }

    @Test
    fun `create posts title and destination`() = runTest {
        val rec = Recorder()
        val body = """{"id":"c","title":"ring the venue","column":"go","labels":[],"parked":false}"""
        val d = client(HttpStatusCode.Created, body, rec).create("ring the venue", "go", false)
        assertEquals(HttpMethod.Post, rec.calls.single().first)
        assertEquals("/api/phone/tasks", rec.calls.single().second)
        assertEquals("go", d.column)
    }

    @Test
    fun `complete posts to the complete path`() = runTest {
        val rec = Recorder()
        val body = """{"id":"a","title":"t","column":"done","labels":[],"parked":false}"""
        client(HttpStatusCode.OK, body, rec).complete("a")
        assertEquals("/api/phone/tasks/a/complete", rec.calls.single().second)
    }

    @Test
    fun `401 surfaces as Unauthorized`() = runTest {
        assertFailsWith<BoardError.Unauthorized> {
            client(HttpStatusCode.Unauthorized, """{"error":"unauthorized"}""").list()
        }
    }

    @Test
    fun `404 surfaces as NotFound`() = runTest {
        assertFailsWith<BoardError.NotFound> {
            client(HttpStatusCode.NotFound, """{"error":"not found"}""").complete("nope")
        }
    }

    @Test
    fun `400 surfaces as Rejected with the server message`() = runTest {
        val e = assertFailsWith<BoardError.Rejected> {
            client(HttpStatusCode.BadRequest, """{"error":"unknown column"}""").move("a", "nonsense")
        }
        assertEquals("unknown column", e.reason)
    }

    @Test
    fun `500 surfaces as Server`() = runTest {
        val e = assertFailsWith<BoardError.Server> {
            client(HttpStatusCode.InternalServerError, """{"error":"list failed"}""").list()
        }
        assertTrue(e.status == 500)
    }
}
