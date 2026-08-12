package ru.nezabudka.app.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

@RunWith(AndroidJUnit4::class)
class CredentialBackupRulesTest {
    @Test
    fun credential_preferences_are_excluded_from_legacy_and_modern_backup_rules() {
        assertTrue(excludedPaths("backup_rules").contains(CREDENTIAL_FILE))
        assertTrue(excludedPaths("data_extraction_rules").count { it == CREDENTIAL_FILE } == 2)
    }

    private fun excludedPaths(resourceName: String): List<String> {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val resourceId = context.resources.getIdentifier(resourceName, "xml", context.packageName)
        check(resourceId != 0) { "XML resource $resourceName not found" }
        val parser = context.resources.getXml(resourceId)
        return buildList {
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "exclude") {
                    parser.getAttributeValue(null, "path")?.let(::add)
                }
                parser.next()
            }
        }
    }

    private companion object {
        const val CREDENTIAL_FILE = "cloud_stt_credentials.xml"
    }
}
