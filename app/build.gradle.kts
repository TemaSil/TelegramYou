// Imported rather than written out in full: inside a task class in a build
// script, `java.` resolves to Gradle's `java` extension, not the package.
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Properties
import javax.inject.Inject

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
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
 *
 * APP_BUILD_NUMBER comes first, and only the Release workflow sets it: it
 * gives the release the number of the Build run that tested the same commit,
 * so "1.1 · build 430" names one commit whichever APK it is on — rather than
 * the Release workflow's own run count, which starts again at 1.
 */
val buildNumber: Int =
    System.getenv("APP_BUILD_NUMBER")?.toIntOrNull()
        ?: System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
        ?: runCatching {
            providers.exec {
                commandLine("git", "rev-list", "--count", "HEAD")
            }.standardOutput.asText.get().trim().toInt()
        }.getOrDefault(1)

/**
 * The version, as people see it: "1.1", "1.1.5", "1.2". Moved by hand, when
 * the owner says the app has moved — nothing bumps it on its own.
 *
 * Used for versionName and the APK name, so the two cannot drift, and read
 * out of this file by the Build and UI workflows for the release title and
 * the gallery, so keep it a plain string on one line.
 *
 * Until 1.1 the build number was its last part, "1.0.423". The number has
 * not gone: it is still the versionCode, which is what Android compares,
 * and the release is titled "TelegramYou 1.1 · build 430", which is what
 * the in-app update check compares (see isUpdate in :core). So every push
 * to main is still an update to a phone on the one before, while the name
 * stays 1.1 until it is changed here.
 *
 * 0.2 until 25 September 2026, then 1.0.<build>, then 1.1 from 27 September,
 * on the owner's word, with the first release build; 1.2 from 28 September,
 * with profiles, blocking, contacts and deleting chats; 1.3 the same day,
 * with the Appearance screen.
 */
val appVersionName = "2.1"

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

/**
 * The Telegram credentials this build uses, or none for the demo client.
 *
 * Read from local.properties on a developer's machine, and from the
 * environment on CI, where the Build workflow passes in the repository's
 * TELEGRAM_API_ID and TELEGRAM_API_HASH secrets. Never from a tracked file:
 * the repository is public, and a hash committed to it is found by anyone
 * searching GitHub, where one inside the APK takes a decompiler.
 *
 * `-PdemoClient=true` builds the demo whatever is configured. The UI
 * workflow uses it: its smoke test drives the demo's seeded chats and its
 * login code, which a live build does not have.
 */
val forceDemo = providers.gradleProperty("demoClient").orNull == "true"
val telegramApiId: String =
    if (forceDemo) "0"
    else localProperties.getProperty("TELEGRAM_API_ID")?.takeIf { it.isNotBlank() }
        ?: System.getenv("TELEGRAM_API_ID")?.takeIf { it.isNotBlank() }
        ?: "0"
val telegramApiHash: String =
    if (telegramApiId == "0") ""
    else localProperties.getProperty("TELEGRAM_API_HASH")?.takeIf { it.isNotBlank() }
        ?: System.getenv("TELEGRAM_API_HASH").orEmpty()
val isLiveBuild = telegramApiId != "0" && telegramApiHash.isNotBlank()

/**
 * The release key, from local.properties (`release.storeFile`,
 * `release.storePassword`, `release.keyAlias`, `release.keyPassword`) or the
 * environment (RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS,
 * RELEASE_KEY_PASSWORD), or null when neither has it. The key file itself
 * lives outside the repository; `*.jks` and `*.keystore` are ignored, and a
 * release key must never be committed — CLAUDE.md, "Credentials".
 */
class ReleaseSigning(val storeFile: String, val storePassword: String, val keyAlias: String, val keyPassword: String)

val releaseSigning: ReleaseSigning? = run {
    fun read(property: String, variable: String) =
        localProperties.getProperty(property)?.takeIf { it.isNotBlank() }
            ?: System.getenv(variable)?.takeIf { it.isNotBlank() }
    val file = read("release.storeFile", "RELEASE_STORE_FILE") ?: return@run null
    ReleaseSigning(
        storeFile = file,
        storePassword = read("release.storePassword", "RELEASE_STORE_PASSWORD") ?: return@run null,
        keyAlias = read("release.keyAlias", "RELEASE_KEY_ALIAS") ?: return@run null,
        keyPassword = read("release.keyPassword", "RELEASE_KEY_PASSWORD") ?: return@run null
    )
}

