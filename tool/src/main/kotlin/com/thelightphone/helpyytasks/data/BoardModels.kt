package com.thelightphone.helpyytasks.data

import kotlinx.serialization.Serializable

/** A task as the list returns it. The server sends nothing else. */
@Serializable
data class PhoneTask(
    val id: String,
    val title: String,
    val column: String,
    val labels: List<String> = emptyList(),
)

/**
 * A task as GET /tasks/:id returns it. Defaults on every optional field so a
 * server that drops one does not crash the phone.
 */
@Serializable
data class PhoneTaskDetail(
    val id: String,
    val title: String,
    val column: String,
    val labels: List<String> = emptyList(),
    val body: String = "",
    val nextAction: String? = null,
    val waitingOn: String? = null,
    val effort: String? = null,
    val suggestedSubtasks: List<SuggestedSubtask> = emptyList(),
    val parked: Boolean = false,
    val updatedAt: String? = null,
)

/**
 * A suggested subtask as the board stores it. Only `title` is shown on the
 * phone; the rest are carried so the shape does not break when the board's
 * generator adds a field.
 */
@Serializable
data class SuggestedSubtask(
    val title: String = "",
    val nextAction: String? = null,
    val status: String? = null,
    val source: String? = null,
)

@Serializable
internal data class TaskListResponse(val tasks: List<PhoneTask> = emptyList())

@Serializable
internal data class CreateTaskRequest(
    val title: String,
    val column: String,
    val parked: Boolean,
)

@Serializable
internal data class PatchTaskRequest(
    val column: String? = null,
    val parked: Boolean? = null,
)

/** Failures the UI must tell apart. Anything else is Server. */
sealed class BoardError(message: String) : Exception(message) {
    /** Token missing, wrong or revoked. Send the user to Settings. */
    object Unauthorized : BoardError("unauthorized")
    /** Gone, archived, or never existed. Treat as already handled. */
    object NotFound : BoardError("not found")
    /** The server refused the request as invalid — a client bug, not a user one. */
    class Rejected(val reason: String) : BoardError("rejected: $reason")
    /** No usable connection. Show the cache, marked stale. */
    class Offline(cause: Throwable?) : BoardError("offline: ${cause?.message}")
    class Server(val status: Int) : BoardError("server error $status")
}

/** What the repository needs from the network. Lets tests use a fake. */
interface BoardSource {
    suspend fun list(): List<PhoneTask>
    suspend fun listParked(): List<PhoneTask>
    suspend fun listDone(): List<PhoneTask>
    suspend fun detail(id: String): PhoneTaskDetail
    suspend fun move(id: String, column: String): PhoneTaskDetail
    suspend fun setParked(id: String, parked: Boolean): PhoneTaskDetail
    suspend fun create(title: String, column: String, parked: Boolean): PhoneTaskDetail
    suspend fun complete(id: String): PhoneTaskDetail
}
