plugins {
    id("com.android.test")
}

android {
    namespace = "ru.nezabudka.app.baselineprofile"
    compileSdk = 36
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    defaultConfig {
        minSdk = 29
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        create("benchmark") {
            matchingFallbacks += listOf("release")
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.test:runner:1.6.2")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.4.1")
    implementation("androidx.arch.core:core-runtime:2.2.0")
    implementation("androidx.startup:startup-runtime:1.2.0")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
}