/**
 * `-PtelegramTestDc=true` points a live build at Telegram's test servers.
 *
 * There, numbers of the form +99966XYYYY exist for testing, and their login
 * code is X five times — no SMS, nobody's phone. That is what lets the Live
 * workflow sign in and send a photo, which the production servers could only
 * do with a real number and a real code. Never set for an APK anyone installs:
 * the test servers have their own accounts, and nobody's chats are there.
 */
val useTestDc = providers.gradleProperty("telegramTestDc").orNull == "true"

/**
 * The hash as it is stored in the APK: XORed with a mask made fresh for each
 * build, both as hex. The app puts it back together at start-up.
 *
 * This is masking, not encryption — the app has to be able to read it, so
 * anything that runs the app can too. What it stops is the one-line
 * extraction: `strings` over the APK, or a search of the dex for a 32-digit
 * hex word, no longer finds it.
 */
val maskedApiHash: Pair<String, String> = run {
    val plain = telegramApiHash.toByteArray()
    val mask = ByteArray(plain.size).also { SecureRandom().nextBytes(it) }
    val masked = ByteArray(plain.size) { i -> (plain[i].toInt() xor mask[i].toInt()).toByte() }
    fun ByteArray.hex() = joinToString("") { byte -> "%02x".format(byte) }
    masked.hex() to mask.hex()
}

