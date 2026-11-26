pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        gradlePluginPortal()
    }
    plugins {
        id("net.fabricmc.fabric-loom") version providers.gradleProperty("loom_version").get()
    }
}

rootProject.name = "Unified"

// Independent Fabric mods. None depends on another; each builds its own JAR with its own version.
include(":mods:qol")
include(":mods:optimizations")
