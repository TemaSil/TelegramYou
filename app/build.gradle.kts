import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

android {
    namespace = "com.telegramyou.app"
    // 36, not 35: Compose 1.9 is built against it and refuses to link
    // otherwise. targetSdk stays at 35 deliberately — raising it opts the app
    // into Android 16 behaviour changes, which is a separate decision from
    // which components are available to compile against.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.telegramyou.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

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

    kotlinOptions {
        jvmTarget = "17"
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

    // APK names: TelegramYou-<versionName>.apk (e.g. TelegramYou-0.1.0-debug.apk)
    applicationVariants.configureEach {
        val variantVersionName = versionName
        outputs.configureEach {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl)
                .outputFileName = "TelegramYou-$variantVersionName.apk"
        }
    }
}

dependencies {
    // The earliest BOM carrying material3 1.4.0, which is where Material 3
    // Expressive lives — ButtonGroup, FloatingToolbar, LoadingIndicator,
    // SplitButton, MaterialExpressiveTheme and the motion schemes.
    //
    // Earliest on purpose. Every later BOM pins the same material3 1.4.0 and
    // differs only in the Compose core underneath: 2025.09.01 brings ui
    // 1.9.2, while the newest, 2026.09.00, brings 1.12.1 and with it
    // compileSdk 37 and Android Gradle plugin 9 — a major-version move that
    // buys no Expressive at all. The Build workflow prints the whole
    // BOM-to-material3 table, so this can be rechecked rather than recalled.
    val composeBom = platform("androidx.compose:compose-bom:2025.09.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.material3:material3")
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
