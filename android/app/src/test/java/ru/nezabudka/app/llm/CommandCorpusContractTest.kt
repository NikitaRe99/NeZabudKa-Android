package ru.nezabudka.app.llm

import ru.nezabudka.app.domain.CommandIntent
import ru.nezabudka.app.domain.CommandScope
import ru.nezabudka.app.domain.RussianTemporalParser
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class CommandCorpusContractTest {
    private val cases = loadCorpus()
    private val extendedCases = loadCorpus("/llm/command_corpus_ru_extended.json")
    private val now = LocalDateTime.of(2026, 8, 8, 12, 0)

    @Test fun `corpus has planned coverage and unique ids`() {
        assertEquals(130, cases.size)
        assertEquals(cases.size, cases.map { it.id }.toSet().size)
        assertEquals(25, cases.count { it.intent == "create_reminder" })
        assertEquals(20, cases.count { it.intent == "list_reminders" })
        assertEquals(20, cases.count { it.intent == "delete_reminder" })
        assertEquals(20, cases.count { it.intent == "create_note" })
        assertEquals(15, cases.count { it.intent == "create_folder" || it.intent == "delete_folder" })
        assertEquals(30, cases.count { it.intent == "unknown" })
    }

    @Test fun `extended holdout corpus is separate and structurally valid`() {
        assertEquals(84, extendedCases.size)
        assertEquals(84, extendedCases.map { it.id }.toSet().size)
        assertTrue((cases.map { it.id }.toSet() intersect extendedCases.map { it.id }.toSet()).isEmpty())
        extendedCases.forEach { case ->
            assertTrue("${case.id}: empty utterance", case.utterance.isNotBlank())
            val intent = CommandIntent.fromWireName(case.intent)
            assertTrue("${case.id}: $intent is not allowed in ${case.scope}", intent in allowed(CommandScope.valueOf(case.scope.uppercase())))
            when (intent) {
                CommandIntent.CREATE_REMINDER, CommandIntent.CREATE_NOTE -> assertFalse(case.id, case.text.isNullOrBlank())
                CommandIntent.DELETE_REMINDER -> assertTrue(case.id, case.targetLast || !case.target.isNullOrBlank())
                CommandIntent.CREATE_FOLDER, CommandIntent.DELETE_FOLDER -> assertFalse(case.id, case.folder.isNullOrBlank())
                else -> Unit
            }
        }
    }

    @Test fun `corpus commands respect screen scopes and payload contract`() {
        cases.forEach { case ->
            assertTrue("${case.id}: empty utterance", case.utterance.isNotBlank())
            val intent = CommandIntent.fromWireName(case.intent)
            val scope = CommandScope.valueOf(case.scope.uppercase())
            assertTrue("${case.id}: $intent is not allowed in $scope", intent in allowed(scope))
            when (intent) {
                CommandIntent.CREATE_REMINDER, CommandIntent.CREATE_NOTE ->
                    assertFalse("${case.id}: expected text is missing", case.text.isNullOrBlank())
                CommandIntent.DELETE_REMINDER ->
                    assertTrue("${case.id}: deletion target is missing", case.targetLast || !case.target.isNullOrBlank())
                CommandIntent.CREATE_FOLDER, CommandIntent.DELETE_FOLDER ->
                    assertFalse("${case.id}: folder is missing", case.folder.isNullOrBlank())
                CommandIntent.LIST_REMINDERS, CommandIntent.UNKNOWN -> Unit
            }
        }
    }

    @Test fun `expected dates agree with deterministic temporal parser`() {
        val temporal = RussianTemporalParser()
        val mismatches = mutableListOf<String>()
        var checks = 0
        cases.filter { it.remindAt != null }.forEach { case ->
            checks++
            val expected = LocalDateTime.parse(case.remindAt)
            val actual = temporal.parse(case.utterance, now).remindAt
            if (actual != expected) mismatches += "${case.id}: reminder expected=$expected actual=$actual"
        }
        cases.filter { it.periodStart != null || it.periodEnd != null }.forEach { case ->
            assertNotNull("${case.id}: period start is missing", case.periodStart)
            assertNotNull("${case.id}: period end is missing", case.periodEnd)
            val range = temporal.extractDateRange(case.utterance, now)
            val date = if (range == null) temporal.extractDate(case.utterance, now) else null
            val actualStart = range?.start?.atStartOfDay() ?: date?.atStartOfDay()
            val actualEnd = range?.endExclusive?.atStartOfDay() ?: date?.plusDays(1)?.atStartOfDay()
            checks++
            val expectedStart = LocalDateTime.parse(case.periodStart)
            val expectedEnd = LocalDateTime.parse(case.periodEnd)
            if (actualStart != expectedStart || actualEnd != expectedEnd) {
                mismatches += "${case.id}: period expected=$expectedStart..$expectedEnd actual=$actualStart..$actualEnd"
            }
        }
        val correct = checks - mismatches.size
        println("Deterministic temporal baseline: $correct/$checks; mismatches=${mismatches.joinToString()}")
        assertTrue("Temporal baseline dropped below 90%: ${mismatches.joinToString()}", correct * 10 >= checks * 9)
    }

    private fun allowed(scope: CommandScope): Set<CommandIntent> = when (scope) {
        CommandScope.ASSISTANT -> setOf(
            CommandIntent.CREATE_REMINDER, CommandIntent.LIST_REMINDERS, CommandIntent.DELETE_REMINDER,
            CommandIntent.CREATE_NOTE, CommandIntent.CREATE_FOLDER, CommandIntent.DELETE_FOLDER,
            CommandIntent.UNKNOWN,
        )
        CommandScope.REMINDERS -> setOf(
            CommandIntent.CREATE_REMINDER, CommandIntent.LIST_REMINDERS,
            CommandIntent.DELETE_REMINDER, CommandIntent.UNKNOWN,
        )
        CommandScope.NOTES -> setOf(
            CommandIntent.CREATE_NOTE, CommandIntent.CREATE_FOLDER,
            CommandIntent.DELETE_FOLDER, CommandIntent.UNKNOWN,
        )
    }

    private fun loadCorpus(path: String = "/llm/command_corpus_ru.json"): List<CorpusCase> =
        Json.decodeFromString(
            requireNotNull(javaClass.getResourceAsStream(path)) {
                "Command corpus resource is missing"
            }.bufferedReader().use { it.readText() },
        )
}

@Serializable
internal data class CorpusCase(
    val id: String,
    val scope: String,
    val utterance: String,
    val intent: String,
    val text: String? = null,
    val target: String? = null,
    val targetLast: Boolean = false,
    val folder: String? = null,
    val remindAt: String? = null,
    val periodStart: String? = null,
    val periodEnd: String? = null,
)
