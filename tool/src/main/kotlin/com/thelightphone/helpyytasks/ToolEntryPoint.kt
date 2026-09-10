package com.thelightphone.helpyytasks

import com.thelightphone.sdk.EntryPoint
import com.thelightphone.sdk.LightEntryPoint
import com.thelightphone.sdk.shared.LightServerData
import kotlinx.coroutines.flow.StateFlow

@EntryPoint
object ToolEntryPoint : LightEntryPoint {
    // No push in v1: the tool is opened deliberately, not pushed to.
    override suspend fun onToolCreate(serverData: StateFlow<LightServerData?>) = Unit
    override suspend fun onPushNotification(data: ByteArray) = Unit
}
