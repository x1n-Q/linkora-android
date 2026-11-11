pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "LinkoraMobile"
include(":app")
include(":runtime-node")
include(":runtime-php")
include(":runtime-mariadb")
include(":runtime-connector")
