plugins {
    id("net.fabricmc.fabric-loom")
}

version = providers.gradleProperty("mod_version").get()
group = providers.gradleProperty("maven_group").get()

base {
    archivesName = providers.gradleProperty("archives_base_name")
}

// Development-only fixture mod (sample features and client game tests). It is never part of the
// distributable JAR: the jar task packages only the main source set.
val devFixtures: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().runtimeClasspath + sourceSets.main.get().output
}

loom {
    mods {
        register("qol_unified") {
            sourceSet(sourceSets.main.get())
        }
        register("qol_unified_dev") {
            sourceSet(devFixtures)
        }
    }
    runs {
        // Manual QA with sample features, in a disposable game directory.
        register("fixtureClient") {
            client()
            name("Fixture Client")
            source(devFixtures)
            runDir("run-fixtures")
        }
        // Automated client game tests. "main" seeds a fresh configuration; "restart" reuses it.
        for ((scenario, dir) in listOf(
                "main" to "build/run-gametest",
                "restart" to "build/run-gametest",
                "newer" to "build/run-gametest-newer",
                "corrupt" to "build/run-gametest-corrupt")) {
            register("gameTest" + scenario.replaceFirstChar { it.uppercase() }) {
                client()
                name("Game Test ($scenario)")
                source(devFixtures)
                runDir(dir)
                property("fabric.client.gametest")
                property("fabric.client.gametest.modid", "qol_unified_dev")
                property("qol_unified.dev.scenario", scenario)
                ideConfigGenerated(false)
            }
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")
    implementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")
    implementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")

    testImplementation(platform("org.junit:junit-bom:${providers.gradleProperty("junit_version").get()}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    // Keep log files written during tests out of the project directory.
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

// Keep the development fixtures compiling in CI without packaging them.
tasks.check {
    dependsOn(devFixtures.classesTaskName)
}
