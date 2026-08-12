package ru.nezabudka.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualOrderTest {
    @Test
    fun `adjacent drag in a long list updates only two rows`() {
        val stored = (1L..300L).map { id -> id to id * 10L }

        val updates = manualOrderUpdates(stored, movedId = 150L, beforeId = 149L)

        assertEquals(2, updates.size)
        assertEquals(listOf(150L, 149L), reorderedIds(stored, updates).slice(148..149))
    }

    @Test
    fun `dropping into the same position performs no writes`() {
        val stored = listOf(1L to 10L, 2L to 20L, 3L to 30L)

        assertTrue(manualOrderUpdates(stored, movedId = 2L, beforeId = 3L).isEmpty())
    }

    @Test
    fun `legacy duplicate values are normalized into the requested order`() {
        val stored = listOf(1L to 0L, 2L to 0L, 3L to 0L)
        val updates = manualOrderUpdates(stored, movedId = 3L, beforeId = 1L)

        assertEquals(listOf(3L, 1L, 2L), reorderedIds(stored, updates))
    }

    private fun reorderedIds(
        stored: List<Pair<Long, Long>>,
        updates: List<ManualOrderUpdate>,
    ): List<Long> {
        val changed = updates.associate { update -> update.id to update.order }
        return stored
            .map { (id, order) -> id to (changed[id] ?: order) }
            .sortedWith(compareBy<Pair<Long, Long>> { it.second }.thenBy { it.first })
            .map(Pair<Long, Long>::first)
    }
}
