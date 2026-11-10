plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.amqatar.marquee.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.amqatar.marquee.tv"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-playertest"
    }

    signingConfigs {
        // A fixed debug key committed to the repo, so every CI test build installs over the
        // previous one. Debug builds only; the release key is never committed.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        // Both native libraries may ship the NDK C++ runtime; one copy is enough.
        jniLibs.pickFirsts += listOf("**/libc++_shared.so")
    }
}

dependencies {
    implementation(project(":shared"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.tv.material)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.nextlib.media3ext)
    implementation(libs.libmpv)

    implementation(libs.serialization.json)
}
