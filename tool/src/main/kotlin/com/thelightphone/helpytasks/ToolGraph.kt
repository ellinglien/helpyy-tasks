package com.thelightphone.helpytasks

import com.thelightphone.helpytasks.data.BoardClient
import com.thelightphone.helpytasks.data.BoardRepository
import com.thelightphone.helpytasks.data.DataStoreTokenStore
import com.thelightphone.helpytasks.data.TokenStore
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
