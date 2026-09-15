import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/** Used for both versionName and the APK name, so the two cannot drift. */
val appVersionName = "0.1.0"

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
        versionCode = 1
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
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
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Pure-logic tests that run on the JVM: no device, no emulator, seconds.
    testImplementation("junit:junit:4.13.2")
}
