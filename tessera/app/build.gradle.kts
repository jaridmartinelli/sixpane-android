plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.tessera.browser"
    compileSdk = 35

    // One fixed key for every build of this app, so each new APK installs over
    // the previous one and keeps all 42 sessions. Debug/test signing only.
    signingConfigs {
        create("tessera") {
            storeFile = rootProject.file("signing/tessera-debug.jks")
            storePassword = "tessera-debug"
            keyAlias = "tessera"
            keyPassword = "tessera-debug"
        }
    }

    defaultConfig {
        applicationId = "app.tessera.browser"
        minSdk = 29
        targetSdk = 35
        versionCode = 3
        versionName = "1.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("tessera") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("tessera")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.13.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
}
