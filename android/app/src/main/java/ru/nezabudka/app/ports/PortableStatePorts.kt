package ru.nezabudka.app.ports

import kotlinx.coroutines.flow.Flow

data class UserModelsStorageState(
    val configured: Boolean = false,
    val location: String = "Каталог пользователя не выбран",
    val cache: ModelCacheStats = ModelCacheStats(),
)

data class ModelCacheStats(
    val nativeCopiesBytes: Long = 0L,
    val incompleteDownloadsBytes: Long = 0L,
    val temporaryAudioBytes: Long = 0L,
) {
    val totalBytes: Long
        get() = nativeCopiesBytes + incompleteDownloadsBytes + temporaryAudioBytes
}

interface PortableModelSelection {
    val selectedModelName: Flow<String?>
    fun currentSelectedModelName(): String?
    fun selectIfAvailable(fileName: String)
}

data class PortableCloudConfiguration(
    val yandexFolderId: String = "",
    val vkEndpoint: String = "",
)

interface PortableCloudConfigurationStore {
    val portableConfiguration: Flow<PortableCloudConfiguration>
    fun currentPortableConfiguration(): PortableCloudConfiguration
    fun restorePortableConfiguration(configuration: PortableCloudConfiguration)
}
