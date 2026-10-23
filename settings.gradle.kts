pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Lets Gradle fetch the JDK the build asks for when it is not already installed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "wearmesh"

include(":wear")

// The MeshCore protocol and Bluetooth link, shared with MeshCore TAK Bridge. A git submodule,
// built from source as part of this build: run `git submodule update --init` after cloning.
includeBuild("meshcore-kotlin")
