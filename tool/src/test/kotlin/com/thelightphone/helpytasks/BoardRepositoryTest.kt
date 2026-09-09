package com.thelightphone.helpytasks

import com.thelightphone.helpytasks.data.BoardError
import com.thelightphone.helpytasks.data.BoardRepository
import com.thelightphone.helpytasks.data.BoardSource
import com.thelightphone.helpytasks.data.PhoneTask
import com.thelightphone.helpytasks.data.PhoneTaskDetail
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun task(id: String, title: String, column: String = "go") =
    PhoneTask(id, title, column, emptyList())

private fun detail(id: String, column: String = "go", parked: Boolean = false) =
    PhoneTaskDetail(id = id, title = "t", column = column, parked = parked)

private class FakeSource(
    var active: List<PhoneTask> = emptyList(),
    var parked: List<PhoneTask> = emptyList(),
    var failWith: BoardError? = null,
) : BoardSource {
    var listCalls = 0
    val completed = mutableListOf<String>()
    val parkedCalls = mutableListOf<Pair<String, Boolean>>()

    private fun boom() { failWith?.let { throw it } }

    override suspend fun list(): List<PhoneTask> { listCalls++; boom(); return active }
    override suspend fun listParked(): List<PhoneTask> { boom(); return parked }
    override suspend fun detail(id: String): PhoneTaskDetail { boom(); return detail(id) }
    override suspend fun move(id: String, column: String): PhoneTaskDetail {
        boom(); active = active.filterNot { it.id == id }; return detail(id, column)
    }
    override suspend fun setParked(id: String, parked: Boolean): PhoneTaskDetail {
        boom(); parkedCalls += id to parked; active = active.filterNot { it.id == id }
        return detail(id, parked = parked)
    }
    override suspend fun create(title: String, column: String, parked: Boolean): PhoneTaskDetail {
        boom(); active = active + task("new", title, column); return detail("new", column, parked)
    }
    override suspend fun complete(id: String): PhoneTaskDetail {
        boom(); completed += id; active = active.filterNot { it.id == id }
        return detail(id, "done")
    }
}

class BoardRepositoryTest {

    @Test
    fun `refresh publishes tasks and is not stale`() = runTest {
        val repo = BoardRepository(FakeSource(listOf(task("a", "one"))))
        repo.refresh()
        assertEquals(listOf("one"), repo.state.value.tasks.map { it.title })
        assertTrue(!repo.state.value.stale)
    }

    @Test
    fun `going offline keeps the last tasks and marks them stale`() = runTest {
        val source = FakeSource(listOf(task("a", "one")))
        val repo = BoardRepository(source)
        repo.refresh()
        source.failWith = BoardError.Offline(null)
        repo.refresh()
        assertEquals(listOf("one"), repo.state.value.tasks.map { it.title })
        assertTrue(repo.state.value.stale)
    }

    @Test
    fun `unauthorized clears tasks`() = runTest {
        val source = FakeSource(listOf(task("a", "one")))
        val repo = BoardRepository(source)
        repo.refresh()
        source.failWith = BoardError.Unauthorized
        repo.refresh()
        assertEquals(emptyList(), repo.state.value.tasks)
        assertTrue(repo.state.value.error is BoardError.Unauthorized)
    }

    @Test
    fun `completing removes the task immediately`() = runTest {
        val source = FakeSource(listOf(task("a", "one"), task("b", "two")))
        val repo = BoardRepository(source)
        repo.refresh()
        val before = source.listCalls
        repo.complete("a")
        assertEquals(listOf("two"), repo.state.value.tasks.map { it.title })
        assertEquals(before, source.listCalls, "no refetch needed")
        assertEquals(listOf("a"), source.completed)
    }

    @Test
    fun `a failed complete puts the task back`() = runTest {
        val source = FakeSource(listOf(task("a", "one")))
        val repo = BoardRepository(source)
        repo.refresh()
        source.failWith = BoardError.Offline(null)
        repo.complete("a")
        assertEquals(listOf("one"), repo.state.value.tasks.map { it.title })
        assertTrue(repo.state.value.stale)
    }

    @Test
    fun `completing something the server lost still removes it`() = runTest {
        val source = FakeSource(listOf(task("a", "one")))
        val repo = BoardRepository(source)
        repo.refresh()
        source.failWith = BoardError.NotFound
        repo.complete("a")
        assertEquals(emptyList(), repo.state.value.tasks)
    }

    @Test
    fun `parking removes the task from the active list`() = runTest {
        val source = FakeSource(listOf(task("a", "one"), task("b", "two")))
        val repo = BoardRepository(source)
        repo.refresh()
        repo.setParked("a", true)
        assertEquals(listOf("two"), repo.state.value.tasks.map { it.title })
        assertEquals(listOf("a" to true), source.parkedCalls)
    }

    @Test
    fun `a failed park puts the task back`() = runTest {
        val source = FakeSource(listOf(task("a", "one")))
        val repo = BoardRepository(source)
        repo.refresh()
        source.failWith = BoardError.Offline(null)
        repo.setParked("a", true)
        assertEquals(listOf("one"), repo.state.value.tasks.map { it.title })
    }

    @Test
    fun `parking lot loads separately from active work`() = runTest {
        val source = FakeSource(
            active = listOf(task("a", "one")),
            parked = listOf(task("p", "parked thing")),
        )
        val repo = BoardRepository(source)
        repo.refresh()
        repo.refreshParked()
        assertEquals(listOf("one"), repo.state.value.tasks.map { it.title })
        assertEquals(listOf("parked thing"), repo.state.value.parked.map { it.title })
    }

    @Test
    fun `pulling from the parking lot refreshes both lists`() = runTest {
        val source = FakeSource(parked = listOf(task("p", "parked thing")))
        val repo = BoardRepository(source)
        repo.refreshParked()
        repo.setParked("p", false)
        assertEquals(listOf("p" to false), source.parkedCalls)
        assertTrue(repo.state.value.parked.none { it.id == "p" })
    }

    @Test
    fun `capture refreshes so the task lands where the board puts it`() = runTest {
        val source = FakeSource(listOf(task("a", "one")))
        val repo = BoardRepository(source)
        repo.refresh()
        repo.capture("ring the venue", "go", false)
        assertTrue(repo.state.value.tasks.map { it.title }.contains("ring the venue"))
    }
}
