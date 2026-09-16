import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin/JVM: no Android, no Compose, no AGP. Everything here compiles and
// tests without the Android SDK, which is the point — this module is the only
// part of the project that can be built outside CI, so logic that needs no
// device belongs here rather than in :app.
plugins {
    kotlin("jvm")
}

// Java 17 to match :app's compileOptions, expressed as source/target rather
// than a toolchain: a toolchain would demand exactly JDK 17 be installed and
// fail on a machine that has a newer one, which defeats the purpose.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
