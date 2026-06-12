plugins {
    id("com.android.application") version "8.5.2"
    id("org.jetbrains.kotlin.android") version "2.0.21"
}
android {
    namespace = "com.mk.kayipkrallik"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.mk.kayipkrallik"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "2.1"
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
// Sıfır harici bağımlılık: saf Android SDK + Kotlin stdlib. Motor yok, kütüphane yok.

dependencies { implementation(project(":core")) }
