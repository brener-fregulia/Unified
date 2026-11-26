plugins {
    id("net.fabricmc.fabric-loom")
}

// Development-only runtime tests for Unified Settings (never published, no version of its own). The mods under test
// are the real production JARs, nested library included, added with fabric.addMods; the library is compile-only here
// so the copy that runs is the one Fabric resolves from those JARs.
version = "0.0.0"

dependencies {
    minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")
    implementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")
    implementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")
    implementation("net.fabricmc:fabric-language-kotlin:${providers.gradleProperty("fabric_kotlin_version").get()}")
    compileOnly(project(":libs:settings"))
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.encoding = "UTF-8"
}

val qolJar = project(":mods:qol").tasks.named<Jar>("jar")
val optimizationsJar = project(":mods:optimizations").tasks.named<Jar>("jar")
val scenarios = mapOf(
    "QolOnly" to listOf(qolJar),
    "OptimizationsOnly" to listOf(optimizationsJar),
    "Both" to listOf(qolJar, optimizationsJar),
)

loom {
    runs {
        scenarios.forEach { (name, jars) ->
            register("gameTest$name") {
                client()
                name("Unified Settings runtime ($name)")
                runDir("build/run-gametest-$name")
                property("fabric.client.gametest")
                property("fabric.client.gametest.modid", "unified_settings_runtime_test")
                property("unified_settings.test.scenario", name)
                property("fabric.addMods", jars.joinToString(File.pathSeparator) {
                    it.get().archiveFile.get().asFile.absolutePath
                })
                ideConfigGenerated(false)
            }
        }
    }
}

scenarios.forEach { (name, jars) ->
    tasks.matching { it.name == "runGameTest$name" }.configureEach { dependsOn(jars) }
}
