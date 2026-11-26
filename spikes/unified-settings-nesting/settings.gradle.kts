// Disposable runtime spike for M2.1.0 (docs/architecture/unified-settings-integration.md §18). A separate Gradle
// build: it never touches the production modules. Run it with ../../gradlew -p spikes/unified-settings-nesting.
pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        gradlePluginPortal()
    }
    plugins {
        id("net.fabricmc.fabric-loom") version "1.19.4"
    }
}

rootProject.name = "unified-settings-nesting-spike"
include("lib-1.0.0", "lib-1.1.0", "lib-2.0.0", "parent-a", "parent-b", "runner")
