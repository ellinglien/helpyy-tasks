package com.thelightphone.helpyytasks

import com.thelightphone.helpyytasks.data.BoardClient
import com.thelightphone.helpyytasks.data.BoardRepository
import com.thelightphone.helpyytasks.data.DataStoreTokenStore
import com.thelightphone.helpyytasks.data.TokenStore
import com.thelightphone.sdk.SealedLightContext
import io.ktor.client.engine.okhttp.OkHttp

/**
 * One repository and one token store for the whole tool. A DI framework would
 * be more machinery than the tool.
 */
object ToolGraph {
    @Volatile private var repo: BoardRepository? = null
    @Volatile private var store: TokenStore? = null

    fun tokenStore(context: SealedLightContext): TokenStore =
        store ?: synchronized(this) {
            // Takes SealedLightContext, not a raw Context: android.content.Context
            // is a blocked import in tool modules and androidContext is internal.
            store ?: DataStoreTokenStore(context).also { store = it }
        }

    fun repository(context: SealedLightContext): BoardRepository =
        repo ?: synchronized(this) {
            repo ?: run {
                val tokens = tokenStore(context)
                BoardRepository(
                    BoardClient(
                        baseUrl = { tokens.read().baseUrl },
                        token = { tokens.read().token },
                        engine = OkHttp.create(),
                    ),
                ).also { repo = it }
            }
        }
}
