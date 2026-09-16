pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    // Versions live here rather than in the root build script so that a module
    // can be configured without resolving plugins it does not apply. :core is
    // pure Kotlin/JVM and must stay buildable where the Android SDK and
    // dl.google.com are unavailable; keeping AGP out of the root plugins block
    // is what makes `gradle :core:test` work there.
    plugins {
        id("com.android.application") version "9.4.0"
        id("org.jetbrains.kotlin.jvm") version "2.4.20"
        id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
        id("com.android.compose.screenshot") version "0.0.1-alpha16"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "TelegramYou"
include(":app")
include(":core")
