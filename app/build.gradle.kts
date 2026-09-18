import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.android.compose.screenshot")
}

/**
 * The build number, and with it the version.
 *
 * Every APK CI publishes used to be 0.1.0 with versionCode 1, so nothing on a
 * phone could tell two of them apart and the release page could not say which
 * build it was offering.
 *
 * GITHUB_RUN_NUMBER is the source where there is one: it is monotonic, it
 * needs no git history, and a shallow checkout — which is what actions/checkout
 * does by default — makes counting commits return 1 on every run. Off CI it
 * falls back to the commit count, and to 1 in a tree with no git at all, so a
 * local build still has a number that moves.
 */
val buildNumber: Int =
    System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
        ?: runCatching {
            providers.exec {
                commandLine("git", "rev-list", "--count", "HEAD")
            }.standardOutput.asText.get().trim().toInt()
        }.getOrDefault(1)

/** Used for both versionName and the APK name, so the two cannot drift. */
val appVersionName = "0.2.$buildNumber"

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

android {
    namespace = "com.telegramyou.app"
    // 37, not 36: Compose 1.12 is built against it and the AAR metadata check
    // refuses anything lower. targetSdk stays at 35 deliberately — raising it
    // opts the app into Android 16 behaviour changes, which is a separate
    // decision from which components are available to compile against.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.telegramyou.app"
        minSdk = 26
        targetSdk = 35
        // Monotonic, so Android treats a newer APK as an upgrade rather than
        // as the same build it already has.
        versionCode = buildNumber
        versionName = appVersionName

        buildConfigField(
            "int",
            "TELEGRAM_API_ID",
            (localProperties.getProperty("TELEGRAM_API_ID") ?: "0")
        )
        buildConfigField(
            "String",
            "TELEGRAM_API_HASH",
            "\"${localProperties.getProperty("TELEGRAM_API_HASH") ?: ""}\""
        )
        buildConfigField(
            "boolean",
            "USE_DEMO_CLIENT",
            ((localProperties.getProperty("TELEGRAM_API_ID") ?: "0") == "0").toString()
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Screenshots taken by an instrumentation test have nowhere safe to
        // live: the app's own external files go with it when Gradle uninstalls
        // the APK at the end of the run, which is why the first attempt came
        // back with none. TestStorage writes through a service that outlives
        // the app, and AGP copies the result into build/outputs.
        testInstrumentationRunnerArguments["useTestStorageService"] = "true"
        vectorDrawables.useSupportLibrary = true

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    // With AGP 9's built-in Kotlin there is no kotlinOptions block and no
    // separate Kotlin plugin: the Kotlin jvmTarget follows these.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // android.jar in a unit test is stubs, and every stub throws
    // "RuntimeException: Stub!" rather than doing nothing. That is fine until
    // production code logs on a path a test exercises — VoicePlayer catching a
    // failed MediaPlayer and calling Log.w turned a caught error into an
    // uncaught one, inside the catch block. Default values instead of throws.
    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Yes, this is also in gradle.properties, and both are required. The
    // plugin checks the property while being applied, which happens before
    // this block exists; then it checks the module's own experimental
    // properties while configuring the project. Setting either one alone
    // fails, each time with a message naming only the other place.
    experimentalProperties["android.experimental.enableScreenshotTest"] = true


    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }

}

// APK names: TelegramYou-<versionName>-<variant>.apk, e.g.
// TelegramYou-0.1.0-debug.apk.
//
// Set through archivesName rather than by rewriting outputFileName on each
// variant output. That older approach reached into
// com.android.build.gradle.internal.api.BaseVariantOutputImpl — an internal
// class behind a deprecated API, which AGP 9 removed outright. archivesName
// is Gradle's own and has no such expiry.
base {
    archivesName = "TelegramYou-$appVersionName"
}

dependencies {
    // Models and the pure logic around them: no Android types, so they live in
    // a plain JVM module that builds without the SDK.
    implementation(project(":core"))

    // The newest BOM, because Material 3 Expressive requires it.
    //
    // Expressive is not public in any stable material3 — MaterialExpressiveTheme,
    // MotionScheme and LoadingIndicator are all `internal` in 1.4.0, the newest
    // stable there is. It is public only from the 1.5.0 alphas, and material3
    // 1.5.0-alpha28 declares Compose core 1.12.0, which is what this BOM pins.
    // Hence compileSdk 37, AGP 9 and Gradle 9 as well: the whole stack moves
    // together or not at all.
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // The app run on a real Android runtime, driven from outside itself.
    //
    // UiAutomator rather than Compose's own test rule, deliberately. The
    // Compose rule synchronises on the composition being idle, and this app is
    // never idle — the typing indicator and the Expressive loading indicator
    // are infinite animations, which is exactly the case that hangs it.
    // UiAutomator reads the accessibility tree instead and does not care.
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    // The service behind useTestStorageService above. androidTestUtil, not
    // androidTestImplementation: it is an APK installed alongside the tests
    // rather than a library they link against.
    androidTestUtil("androidx.test.services:test-services:1.5.0")

    // Pinned past the BOM on purpose. The BOM pins stable material3 1.4.0;
    // this is the only way to reach Expressive at all. It is an alpha, and
    // the theme every screen is built on, so it is worth knowing that is a
    // deliberate trade and not an oversight.
    implementation("androidx.compose.material3:material3:1.5.0-alpha28")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    // Pinned, not taken from the BOM: androidx froze material-icons-extended
    // at 1.7.x and dropped it from later BOMs, so an unversioned coordinate
    // stops resolving. The icons themselves have not changed.
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    // The shape library the avatar cluster builds its outlines from.
    //
    // Declared rather than leaned on: material3 already pulls it in — its POM
    // names graphics-shapes-android 1.0.1 at compile scope — but this code
    // calls RoundedPolygon and CornerRounding itself, and a dependency used
    // directly belongs in the build file rather than arriving by luck.
    //
    // Worth knowing that this one is stable while material3 is an alpha. The
    // catalogue of thirty-five named shapes lives in material3 and every one
    // of them is `internal` there, so the shapes are built from this instead.
    implementation("androidx.graphics:graphics-shapes:1.0.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")

    // Images in bubbles. Coil rather than hand-rolled decoding: a photo in a
    // scrolling list needs a cache, request cancellation when the row leaves
    // the window, and downsampling to the size actually drawn — all of which
    // is a library's job. 3.x is the Compose-first line and loads a File or a
    // content:// Uri without help.
    implementation("io.coil-kt.coil3:coil-compose:3.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Pure-logic tests that run on the JVM: no device, no emulator, seconds.
    testImplementation("junit:junit:4.13.2")
    // viewModelScope runs on Dispatchers.Main, which does not exist on the
    // JVM until a test provides one.
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
