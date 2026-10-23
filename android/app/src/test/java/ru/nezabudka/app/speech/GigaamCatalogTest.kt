package ru.nezabudka.app.speech

import org.junit.Assert.*
import org.junit.Test

class GigaamCatalogTest {
    @Test fun catalogContainsOnlyV3AndExperimentalLiteWithPinnedDigests() {
        assertEquals(listOf("gigaam-v3", "gigaam-v3-lite"), GigaamCatalog.downloads.map { it.id })
        for (model in GigaamCatalog.downloads) {
            assertTrue(GigaamCatalog.checksums.getValue(model.fileName).matches(Regex("[a-f0-9]{64}")))
            assertTrue(model.url.startsWith("https://github.com/NikitaRe99/NeZabudKa-Android/releases/download/"))
        }
        assertTrue(GigaamCatalog.downloads.last().title.contains("экспериментальная"))
    }
}
