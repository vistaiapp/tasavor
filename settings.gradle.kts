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
        // Required for Cafe Bazaar's Poolakey billing SDK
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Tasavor"
include(":app")
