// Projectivy Launcher's wallpaper plugin API, copied unchanged from
// github.com/spocky/projectivy-plugin-wallpaper-provider (api/, Apache-2.0,
// LICENSE beside this file) at 60ec7b4 (2026-04-25). Upstream says not to
// change it: Projectivy talks to the plugin through these AIDL and Parcelable
// types, so their shape is the wire format. Only this build file is ours,
// written for this repo's AGP 8.5 / Kotlin 1.9 instead of upstream's version
// catalog.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.parcelize")
}

android {
    namespace = "tv.projectivy.plugin.wallpaperprovider.api"
    compileSdk = 34

    defaultConfig {
        minSdk = 28
    }

    buildFeatures {
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.8.0")
}
