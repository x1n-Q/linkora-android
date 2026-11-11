plugins {
    id("com.android.application")
}

android {
    namespace = "top.linkora.runtime.php"
    compileSdk = 37

    defaultConfig {
        applicationId = "top.linkora.runtime.php"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "8.4.27-2"

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
            assets.directories.add("../app/src/main/assets/licenses")
            jniLibs.directories.add("../runtime-payloads/php/jniLibs")
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += "**/libphp_runtime.so"
        }
    }
}
