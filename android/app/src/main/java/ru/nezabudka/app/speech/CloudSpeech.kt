@file:Suppress("DEPRECATION")

package ru.nezabudka.app.speech

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import ru.nezabudka.app.ports.SpeechRecognition
import ru.nezabudka.app.ports.SpeechToTextEngine
import ru.nezabudka.app.ports.PortableCloudConfiguration
import ru.nezabudka.app.ports.PortableCloudConfigurationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okio.BufferedSink
import java.io.DataInputStream
import java.io.File
import java.io.FilterOutputStream
import java.util.Base64
import java.util.concurrent.TimeUnit

data class CloudCredentialsState(
    val yandexConfigured: Boolean = false,
    val yandexFolderId: String = "",
    val googleConfigured: Boolean = false,
    val vkConfigured: Boolean = false,
    val vkEndpoint: String = "",
)

class CloudSttCredentials(context: Context) : PortableCloudConfigurationStore {
    private val applicationContext = context.applicationContext
    // AndroidX deprecated this API in favour of plain SharedPreferences. Tokens are
    // intentionally kept in the Android Keystore-backed store until a safe encrypted
    // replacement with a tested migration is introduced.
    private val prefs by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val masterKey = MasterKey.Builder(applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            applicationContext,
            "cloud_stt_credentials",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
    private val _state = MutableStateFlow(CloudCredentialsState())
    val state: StateFlow<CloudCredentialsState> = _state
    override val portableConfiguration: Flow<PortableCloudConfiguration> = state
        .map { value: CloudCredentialsState ->
            PortableCloudConfiguration(value.yandexFolderId, value.vkEndpoint)
        }
        .distinctUntilChanged()

    /** Reads Android Keystore-backed preferences off the UI startup path. */
    fun refresh() {
        _state.value = readState()
    }

    fun saveYandex(apiKey: String, folderId: String) {
        val edit = prefs.edit().putString(YANDEX_FOLDER, folderId.trim())
        if (apiKey.isNotBlank()) edit.putString(YANDEX_KEY, apiKey.trim())
        edit.apply(); _state.value = readState()
    }
    fun saveGoogle(accessToken: String) {
        if (accessToken.isNotBlank()) prefs.edit().putString(GOOGLE_TOKEN, accessToken.trim()).apply()
        _state.value = readState()
    }
    fun saveVk(serviceToken: String, endpoint: String) {
        val edit = prefs.edit().putString(VK_ENDPOINT, endpoint.trim())
        if (serviceToken.isNotBlank()) edit.putString(VK_TOKEN, serviceToken.trim())
        edit.apply(); _state.value = readState()
    }
    internal fun yandexKey() = prefs.getString(YANDEX_KEY, null)
    internal fun yandexFolder() = prefs.getString(YANDEX_FOLDER, "").orEmpty()
    internal fun googleToken() = prefs.getString(GOOGLE_TOKEN, null)
    internal fun vkToken() = prefs.getString(VK_TOKEN, null)
    internal fun vkEndpoint() = prefs.getString(VK_ENDPOINT, "").orEmpty()
    override fun currentPortableConfiguration() = PortableCloudConfiguration(
        yandexFolderId = state.value.yandexFolderId,
        vkEndpoint = state.value.vkEndpoint,
    )
    override fun restorePortableConfiguration(configuration: PortableCloudConfiguration) {
        saveYandex(apiKey = "", folderId = configuration.yandexFolderId)
        saveVk(serviceToken = "", endpoint = configuration.vkEndpoint)
    }
    private fun readState() = CloudCredentialsState(
        yandexConfigured = !prefs.getString(YANDEX_KEY, null).isNullOrBlank(),
        yandexFolderId = prefs.getString(YANDEX_FOLDER, "").orEmpty(),
        googleConfigured = !prefs.getString(GOOGLE_TOKEN, null).isNullOrBlank(),
        vkConfigured = !prefs.getString(VK_TOKEN, null).isNullOrBlank() && !prefs.getString(VK_ENDPOINT, null).isNullOrBlank(),
        vkEndpoint = prefs.getString(VK_ENDPOINT, "").orEmpty(),
    )
    private companion object {
        const val YANDEX_KEY = "yandex_api_key"
        const val YANDEX_FOLDER = "yandex_folder_id"
        const val GOOGLE_TOKEN = "google_oauth_token"
        const val VK_TOKEN = "vk_service_token"
        const val VK_ENDPOINT = "vk_speech_endpoint"
    }
}

/** VK Cloud Speech endpoint is configurable because Cloud Voice URLs are issued per service/region. */
class VkCloudSpeechEngine(private val credentials: CloudSttCredentials) : SpeechToTextEngine {
    override val id = "vk_cloud"
    override val displayName = "VK Cloud Speech (облако)"
    override suspend fun transcribe(processedWav: File): SpeechRecognition = withContext(Dispatchers.IO) {
        val token = credentials.vkToken() ?: error("Укажите сервисный токен VK Cloud в настройках")
        val endpoint = credentials.vkEndpoint().takeIf { it.startsWith("https://") }
            ?: error("Укажите HTTPS endpoint Speech API из кабинета VK Cloud")
        val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", processedWav.name, processedWav.asRequestBody("audio/wav".toMediaType()))
            .addFormDataPart("language", "ru-RU")
            .build()
        val request = Request.Builder().url(endpoint).header("Authorization", "Bearer $token").post(requestBody).build()
        cloudHttp.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("VK Cloud Speech: HTTP ${response.code}: ${body.take(300)}")
            val root = cloudJson.parseToJsonElement(body)
            fun findText(element: kotlinx.serialization.json.JsonElement): String? = when (element) {
                is kotlinx.serialization.json.JsonObject -> element["text"]?.jsonPrimitive?.contentOrNull
                    ?: element["transcript"]?.jsonPrimitive?.contentOrNull
                    ?: element.values.firstNotNullOfOrNull(::findText)
                is kotlinx.serialization.json.JsonArray -> element.firstNotNullOfOrNull(::findText)
                else -> null
            }
            val text = findText(root)?.trim().orEmpty()
            if (text.isBlank()) error("VK Cloud Speech вернул ответ без распознанного текста")
            SpeechRecognition(text)
        }
    }
}

