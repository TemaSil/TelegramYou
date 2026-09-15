// No org.jetbrains.kotlin.android: AGP 9 carries Kotlin support itself, and
// applying the standalone plugin alongside it is a hard error.
// See https://kotl.in/gradle/agp-built-in-kotlin
plugins {
    id("com.android.application") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}
