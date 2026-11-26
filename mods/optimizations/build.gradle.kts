plugins {
    id("net.fabricmc.fabric-loom")
}

// This module's own version and artifact name, from mods/optimizations/gradle.properties.
version = property("mod_version") as String
group = providers.gradleProperty("maven_group").get()

base {
    archivesName = property("archives_base_name") as String
}

loom {
    mods {
        register("unified_optimizations") {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")
    implementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")
    // No Fabric API yet: the foundation needs only the loader. Features add what they need, when approved.

    testImplementation(platform("org.junit:junit-bom:${providers.gradleProperty("junit_version").get()}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    val testRunDir = layout.buildDirectory.dir("test-run")
    workingDir(testRunDir)
    doFirst { testRunDir.get().asFile.mkdirs() }
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.encoding = "UTF-8"
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version,
        "minecraft_version" to providers.gradleProperty("minecraft_version").get(),
        "loader_version" to providers.gradleProperty("loader_version").get(),
    )
    inputs.properties(props)
    filesMatching("fabric.mod.json") {
        expand(props)
    }
}
