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
    // Google's phone-number library, the one Android and Telegram's own
    // clients format numbers with: the as-you-type formatter, which country a
    // number belongs to, and each country's calling code. Pure Java, so it
    // belongs here with the rest of the logic, and its answers are tested on
    // the JVM like everything else in this module.
    implementation("com.googlecode.libphonenumber:libphonenumber:9.0.40")

    testImplementation("junit:junit:4.13.2")
}

// `internal` means "this module", and :app is a different one — so an internal
// declaration here compiles clean, passes every test in this module, and then
// breaks :app in CI. That is exactly the round trip this module exists to avoid,
// so it is caught locally instead.
val checkNoInternalApi by tasks.registering {
    val sources = kotlin.sourceSets.named("main").map { it.kotlin.srcDirs }
    inputs.files(sources)
    outputs.upToDateWhen { true }
    doLast {
        val offenders = sources.get()
            .flatMap { dir -> dir.walkTopDown().filter { it.extension == "kt" } }
            .flatMap { file ->
                file.readLines().withIndex()
                    .filter { (_, line) -> Regex("""^\s*internal\s""").containsMatchIn(line) }
                    .map { (index, line) -> "${file.name}:${index + 1}: ${line.trim()}" }
            }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "internal is module-scoped and :app cannot see it:\n" +
                    offenders.joinToString("\n") { "  $it" }
            )
        }
    }
}

tasks.named("check") { dependsOn(checkNoInternalApi) }
