package ru.nezabudka.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalTime

class FirstUseSetupTest {
    private class Store : FirstUseSetupStore {
        val offered = mutableSetOf<FirstUseSetupKind>()
        override fun wasOffered(kind: FirstUseSetupKind) = kind in offered
        override fun markOffered(kind: FirstUseSetupKind) { offered += kind }
        override fun reset() { offered.clear() }
    }

    @Test fun `construction never opens training and events remain once only across launches`() {
        val store = Store()
        val controller = FirstUseSetupController(store)
        assertTrue(controller.pending.value.isEmpty())
        assertTrue(controller.offer(FirstUseSetupKind.TIME_OF_DAY, phrase = "утром"))
        assertFalse(controller.offer(FirstUseSetupKind.TIME_OF_DAY, phrase = "вечером"))
        controller.finish(controller.pending.value.single().id)
        val restarted = FirstUseSetupController(store)
        assertTrue(restarted.pending.value.isEmpty())
        assertFalse(restarted.offer(FirstUseSetupKind.TIME_OF_DAY))
        assertTrue(restarted.offer(FirstUseSetupKind.FOLDER_INTERFACE, folderName = "Работа"))
    }

    @Test fun `one dialog at a time and stale actions cannot close another dialog`() {
        val controller = FirstUseSetupController(Store())
        controller.offer(FirstUseSetupKind.FOLDER_INTERFACE)
        controller.offer(FirstUseSetupKind.REMINDER_DELIVERY, requiresExactTime = true)
        val (folder, reminder) = controller.pending.value
        assertNull(controller.finish(reminder.id))
        assertEquals(folder, controller.finish(folder.id))
        assertNull(controller.finish(folder.id))
        assertEquals(reminder, controller.pending.value.single())
    }

    @Test fun `reset permits all events again and old callbacks have no effect`() {
        val controller = FirstUseSetupController(Store())
        FirstUseSetupKind.entries.forEach { controller.offer(it) }
        val old = controller.pending.value.first().id
        controller.reset()
        assertTrue(controller.pending.value.isEmpty())
        FirstUseSetupKind.entries.forEach { assertTrue(controller.offer(it)) }
        assertNull(controller.finish(old))
        assertEquals(FirstUseSetupKind.entries.size, controller.pending.value.size)
    }

    @Test fun `time input rejects invalid clocks and normalizes short hours`() {
        assertEquals(LocalTime.of(7, 30), parseFirstUseTime(" 7:30 "))
        assertEquals("00:00", parseFirstUseTime("0:00").toString())
        listOf("24:00", "09:60", "", "9", "12:3", "-1:00").forEach { assertNull(it, parseFirstUseTime(it)) }
    }

    @Test fun `NLU prompt requires a failed command and inactive understanding`() {
        assertTrue(shouldOfferNluSetup(false, false, true))
        assertTrue(shouldOfferNluSetup(false, false, false))
        assertTrue(shouldOfferNluSetup(false, true, false))
        assertFalse(shouldOfferNluSetup(false, true, true))
        assertFalse(shouldOfferNluSetup(true, false, true))
        assertFalse(shouldOfferNluSetup(true, false, false))
    }

    @Test fun `time of day setup applies only when it supplies the clock time`() {
        val parser = RussianTemporalParser()
        assertEquals("утром", parser.namedTimePhraseUsed("Напомни завтра утром проверить почту"))
        assertNotNull(parser.namedTimePhraseUsed("Напомни после обеда забрать бумаги"))
        assertNotNull(parser.namedTimePhraseUsed("Напомни днём проверить почту"))
        assertNull(parser.namedTimePhraseUsed("Напомни завтра в восемь утра проверить почту"))
        assertNull(parser.namedTimePhraseUsed("Напомни утром в 10:25 проверить почту"))
        assertNull(parser.namedTimePhraseUsed("Напомни через пять минут утром проверить почту"))
    }
}
