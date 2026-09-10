package com.thelightphone.helpyytasks.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.thelightphone.sdk.SealedLightContext
import kotlinx.coroutines.flow.first

/**
 * Where the tool points and what it presents. `token` is a secret: toString is
 * overridden so it cannot reach a log by accident rather than relying on every
 * caller to remember.
 */
data class Settings(val baseUrl: String = "", val token: String = "") {

    val isConfigured: Boolean get() = baseUrl.isNotBlank() && token.isNotBlank()

    override fun toString(): String =
        "Settings(baseUrl=$baseUrl, token=${if (token.isBlank()) "unset" else "set"})"

    companion object {
        fun normalised(baseUrl: String, token: String) =
            Settings(baseUrl.trim().trimEnd('/'), token.trim())
    }
}

interface TokenStore {
    suspend fun read(): Settings
    suspend fun write(settings: Settings)
}

/** Test double. */
class InMemoryTokenStore(initial: Settings = Settings()) : TokenStore {
    private var current = initial
    override suspend fun read(): Settings = current
    override suspend fun write(settings: Settings) {
        current = Settings.normalised(settings.baseUrl, settings.token)
    }
}

private val KEY_BASE_URL = stringPreferencesKey("helpy_tasks_base_url")
private val KEY_TOKEN = stringPreferencesKey("helpy_tasks_token")

/**
 * App-private storage. Not encrypted beyond Android's file-based encryption —
 * androidx.security.crypto is not in Light's version catalog, and the token is
 * scoped to the board.
 *
 * Deviation from the plan: the plan's DataStoreTokenStore took a raw
 * `android.content.Context` and opened its own `preferencesDataStore`. The
 * fork's LightSdkPlugin unconditionally blocks `import android.content.Context`
 * in tool-module source (checked at Gradle configuration time, so it fails
 * before any test can even run) — see BLOCKED_IMPORTS in
 * plugin/.../LightSdkPlugin.kt. `SealedLightContext.androidContext` is also
 * `internal` to sdk:client, so the tool module cannot reach a raw Context at
 * all. The SDK's own convention for this (see LightPushManager) is to read
 * `SealedLightContext.dataStore`, a DataStore<Preferences> already backed by
 * a shared "DEFAULT_DATASTORE" file, and namespace your own keys inside it —
 * so that is what this does instead.
 */
class DataStoreTokenStore(private val context: SealedLightContext) : TokenStore {

    override suspend fun read(): Settings {
        val prefs = context.dataStore.data.first()
        return Settings(prefs[KEY_BASE_URL].orEmpty(), prefs[KEY_TOKEN].orEmpty())
    }

    override suspend fun write(settings: Settings) {
        val clean = Settings.normalised(settings.baseUrl, settings.token)
        context.dataStore.edit {
            it[KEY_BASE_URL] = clean.baseUrl
            it[KEY_TOKEN] = clean.token
        }
    }
}
