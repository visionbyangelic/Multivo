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

rootProject.name = "Multivo"

// :server -> Ktor backend (run with ./gradlew :server:run)
// :app    -> Android app (Jetpack Compose)
include(":server")
include(":app")
