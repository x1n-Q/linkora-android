plugins {
    id("com.android.application")
}

android {
    namespace = "top.linkora.runtime.node"
    compileSdk = 37

    defaultConfig {
        applicationId = "top.linkora.runtime.node"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "24.22.1-1"

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
            assets.directories.add("../runtime-payloads/node/assets")
            assets.directories.add("../app/src/main/assets/licenses")
            jniLibs.directories.add("../runtime-payloads/node/jniLibs")
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += "**/libnode_runtime.so"
        }
    }
}
