plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// CI passes a monotonically increasing version so the in-app updater can tell
// when a newer build exists. Falls back to 1 for local builds.
val ciVersionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
val ciVersionName = System.getenv("VERSION_NAME") ?: "1.0.$ciVersionCode"

android {
    namespace = "com.plutoforce.tapcopy"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.plutoforce.tapcopy"
        minSdk = 30
        targetSdk = 36
        versionCode = ciVersionCode
        versionName = ciVersionName
    }

    // A fixed, committed key so every build shares one signature and updates
    // install over the top. Self-signed (not a Play key) — fine for sideloading.
    signingConfigs {
        create("shared") {
            storeFile = file("tapcopy.keystore")
            storePassword = "tapcopy"
            keyAlias = "tapcopy"
            keyPassword = "tapcopy"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    // Bundled model: OCR works immediately and all recognition stays on-device.
    implementation("com.google.mlkit:text-recognition:16.0.1")
    // FileProvider for handing the downloaded update APK to the installer.
    implementation("androidx.core:core:1.13.1")
}
