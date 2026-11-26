import java.util.Properties

plugins {
    id("net.fabricmc.fabric-loom") apply false
}

// Toolchain versions come from the monorepo's own gradle.properties, so the spike tests the real setup.
val shared = Properties().apply { rootDir.resolve("../../gradle.properties").reader().use { load(it) } }

subprojects {
    apply(plugin = "net.fabricmc.fabric-loom")
    shared.forEach { (k, v) -> project.extensions.extraProperties.set(k.toString(), v) }
    group = "unified.spike"

    dependencies {
        "minecraft"("com.mojang:minecraft:${shared["minecraft_version"]}")
        "implementation"("net.fabricmc:fabric-loader:${shared["loader_version"]}")
        "implementation"("net.fabricmc.fabric-api:fabric-api:${shared["fabric_api_version"]}")
    }
    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion = JavaLanguageVersion.of(25)
    }
    tasks.withType<JavaCompile>().configureEach {
        options.release = 25
        options.encoding = "UTF-8"
    }
    tasks.named<ProcessResources>("processResources") {
        val props = mapOf("version" to project.version.toString())
        inputs.properties(props)
        filesMatching("fabric.mod.json") { expand(props) }
    }
}
