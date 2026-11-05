plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.sixpane.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.sixpane.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 7
        versionName = "0.7-universal"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["appLabel"] = "SIXPANE"
    }
    buildTypes {
        debug {
            // User-approved side-by-side install; existing One-Hand sessions remain in their original app.
            applicationIdSuffix = ".preview.universal"
            versionNameSuffix = "-preview"
            manifestPlaceholders["appLabel"] = "SIXPANE Universal Preview"
        }
        release { isMinifyEnabled = false }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.webkit:webkit:1.13.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
