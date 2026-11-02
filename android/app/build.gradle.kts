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
        versionCode = 5
        versionName = "0.5-minimal"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["appLabel"] = "SIXPANE"
    }
    buildTypes {
        debug {
            // Independent one-hand preview ID; never overwrite old differently signed debug apps.
            applicationIdSuffix = ".preview.onehand"
            versionNameSuffix = "-preview"
            manifestPlaceholders["appLabel"] = "SIXPANE One-Hand Preview"
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
