package ru.nezabudka.app.speech

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.nezabudka.app.data.SettingsStore
import ru.nezabudka.app.data.UserModelsStorage
import java.io.File

@RunWith(AndroidJUnit4::class)
class NotePunctuationTest {
    @Test fun real_model_preserves_words_and_compares_comma_boundaries() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val restorer = NotePunctuation(context)
        val report = JSONArray()
        val cases = listOf(
            "Если завтра будет дождь, я останусь дома. Когда погода улучшится, мы пойдём гулять.",
            "Я знаю, что ты придёшь завтра.",
            "Когда закончишь работу, позвони мне.",
            "Мы купили хлеб, молоко и яйца.",
            "Мама, я скоро вернусь.",
            "Я хотел выйти, но начался дождь.",
            "Он сказал, что вернётся, когда закончит работу.",
            "Завтра утром я схожу к врачу.",
            "К счастью, поезд пришёл вовремя.",
            "Чтобы успеть на автобус, нужно выйти раньше.",
            "Давай обсудим, что делать дальше.",
            "Я не знаю, придёт ли он сегодня.",
        )
        suspend fun compare(name: String, input: String, expected: String? = null) {
            assertEquals(input, restorer.restore(input, NOTE_PUNCTUATION_WHISPER))
            val started = SystemClock.elapsedRealtime()
            val output = restorer.restore(input, NOTE_PUNCTUATION_RUPUNCT)
            assertEquals("Comma restoration must preserve every word and other sign", input.replace(",", ""), output.replace(",", ""))
            report.put(JSONObject().put("case", name).put("input", input).put("output", output)
                .put("expected", expected).put("durationMs", SystemClock.elapsedRealtime() - started))
        }
        cases.forEachIndexed { index, expected -> compare("plain-${index + 1}", expected.replace(",", ""), expected) }
        cases.forEachIndexed { index, expected -> compare("punctuated-${index + 1}", expected, expected) }
        compare("long-note", List(30) { cases[0].replace(",", "") }.joinToString("\n"))
        compare("numbers-links", "Цена 3,5 рубля. Сайт https://example.ru/a,b и почта test@example.ru.")
        val recording = File(context.filesDir, "punctuation-reference.wav")
        if (recording.isFile) {
            val models = WhisperModelManager(context, UserModelsStorage(context)).apply { refresh() }
            var accurate = false
            val engine = WhisperCppSttEngine(models, WhisperVadModelProvider(context), File(context.cacheDir, "punctuation-test-segments"),
                configuredThreadCount = { 6 }, accurateMode = { accurate })
            try {
                engine.setCommandVocabularyEnabled(false)
                for (mode in listOf(false, true)) {
                    accurate = mode
                    val transcript = engine.transcribe(recording).text
                    compare(if (accurate) "user-recording-accurate" else "user-recording-fast", transcript, cases[0])
                }
            } finally { engine.release() }
        }
        File(context.filesDir, "punctuation-report.json").writeText(report.toString(2))
        assertTrue("The model should recover commas on at least one clear example", (0 until cases.size).any {
            report.getJSONObject(it).getString("output").contains(',')
        })
    }

    @Test fun selected_model_survives_settings_reload() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = SettingsStore(context)
        val previous = settings.state.value.notePunctuationModel
        try {
            settings.setNotePunctuationModel(NOTE_PUNCTUATION_RUPUNCT)
            assertEquals(NOTE_PUNCTUATION_RUPUNCT, SettingsStore(context).state.value.notePunctuationModel)
            settings.setNotePunctuationModel("unsupported")
            assertEquals(NOTE_PUNCTUATION_WHISPER, SettingsStore(context).state.value.notePunctuationModel)
        } finally { settings.setNotePunctuationModel(previous) }
    }
}
