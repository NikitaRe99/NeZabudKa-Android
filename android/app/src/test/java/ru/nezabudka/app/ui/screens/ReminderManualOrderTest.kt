package ru.nezabudka.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderManualOrderTest {
    @Test
    fun `moving before another item in the displayed group uses that item`() {
        assertEquals(
            1L,
            reminderMoveBeforeId(
                storedOrder = listOf(1L, 2L, 3L),
                displayedGroupOrder = listOf(3L, 1L),
                movedId = 3L,
            ),
        )
    }

    @Test
    fun `moving to group end uses following stored item from another group`() {
        assertEquals(
            5L,
            reminderMoveBeforeId(
                storedOrder = listOf(1L, 2L, 3L, 4L, 5L),
                displayedGroupOrder = listOf(4L, 2L),
                movedId = 2L,
            ),
        )
    }

    @Test
    fun `moving to end of stored order appends item`() {
        assertNull(
            reminderMoveBeforeId(
                storedOrder = listOf(1L, 2L, 3L),
                displayedGroupOrder = listOf(3L, 1L),
                movedId = 1L,
            ),
        )
    }
}
