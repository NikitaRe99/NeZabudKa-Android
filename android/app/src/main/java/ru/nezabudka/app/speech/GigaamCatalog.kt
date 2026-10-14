package ru.nezabudka.app.speech

/** Pinned GitHub assets; hashes verified by ModelDownloadWorker before storage. */
object GigaamCatalog {
    const val BASE_URL = "https://github.com/NikitaRe99/NeZabudKa-Android/releases/download/models-v3"
    val downloads = listOf(
        WhisperDownload("gigaam-v3", "GigaAM v3 CTC INT8 (рекомендуется)", "Русская модель третьего поколения", "gigaam-v3-ctc-int8.onnx", "306 МиБ", "$BASE_URL/gigaam-v3-ctc-int8.onnx"),
        WhisperDownload("gigaam-v2", "GigaAM v2 CTC INT8", "Предыдущая версия для сравнения · INT8", "gigaam-v2-ctc-int8.onnx", "324 МиБ", "$BASE_URL/gigaam-v2-ctc-int8.onnx"),
        WhisperDownload("gigaam-v1", "GigaAM v1 CTC INT8", "Предыдущая версия для сравнения · INT8", "gigaam-v1-ctc-int8.onnx", "361 МиБ", "$BASE_URL/gigaam-v1-ctc-int8.onnx"),
    )
    val checksums = mapOf(
        "gigaam-v1-ctc-int8.onnx" to "871b9633c5adf842e7e16f7a00e28194f09cecd8a58a57c88137e163ce388e0f",
        "gigaam-v2-ctc-int8.onnx" to "395ec148266acf7a4e77310b1250183a376e063934e3dbc2bd6e821f4d79660e",
        "gigaam-v3-ctc-int8.onnx" to "c2ce0e59e528dd6ee566ad0e7375711bbfed30f116879d27a5bf207b026e7683",
    )
}
