import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    // Kotlin itself is compiled by AGP 9's built-in Kotlin support; only the
    // Compose compiler plugin has to be applied here.
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.sachintha82288.devbrowser"
    // GeckoView 157 was built against Android 17 (API 37.1) and its AAR
    // metadata makes that a hard floor for anything that depends on it, so the
    // minor API level has to be spelled out. targetSdk stays at 36: compiling
    // against a newer platform does not opt the app into new runtime behaviour.
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "io.github.sachintha82288.devbrowser"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    // Dark neon launch experience + avoid flashing white on cold start.
    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Produce per-ABI APKs so each build stays small; the universal APK is
    // still built for convenience (adb installs the right one automatically).
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = true
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// kotlinOptions is gone in AGP 9; the built-in Kotlin compiler is configured
// through the top-level kotlin extension instead.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // The Firefox engine (GeckoView, release channel, OMNI build = full
    // WebExtension / extension-process support).
    implementation(libs.mozilla.geckoview)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
