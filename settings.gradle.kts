pluginManagement {
    repositories {
        maven("https://cache-redirector.jetbrains.com/dl.google.com/dl/android/maven2") {
            name = "GoogleMavenMirror"
            content {
                includeGroupByRegex("androidx\\..*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google\\.testing.*")
            }
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://cache-redirector.jetbrains.com/dl.google.com/dl/android/maven2") {
            name = "GoogleMavenMirror"
            content {
                includeGroupByRegex("androidx\\..*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google\\.testing.*")
            }
        }
        google()
        mavenCentral()
    }
}
rootProject.name = "udpfs-android"
include(":app")
