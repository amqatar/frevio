plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.amqatar.marquee.tv"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.amqatar.marquee.tv"
        minSdk = 26
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
        // nextlib and libmpv each bundle FFmpeg 9 as shared libraries with the same names.
        // Keep one copy: libmpv's (declared first below), the fuller build mpv needs.
        jniLibs.pickFirsts += listOf(
            "**/libc++_shared.so", "**/libavcodec.so", "**/libavformat.so", "**/libavutil.so",
            "**/libavfilter.so", "**/libavdevice.so", "**/libswresample.so", "**/libswscale.so",
            "**/libpostproc.so", "**/libdav1d.so",
        )
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
    implementation(libs.libmpv)
    implementation(libs.nextlib.media3ext)

    implementation(libs.serialization.json)
}
