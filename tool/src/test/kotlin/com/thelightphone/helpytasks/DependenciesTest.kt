package com.thelightphone.helpytasks

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

@Serializable
private data class Probe(val ok: Boolean)

class DependenciesTest {

    @Test
    fun `serialization is available`() {
        assertEquals(Probe(true), Json.decodeFromString<Probe>("""{"ok":true}"""))
    }

    @Test
    fun `ktor mock engine is available`() = runTest {
        val client = HttpClient(MockEngine {
            respond(
                """{"ok":true}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        })
        assertEquals(200, client.get("http://example.invalid/").status.value)
        client.close()
    }
}
