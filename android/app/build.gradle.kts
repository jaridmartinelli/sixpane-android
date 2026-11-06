plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
// GitHub Actions provides this keystore only for canonical main-branch builds.
// Pull-request builds continue to use Android's disposable debug signer.
val canonicalKeyPath = System.getenv("SIXPANE_CANONICAL_KEYSTORE_PATH")
val canonicalKeyPassword = System.getenv("SIXPANE_CANONICAL_KEYSTORE_PASSWORD")

android {
    signingConfigs {
        if (!canonicalKeyPath.isNullOrBlank() && !canonicalKeyPassword.isNullOrBlank()) {
            create("canonical") {
                storeFile = file(canonicalKeyPath)
                storePassword = canonicalKeyPassword
                keyAlias = "sixpane-canonical"
                keyPassword = canonicalKeyPassword
            }
        }
    }
    namespace = "com.sixpane.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.sixpane.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 15
        versionName = "0.14-consolidated"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["appLabel"] = "SIXPANE"
    }
    buildTypes {
        debug {
            // Keep the established canonical preview package ID; v14 supports clean installation.
            applicationIdSuffix = ".preview.filterview"
            versionNameSuffix = "-preview"
            manifestPlaceholders["appLabel"] = "SIXPANE"
            signingConfigs.findByName("canonical")?.let { signingConfig = it }
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
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
