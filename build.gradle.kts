// No org.jetbrains.kotlin.android: AGP 9 carries Kotlin support itself, and
// applying the standalone plugin alongside it is a hard error.
// See https://kotl.in/gradle/agp-built-in-kotlin
plugins {
    id("com.android.application") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
    // Renders @Preview functions to PNGs through layoutlib — no device, no
    // emulator. Versioned independently of AGP; 0.0.1-alpha16 was the newest
    // when this was added, and the Build workflow prints the current list.
    id("com.android.compose.screenshot") version "0.0.1-alpha16" apply false
}
