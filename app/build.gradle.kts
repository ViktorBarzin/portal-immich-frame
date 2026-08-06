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
        versionCode = 8
        versionName = "0.1.7"
    }

    buildFeatures {
        buildConfig = true
    }

    // The frame URL is the only configuration knob, and it is settable at RUNTIME
    // over adb (see FrameUrlStore / README) — this is only the built-in DEFAULT
    // used until a device is re-pointed. Override the default at build time with
    // -PframeUrl=<url>; prefer the runtime knob for new devices.
    val frameUrl = (project.findProperty("frameUrl") as String?)
        ?: "https://highlights-immich.viktorbarzin.me"
    buildTypes {
        getByName("debug") {
            buildConfigField("String", "FRAME_URL", "\"$frameUrl\"")
        }
        getByName("release") {
            isMinifyEnabled = false
            buildConfigField("String", "FRAME_URL", "\"$frameUrl\"")
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
    // Intentionally none that SHIP: pure platform WebView + DreamService keeps the
    // build fast and the APK tiny, and guarantees Android-10 compatibility.
    // Test-only deps are fine — they never enter the APK.
    testImplementation("junit:junit:4.13.2")
}