android {
    namespace = "com.telegramyou.app"
    // 37, not 36: Compose 1.12 is built against it and the AAR metadata check
    // refuses anything lower. targetSdk stays at 35 deliberately — raising it
    // opts the app into Android 16 behaviour changes, which is a separate
    // decision from which components are available to compile against.
    compileSdk = 37

    // One debug key for everybody, tracked in the repository.
    //
    // Without this AGP makes a debug keystore on whatever machine is building,
    // and a GitHub runner is a fresh machine every time — so every APK CI
    // published was signed by a different key, and Android refuses to install
    // one over another. It reads as a package conflict, and the only way
    // through it was to uninstall first, losing whatever was in the app.
    //
    // The password is `android` and the alias `androiddebugkey`, which are the
    // values the Android SDK's own debug keystore has used forever. This one
    // is a secret in no sense that matters: it signs debug builds of a demo
    // client and nothing else. A release key would be a different question and
    // is still git-ignored, along with every other *.jks and *.keystore.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // The owner's release key, which is never in this repository: on CI
        // the Build workflow writes it from the RELEASE_KEYSTORE_BASE64 secret
        // to a file outside the checkout and names it here through the
        // environment; on a developer's machine, local.properties does. With
        // neither, there is no release signing config and no release build
        // to install — see releaseSigning.
        releaseSigning?.let { key ->
            create("release") {
                storeFile = file(key.storeFile)
                storePassword = key.storePassword
                keyAlias = key.keyAlias
                keyPassword = key.keyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "com.telegramyou.app"
        minSdk = 26
        targetSdk = 35
        // Monotonic, so Android treats a newer APK as an upgrade rather than
        // as the same build it already has.
        versionCode = buildNumber
        versionName = appVersionName

        buildConfigField("int", "TELEGRAM_API_ID", if (isLiveBuild) telegramApiId else "0")
        // Masked; see maskedApiHash. ApiCredentials in the app reverses it.
        buildConfigField("String", "TELEGRAM_API_HASH_MASKED", "\"${maskedApiHash.first}\"")
        buildConfigField("String", "TELEGRAM_API_HASH_MASK", "\"${maskedApiHash.second}\"")
        buildConfigField("boolean", "USE_DEMO_CLIENT", (!isLiveBuild).toString())
        // Whether this build may contain the demo at all — the backend, its
        // seeded chats and media, and the ten taps that switch to it. True
        // here and false for release, which is what lets R8 drop every line
        // and resource of it from the APK people install.
        buildConfigField("boolean", "DEMO_ALLOWED", "true")
        // Where the in-app update check looks, and for which file: each build
        // type updates to its own kind. Debug follows the rolling `latest`
        // prerelease every push to main refreshes; release follows the
        // newest real release, which only the Release workflow makes.
        buildConfigField("String", "UPDATE_ASSET", "\"TelegramYou-debug.apk\"")
        buildConfigField(
            "String", "UPDATE_API",
            "\"https://api.github.com/repos/TemaSil/TelegramYou/releases/tags/latest\""
        )
        buildConfigField("boolean", "USE_TEST_DC", useTestDc.toString())

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
        // The build people install: live only, shrunk and optimised by R8,
        // signed with the owner's key. Debug is the one for testing — the
        // demo, the debug key, the Compose debug overhead — and the two sit
        // side by side on a phone under different ids.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
            buildConfigField("boolean", "USE_DEMO_CLIENT", "false")
            buildConfigField("boolean", "DEMO_ALLOWED", "false")
            buildConfigField("String", "UPDATE_ASSET", "\"TelegramYou.apk\"")
            buildConfigField(
                "String", "UPDATE_API",
                "\"https://api.github.com/repos/TemaSil/TelegramYou/releases/latest\""
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
    // stable there is. It is public only from the 1.5.0 pre-releases — the
    // first beta since 8 October 2026 (2.1), after alpha29 — and what Compose
    // core the beta declares, and so what actually runs, is in the Build
    // workflow's "needs" and Resolved annotations.
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
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.4.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:core-ktx:1.7.0")
    // TestStorage itself, for files other than screenshots — the accessibility
    // tree dump. Declared rather than leaned on: core-ktx pulls it in, but a
    // class this code names directly belongs in the build file.
    androidTestImplementation("androidx.test.services:storage:1.6.0")
    // The service behind useTestStorageService above. androidTestUtil, not
    // androidTestImplementation: it is an APK installed alongside the tests
    // rather than a library they link against.
    androidTestUtil("androidx.test.services:test-services:1.6.0")

    // Pinned past the BOM on purpose. The BOM pins stable material3 1.4.0;
    // this is the only way to reach Expressive at all. It is a beta now — an
    // alpha until 2.1 — and the theme every screen is built on, so it is
    // worth knowing that is a deliberate trade and not an oversight.
    implementation("androidx.compose.material3:material3:1.5.0-beta01")
    // The navigation suite, for a rail where there is width for one. Its
    // version moves with material3 rather than with the BOM, so it is pinned
    // to the same pre-release — a mismatch here is two copies of the same
    // internal API and a link error, not a warning.
    implementation("androidx.compose.material3:material3-adaptive-navigation-suite:1.5.0-beta01")
    // The player behind video messages. Media3 is androidx's own — there is
    // no Material component for playback, and the alternative was MediaPlayer
    // with a SurfaceView and every format quirk handled by hand.
    //
    // Only the engine. The controls are Material's, drawn over the surface,
    // because media3-ui's own player view is a View with its own look that
    // belongs to no design system this app uses.
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    // The music player's session: the shade, the lock screen, headset
    // buttons and Bluetooth reach it through this, and Media3 draws the
    // platform's own media notification. Same release line as the engine.
    implementation("androidx.media3:media3-session:1.11.1")
    // The camera behind round video messages (1.7): CameraX's own, the
    // front camera bound to the chat's lifecycle and recorded to a square.
    // camera-compose draws the viewfinder as a composable, so the circle is
    // a clip on a stock component rather than a TextureView by hand. Its
    // own release line; the Build workflow prints what it offers.
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-video:1.6.2")
    implementation("androidx.camera:camera-compose:1.6.2")
    // Animated stickers. A Telegram `.tgs` is a gzipped Lottie file, and Lottie
    // is what draws Lottie: Airbnb's library, from Maven Central, with a
    // Compose entry point. There is no Material or androidx equivalent.
    implementation("com.airbnb.android:lottie-compose:6.7.1")
    // The QR code for signing in from another device. ZXing only decides
    // which modules are dark — the drawing is Compose's, see QrCode — and it
    // is plain Java from Maven Central.
    implementation("com.google.zxing:core:3.5.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    // No icon library: the icons are Material Symbols, generated into
    // ui/icons/Symbols.kt by .github/scripts/symbols.py. The frozen
    // material-icons-extended they replaced is gone with them.

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
    implementation("androidx.graphics:graphics-shapes:1.1.0")
    // Android's own emoji picker — categories, recents, skin tones — for
    // the emoji tab of the composer's panel, rather than a grid of our own.
    implementation("androidx.emoji2:emoji2-emojipicker:1.7.0")
    // Home-screen widgets (2.1): Glance draws them in Compose, and its
    // Material 3 theme gives them the wallpaper's colours as the app has.
    // Its own release line; the Build workflow prints what it offers.
    implementation("androidx.glance:glance-appwidget:1.2.0")
    implementation("androidx.glance:glance-material3:1.2.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")

    // Images in bubbles. Coil rather than hand-rolled decoding: a photo in a
    // scrolling list needs a cache, request cancellation when the row leaves
    // the window, and downsampling to the size actually drawn — all of which
    // is a library's job. 3.x is the Compose-first line and loads a File or a
    // content:// Uri without help.
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")

    // Pure-logic tests that run on the JVM: no device, no emulator, seconds.
    testImplementation("junit:junit:4.13.2")
    // viewModelScope runs on Dispatchers.Main, which does not exist on the
    // JVM until a test provides one.
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}

// ── TDLib's native libraries ────────────────────────────────────────────────
//
// `./gradlew :app:fetchTdlib` (gradlew.bat on Windows) downloads the libraries
// the live client needs and puts them in src/main/jniLibs/, which is
// git-ignored — they are 35 MB zipped and far too big for the repository. It
// replaces the by-hand "download the zip from Releases and unzip it" step.
//
// Pinned to one release of the Build TDLib workflow and to the checksum of its
// zip. These files go into the APK and run inside the app with its
// permissions, so a download that is not byte for byte the one that was built
// is refused rather than packaged. Bumping TDLib means running that workflow
// and changing both lines together; `sha256sum tdlib-jnilibs-java.zip` gives
// the second.
val tdlibRelease = "tdlib-java-d1085f9"
val tdlibSha256 = "05b7f02abbd95d8b8c4375f934fdf81efc949ea46f6adbcf837146a1c1e50d18"

/**
 * Downloads, verifies and unpacks the TDLib release.
 *
 * A task type of its own rather than a doLast block, so it touches the
 * project only through injected services and stays usable with Gradle's
 * configuration cache. Skipped when the libraries from this same release are
 * already in place: the marker file names the release they came from.
 */
abstract class FetchTdlib : DefaultTask() {
    @get:Input abstract val release: Property<String>
    @get:Input abstract val sha256: Property<String>
    @get:OutputDirectory abstract val jniLibs: DirectoryProperty
    @get:Internal abstract val download: RegularFileProperty
    @get:Inject abstract val files: FileSystemOperations
    @get:Inject abstract val archives: ArchiveOperations

    @TaskAction
    fun fetch() {
        val zip = download.get().asFile
        zip.parentFile.mkdirs()
        val url = "https://github.com/TemaSil/TelegramYou/releases/download/" +
            "${release.get()}/tdlib-jnilibs-java.zip"
        logger.lifecycle("Downloading $url")
        URI(url).toURL().openStream().use { input ->
            zip.outputStream().use { output -> input.copyTo(output) }
        }

        val digest = MessageDigest.getInstance("SHA-256")
        zip.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        if (actual != sha256.get()) {
            zip.delete()
            throw GradleException(
                "tdlib-jnilibs-java.zip from ${release.get()} has sha256 $actual, " +
                    "expected ${sha256.get()}. Not unpacking it."
            )
        }

        // The zip holds jniLibs/<abi>/libtdjsonjava.so; the leading folder is
        // dropped so each ABI lands directly under src/main/jniLibs.
        files.sync {
            from(archives.zipTree(zip)) {
                include("jniLibs/**/*.so")
                eachFile { relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray()) }
                includeEmptyDirs = false
            }
            into(jniLibs)
        }
        val abis = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        val missing = abis.filterNot { jniLibs.file("$it/libtdjsonjava.so").get().asFile.exists() }
        if (missing.isNotEmpty()) {
            throw GradleException("The release has no libtdjsonjava.so for: $missing")
        }
        // The marker the task's onlyIf reads: which release these came from.
        jniLibs.file("TDLIB_RELEASE").get().asFile.writeText(release.get() + "\n")
        zip.delete()
        logger.lifecycle("TDLib ${release.get()} unpacked for ${abis.joinToString()}")
    }
}

val fetchTdlib = tasks.register<FetchTdlib>("fetchTdlib") {
    group = "telegram"
    description = "Downloads TDLib's native libraries into src/main/jniLibs"
    release.set(tdlibRelease)
    sha256.set(tdlibSha256)
    jniLibs.set(layout.projectDirectory.dir("src/main/jniLibs"))
    download.set(layout.buildDirectory.file("tdlib/tdlib-jnilibs-java.zip"))
    // Copied into locals so the check captures two values, not the script:
    // the configuration cache has to be able to store it.
    val marker = layout.projectDirectory.file("src/main/jniLibs/TDLIB_RELEASE").asFile
    val wanted = tdlibRelease
    onlyIf("the libraries from $wanted are not already in place") {
        !marker.exists() || marker.readText().trim() != wanted
    }
}

// A live build without the libraries installs and then dies on the first
// native call with UnsatisfiedLinkError, so a live build fetches them itself.
// The demo build — CI's, and anyone's without credentials — needs none and
// never downloads anything.
if (isLiveBuild) {
    tasks.named("preBuild") { dependsOn(fetchTdlib) }
}
