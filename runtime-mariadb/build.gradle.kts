plugins {
    id("com.android.application")
}

android {
    namespace = "top.linkora.runtime.mariadb"
    compileSdk = 37

    defaultConfig {
        applicationId = "top.linkora.runtime.mariadb"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "13.0.2-1"

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
            assets.directories.add("../runtime-payloads/mariadb/assets")
            jniLibs.directories.add("../runtime-payloads/mariadb/jniLibs")
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += "**/libmariadbd_runtime.so"
        }
    }
}
