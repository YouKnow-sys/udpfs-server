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
        maven("https://cache-redirector.jetbrains.com/dl.google.com/dl/android/maven2") {
            name = "GoogleMavenMirror"
            content {
                includeGroup("androidx.lifecycle")
            }
        }
        mavenCentral()
    }
}
rootProject.name = "udpfs-android"
include(":app")
