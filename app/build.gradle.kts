plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "me.viktorbarzin.portalframe"
    compileSdk = 34

    defaultConfig {
        applicationId = "me.viktorbarzin.portalframe"
        minSdk = 28      // Portal runs Android 9/10; 28 maximises install compatibility
        targetSdk = 29   // Meta's recommended Portal target (Android 10)
        versionCode = 6
        versionName = "0.1.5"
    }

    buildFeatures {
        buildConfig = true
    }

    // The frame URL is the only configuration knob. Override per build type if needed.
    buildTypes {
        getByName("debug") {
            buildConfigField("String", "FRAME_URL", "\"https://highlights-immich.viktorbarzin.me\"")
        }
        getByName("release") {
            isMinifyEnabled = false
            buildConfigField("String", "FRAME_URL", "\"https://highlights-immich.viktorbarzin.me\"")
        }
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
    // Intentionally none: pure platform WebView + DreamService keeps the build
    // fast and the APK tiny, and guarantees Android-10 compatibility.
}
