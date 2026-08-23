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
        versionCode = 14
        versionName = "0.1.13"
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

    // Where the app looks for a newer build of itself on startup (FrameUpdater).
    // Empty disables the check entirely, which is the default until the publishing
    // endpoint is settled — a build that points nowhere simply never self-updates.
    val updateUrl = (project.findProperty("updateUrl") as String?) ?: ""

    // Sign with an EXPLICIT keystore when one is given, rather than trusting the
    // implicit ~/.android/debug.keystore. On a GitHub runner that default is not
    // where it is locally, so a CI build happily signed itself with a freshly
    // generated key — which every installed frame then refuses as an update
    // (INSTALL_FAILED_UPDATE_INCOMPATIBLE). Nothing said so; the release simply
    // would not have installed anywhere. Unset = previous behaviour, which is what
    // the local Dockerised build (with its cached keystore volume) relies on.
    val debugKeystore = (project.findProperty("debugKeystore") as String?)
        ?: System.getenv("DEBUG_KEYSTORE_PATH")
    signingConfigs {
        getByName("debug") {
            if (!debugKeystore.isNullOrBlank()) {
                storeFile = file(debugKeystore)
                // The stock Android debug-keystore credentials; the secret is the
                // keystore file itself, which is why it lives in Vault.
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("String", "FRAME_URL", "\"$frameUrl\"")
            buildConfigField("String", "UPDATE_URL", "\"$updateUrl\"")
        }
        getByName("release") {
            isMinifyEnabled = false
            buildConfigField("String", "FRAME_URL", "\"$frameUrl\"")
            buildConfigField("String", "UPDATE_URL", "\"$updateUrl\"")
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
    // The platform's org.json is a throwing stub in JVM unit tests, so the update
    // manifest parser would be untestable without a real one. This is the upstream
    // implementation Android's is derived from, and it is test-only: the APK keeps
    // using the platform class.
    testImplementation("org.json:json:20240303")
}
