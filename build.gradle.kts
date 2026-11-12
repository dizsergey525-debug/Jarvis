plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.filko.jarvis"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.filko.jarvis"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }
}
