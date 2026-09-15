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
    compileSdk = 35

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
    // Back on 2025.02.00 until the right BOM is known. 2026.09.00 was tried
    // and does carry Material 3 Expressive — it resolves material3 1.4.0 —
    // but its Compose core is 1.12.1, which demands compileSdk 37 and AGP
    // 9.1. That is a far larger move than Expressive needs, since 1.4.0 is
    // the newest stable material3 whichever BOM pins it. The Build workflow
    // now prints which material3 each BOM carries; the earliest one carrying
    // 1.4.0 is the one to take.
    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
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
