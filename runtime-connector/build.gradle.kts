plugins {
    id("com.android.application")
}

android {
    namespace = "top.linkora.runtime.connector"
    compileSdk = 37

    defaultConfig {
        applicationId = "top.linkora.runtime.connector"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "2026.10.4-1"

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.directories.add("../app/src/main/jniLibs")
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += "**/libcloudflared.so"
        }
    }
}
