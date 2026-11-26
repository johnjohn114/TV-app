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
        versionCode = 5
        versionName = "0.3.0-test"
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
            // Install the test build alongside the existing stable app.
            applicationIdSuffix = ".test"
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
