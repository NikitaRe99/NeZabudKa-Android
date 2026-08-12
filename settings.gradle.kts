pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "LocalReminderAssistant"
include(":app", ":baselineprofile")
project(":app").projectDir = file("android/app")
project(":baselineprofile").projectDir = file("android/baselineprofile")
