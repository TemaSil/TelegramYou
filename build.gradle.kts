// Deliberately empty of a plugins block.
//
// Two reasons, both learned the hard way:
//
//  * No org.jetbrains.kotlin.android anywhere: AGP 9 carries Kotlin support
//    itself, and applying the standalone plugin alongside it is a hard error.
//    See https://kotl.in/gradle/agp-built-in-kotlin
//  * Not even `id("com.android.application") apply false` here. Gradle resolves
//    a plugin marker the moment it is named, so that line drags AGP out of
//    Google's Maven for every build — including `:core:test`, which is pure
//    Kotlin/JVM and is the only part of the project buildable where the Android
//    SDK and dl.google.com are out of reach.
//
// Plugin versions live in settings.gradle.kts (pluginManagement); each module
// applies what it needs, without a version.