private val cloudHttp = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).build()
private val cloudJson = Json { ignoreUnknownKeys = true }
private const val WAV_HEADER_BYTES = 44L
private fun File.requirePcmLength(): Long = DataInputStream(inputStream().buffered()).use { input ->
    val header = ByteArray(44)
    runCatching { input.readFully(header) }
        .getOrElse { throw IllegalArgumentException("WAV-файл повреждён", it) }
    (length() - WAV_HEADER_BYTES).also { require(it >= 0L) { "WAV-файл повреждён" } }
}

internal class PcmWavRequestBody(
    private val source: File,
    private val mediaType: okhttp3.MediaType,
) : RequestBody() {
    private val pcmLength = source.requirePcmLength()
    override fun contentType() = mediaType
    override fun contentLength() = pcmLength
    override fun writeTo(sink: BufferedSink) {
        DataInputStream(source.inputStream().buffered()).use { input ->
            input.readFully(ByteArray(WAV_HEADER_BYTES.toInt()))
            input.copyTo(sink.outputStream())
        }
    }
}

internal class GoogleSpeechJsonRequestBody(private val source: File) : RequestBody() {
    private val prefix = """{"config":{"encoding":"LINEAR16","sampleRateHertz":16000,"languageCode":"ru-RU","enableAutomaticPunctuation":true,"maxAlternatives":1},"audio":{"content":""""
        .encodeToByteArray()
    private val suffix = "\"}}".encodeToByteArray()
    private val pcmLength = source.requirePcmLength()
    override fun contentType() = "application/json; charset=utf-8".toMediaType()
    override fun contentLength(): Long = prefix.size + 4L * ((pcmLength + 2L) / 3L) + suffix.size
    override fun writeTo(sink: BufferedSink) {
        sink.write(prefix)
        val nonClosingOutput = object : FilterOutputStream(sink.outputStream()) {
            override fun close() = flush()
        }
        Base64.getEncoder().wrap(nonClosingOutput).use { encoded ->
            DataInputStream(source.inputStream().buffered()).use { input ->
                input.readFully(ByteArray(WAV_HEADER_BYTES.toInt()))
                input.copyTo(encoded)
            }
        }
        sink.write(suffix)
    }
}

class YandexSpeechKitEngine(private val credentials: CloudSttCredentials) : SpeechToTextEngine {
    override val id = "yandex"
    override val displayName = "Yandex SpeechKit (облако)"
    override suspend fun transcribe(processedWav: File): SpeechRecognition = withContext(Dispatchers.IO) {
        val key = credentials.yandexKey() ?: error("Укажите API-ключ Yandex SpeechKit в настройках")
        val pcmLength = processedWav.requirePcmLength()
        require(pcmLength <= 1_000_000L) { "Yandex SpeechKit v1 принимает не более 30 секунд или 1 МБ аудио" }
        val url = "https://stt.api.cloud.yandex.net/speech/v1/stt:recognize".toHttpUrl().newBuilder()
            .addQueryParameter("lang", "ru-RU").addQueryParameter("topic", "general")
            .addQueryParameter("format", "lpcm").addQueryParameter("sampleRateHertz", "16000")
            .apply { credentials.yandexFolder().takeIf { it.isNotBlank() }?.let { addQueryParameter("folderId", it) } }.build()
        val request = Request.Builder().url(url).header("Authorization", "Api-Key $key")
            .post(PcmWavRequestBody(processedWav, "application/octet-stream".toMediaType())).build()
        cloudHttp.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Yandex SpeechKit: HTTP ${response.code}: ${body.take(300)}")
            val root = cloudJson.parseToJsonElement(body).jsonObject
            val text = root["result"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (text.isBlank()) error("Yandex SpeechKit не распознал речь")
            SpeechRecognition(text)
        }
    }
}

class GoogleCloudSpeechEngine(private val credentials: CloudSttCredentials) : SpeechToTextEngine {
    override val id = "google_cloud"
    override val displayName = "Google Cloud Speech-to-Text"
    override suspend fun transcribe(processedWav: File): SpeechRecognition = withContext(Dispatchers.IO) {
        val token = credentials.googleToken() ?: error("Укажите OAuth access token Google Cloud в настройках")
        val request = Request.Builder().url("https://speech.googleapis.com/v1/speech:recognize")
            .header("Authorization", "Bearer $token")
            .post(GoogleSpeechJsonRequestBody(processedWav)).build()
        cloudHttp.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Google Cloud STT: HTTP ${response.code}: ${body.take(300)}")
            val results = cloudJson.parseToJsonElement(body).jsonObject["results"]?.jsonArray.orEmpty()
            val alternatives = results.mapNotNull { it.jsonObject["alternatives"]?.jsonArray?.firstOrNull()?.jsonObject }
            val text = alternatives.mapNotNull { it["transcript"]?.jsonPrimitive?.contentOrNull }.joinToString(" ").trim()
            if (text.isBlank()) error("Google Cloud STT не распознал речь")
            SpeechRecognition(text, alternatives.firstOrNull()?.get("confidence")?.jsonPrimitive?.doubleOrNull?.toFloat())
        }
    }
}
