// The wallpaper plugin for Projectivy Launcher on the London projector: it
// gives Projectivy random photos from the same ImmichFrame server the Portal
// frames show. Parked unmerged: Projectivy only enables third-party plugins
// with its paid Premium unlock (GBP 5.99, 2026-11-22), which was declined.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "me.viktorbarzin.portalframe.projectivy"
    compileSdk = 34

    defaultConfig {
        applicationId = "me.viktorbarzin.portalframe.projectivy"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        buildConfig = true
    }

    val frameUrl = (project.findProperty("frameUrl") as String?)
        ?: "https://highlights-immich.viktorbarzin.me"

    // Same explicit-keystore rule as :app (see the comment there).
    val debugKeystore = (project.findProperty("debugKeystore") as String?)
        ?: System.getenv("DEBUG_KEYSTORE_PATH")
    signingConfigs {
        getByName("debug") {
            if (!debugKeystore.isNullOrBlank()) {
                storeFile = file(debugKeystore)
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

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
    implementation(project(":projectivy-api"))
    testImplementation("junit:junit:4.13.2")
    // The platform org.json is a throwing stub in JVM unit tests; see :app.
    testImplementation("org.json:json:20240303")
}
