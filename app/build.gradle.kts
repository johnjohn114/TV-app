plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "org.oldtvrevival.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "org.oldtvrevival.app"
        minSdk = 21
        targetSdk = 28
        versionCode = 4
        versionName = "0.2.1-test"
    }
    buildTypes {
        getByName("debug") {
            // Install the test build alongside the existing stable app.
            applicationIdSuffix = ".test"
            versionNameSuffix = "-test"
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
