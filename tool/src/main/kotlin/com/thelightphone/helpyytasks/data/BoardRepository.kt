package com.thelightphone.helpyytasks.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * @param tasks  active work, last successfully fetched; survives a failed refresh
 * @param parked the parking lot, loaded only when that screen is opened
 * @param done   the most recently completed tasks, loaded only when that screen is opened
 * @param stale  what is on screen is older than the last attempt to refresh it
 * @param error  non-null when the last operation failed in a way the user must see
 */
data class BoardState(
    val tasks: List<PhoneTask> = emptyList(),
    val parked: List<PhoneTask> = emptyList(),
    val done: List<PhoneTask> = emptyList(),
    val stale: Boolean = false,
    val loading: Boolean = false,
    val error: BoardError? = null,
)

/**
 * Owns what the screens render. The rule throughout: a failure never empties the
 * list unless the failure means the list cannot be trusted (Unauthorized). A
 * phone that goes blank in a basement is useless.
 */
class BoardRepository(private val source: BoardSource) {

    private val _state = MutableStateFlow(BoardState())
    val state: StateFlow<BoardState> = _state.asStateFlow()

    suspend fun refresh() {
        _state.value = _state.value.copy(loading = true)
        try {
            _state.value = _state.value.copy(
                tasks = source.list(), stale = false, loading = false, error = null,
            )
        } catch (e: BoardError.Unauthorized) {
            // The token is no longer good; showing cached tasks would imply they
            // are current when we can no longer check.
            _state.value = BoardState(error = e)
        } catch (e: BoardError) {
            _state.value = _state.value.copy(stale = true, loading = false, error = e)
        }
    }

    suspend fun refreshParked() {
        try {
            _state.value = _state.value.copy(parked = source.listParked(), error = null)
        } catch (e: BoardError.Unauthorized) {
            _state.value = BoardState(error = e)
        } catch (e: BoardError) {
            _state.value = _state.value.copy(stale = true, error = e)
        }
    }

    suspend fun refreshDone() {
        try {
            _state.value = _state.value.copy(done = source.listDone(), error = null)
        } catch (e: BoardError.Unauthorized) {
            _state.value = BoardState(error = e)
        } catch (e: BoardError) {
            _state.value = _state.value.copy(stale = true, error = e)
        }
    }

    suspend fun detail(id: String): PhoneTaskDetail = source.detail(id)

    /** Removes the row at once so the tap feels instant, then confirms. */
    suspend fun complete(id: String) = optimistic(id) { source.complete(id) }

    /** Park from the active list, or pull back from the parking lot. */
    suspend fun setParked(id: String, parked: Boolean) {
        val beforeActive = _state.value.tasks
        val beforeParked = _state.value.parked
        _state.value = _state.value.copy(
            tasks = beforeActive.filterNot { it.id == id },
            parked = beforeParked.filterNot { it.id == id },
            error = null,
        )
        try {
            source.setParked(id, parked)
        } catch (e: BoardError.NotFound) {
            // Already gone server-side. Leave it removed.
        } catch (e: BoardError) {
            _state.value = _state.value.copy(
                tasks = beforeActive, parked = beforeParked, stale = true, error = e,
            )
        }
    }

    suspend fun move(id: String, column: String) = optimistic(id) { source.move(id, column) }

    /**
     * Move a task out of `done` back onto the board. Removes it from the done
     * list at once, restoring it on failure. NotFound counts as success: the
     * task is already gone from wherever it was.
     */
    suspend fun restore(id: String) {
        val before = _state.value.done
        _state.value = _state.value.copy(done = before.filterNot { it.id == id }, error = null)
        try {
            source.move(id, "go")
        } catch (e: BoardError.NotFound) {
            // Already gone. Leave it removed.
        } catch (e: BoardError) {
            _state.value = _state.value.copy(done = before, stale = true, error = e)
        }
    }

    suspend fun capture(title: String, column: String, parked: Boolean) {
        try {
            source.create(title, column, parked)
            refresh()
        } catch (e: BoardError) {
            _state.value = _state.value.copy(stale = true, error = e)
        }
    }

    /**
     * Remove the row, run the call, restore it on failure. NotFound counts as
     * success: the task is already gone.
     */
    private suspend fun optimistic(id: String, block: suspend () -> PhoneTaskDetail) {
        val before = _state.value.tasks
        _state.value = _state.value.copy(tasks = before.filterNot { it.id == id }, error = null)
        try {
            block()
        } catch (e: BoardError.NotFound) {
            // Already gone. Leave it removed.
        } catch (e: BoardError) {
            _state.value = _state.value.copy(tasks = before, stale = true, error = e)
        }
    }
}
