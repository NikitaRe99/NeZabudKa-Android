plugins { id("com.android.application") }
android {
    namespace = "ru.nezabudka.speechaudit"
    compileSdk = 36
    defaultConfig {
        applicationId = "ru.nezabudka.speechaudit"
        minSdk = 29
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"
        ndk { abiFilters += "arm64-v8a" }
    }
    signingConfigs {
        create("localRelease") {
            storeFile = file(requireNotNull(providers.environmentVariable("NEZABUDKA_KEYSTORE_PATH").orNull))
            storePassword = providers.environmentVariable("NEZABUDKA_STORE_PASSWORD").orNull
            keyAlias = providers.environmentVariable("NEZABUDKA_KEY_ALIAS").orNull
            keyPassword = providers.environmentVariable("NEZABUDKA_KEY_PASSWORD").orNull
        }
    }
    buildTypes.getByName("release") { signingConfig = signingConfigs.getByName("localRelease") }
}
dependencies { implementation("com.microsoft.onnxruntime:onnxruntime-android:1.28.0") }
