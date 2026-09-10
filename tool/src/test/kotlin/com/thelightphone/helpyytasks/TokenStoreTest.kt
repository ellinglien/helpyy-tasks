package com.thelightphone.helpyytasks

import com.thelightphone.helpyytasks.data.InMemoryTokenStore
import com.thelightphone.helpyytasks.data.Settings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TokenStoreTest {

    @Test
    fun `starts unconfigured`() = runTest {
        assertFalse(InMemoryTokenStore().read().isConfigured)
    }

    @Test
    fun `round-trips base url and token`() = runTest {
        val store = InMemoryTokenStore()
        store.write(Settings("https://helpyy.app", "0123456789abcdef0123"))
        assertEquals("https://helpyy.app", store.read().baseUrl)
        assertTrue(store.read().isConfigured)
    }

    @Test
    fun `trims whitespace and a trailing slash`() = runTest {
        val store = InMemoryTokenStore()
        store.write(Settings("  https://helpyy.app/  ", "  abc  "))
        assertEquals("https://helpyy.app", store.read().baseUrl)
        assertEquals("abc", store.read().token)
    }

    @Test
    fun `a blank token is not configured`() = runTest {
        val store = InMemoryTokenStore()
        store.write(Settings("https://helpyy.app", "   "))
        assertFalse(store.read().isConfigured)
    }

    @Test
    fun `toString never reveals the token`() {
        val s = Settings("https://helpyy.app", "super-secret-value")
        assertFalse(s.toString().contains("super-secret-value"))
    }
}
