import com.android.build.api.dsl.ApplicationExtension

plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
}

val releaseSigningEnvironment = mapOf(
    "storeFile" to System.getenv("LINKORA_KEYSTORE_PATH"),
    "storePassword" to System.getenv("LINKORA_STORE_PASSWORD"),
    "keyAlias" to System.getenv("LINKORA_KEY_ALIAS"),
    "keyPassword" to System.getenv("LINKORA_KEY_PASSWORD"),
)
val hasReleaseSigning = releaseSigningEnvironment.values.all { !it.isNullOrBlank() }

subprojects {
    if (name.startsWith("runtime-")) {
        configurations.matching { it.name.endsWith("RuntimeClasspath") }.configureEach {
            exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
        }
    }

    pluginManager.withPlugin("com.android.application") {
        extensions.configure<ApplicationExtension> {
            if (hasReleaseSigning) {
                val production = signingConfigs.create("production") {
                    storeFile = file(checkNotNull(releaseSigningEnvironment["storeFile"]))
                    storePassword = releaseSigningEnvironment["storePassword"]
                    keyAlias = releaseSigningEnvironment["keyAlias"]
                    keyPassword = releaseSigningEnvironment["keyPassword"]
                    enableV1Signing = true
                    enableV2Signing = true
                    enableV3Signing = true
                    enableV4Signing = true
                }
                buildTypes.getByName("release").signingConfig = production
            }
        }
    }
}
