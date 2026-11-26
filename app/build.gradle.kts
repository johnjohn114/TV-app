plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val persistentKeystorePath = System.getenv("ANDROID_KEYSTORE_PATH")

android {
    namespace = "org.oldtvrevival.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "org.oldtvrevival.app"
        minSdk = 21
        targetSdk = 28
        versionCode = 10
        versionName = "26.3.5"
    }
    lint {
        // This app is distributed directly as an APK for legacy Android TV devices,
        // not through Google Play. Keep targetSdk 28 for Android 5.0 compatibility.
        disable += "ExpiredTargetSdkVersion"
    }
    signingConfigs {
        create("persistent") {
            if (!persistentKeystorePath.isNullOrBlank()) {
                storeFile = file(persistentKeystorePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("debug") {
            // Keep the established package identity for existing installations.
            applicationIdSuffix = ".test2"
            if (!persistentKeystorePath.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("persistent")
            }
        }
        getByName("release") {
            // The installed .test2 app must be upgradeable by this signed release.
            applicationIdSuffix = ".test2"
            isMinifyEnabled = false
            if (!persistentKeystorePath.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("persistent")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}
