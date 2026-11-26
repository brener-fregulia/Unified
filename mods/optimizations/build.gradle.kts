plugins {
    id("net.fabricmc.fabric-loom")
    // Kotlin for new code (docs/architecture/monorepo.md, language policy); Mixins stay in Java.
    id("org.jetbrains.kotlin.jvm")
}

// This module's own version and artifact name, from mods/optimizations/gradle.properties.
version = property("mod_version") as String
group = providers.gradleProperty("maven_group").get()

base {
    archivesName = property("archives_base_name") as String
}

// Development-only client game tests (never in the distributable JAR: the jar task packages only main).
val gametest: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().runtimeClasspath + sourceSets.main.get().output
}

// Development-only DNS probe for the fastipping game test: a counting java.net resolver provider. The JDK loads
// resolver providers through the system class loader, so it goes on the boot class path of that run only.
val dnsprobe: SourceSet by sourceSets.creating

loom {
    mods {
        register("unified_settings") {
            sourceSet("main", ":libs:settings")
        }
        register("unified_optimizations") {
            sourceSet(sourceSets.main.get())
        }
        register("unified_optimizations_gametest") {
            sourceSet(gametest)
        }
    }
    runs {
        // Focused client game tests, each in its own disposable game directory.
        for (scenario in listOf("fastipping", "fastquit")) {
            register("gameTest" + scenario.replaceFirstChar { it.uppercase() }) {
                client()
                name("Game Test ($scenario)")
                source(gametest)
                runDir("build/run-gametest-$scenario")
                property("fabric.client.gametest")
                property("fabric.client.gametest.modid", "unified_optimizations_gametest")
                property("unified_optimizations.test.scenario", scenario)
                if (scenario == "fastipping") {
                    vmArg("-Xbootclasspath/a:" + layout.buildDirectory.dir("classes/java/dnsprobe").get().asFile.absolutePath +
                        File.pathSeparator + dnsprobe.output.resourcesDir!!.absolutePath)
                }
                ideConfigGenerated(false)
            }
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")
    implementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")
    // No Fabric API yet: the foundation needs only the loader. Features add what they need, when approved.

    // Kotlin's standard library at runtime comes from Fabric Language Kotlin, a separate mod the player installs; it is
    // a development and compile dependency here and is never bundled into this JAR.
    // Needed at development runtime by the nested Unified Settings library (key mapping, screen and lifecycle APIs).
    implementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")
    implementation("net.fabricmc:fabric-language-kotlin:${providers.gradleProperty("fabric_kotlin_version").get()}")

    // Unified Settings (Library): compiled against and nested (jar-in-jar) as its own mod, never merged into this JAR.
    // Fabric loads one copy for all Unified mods (docs/architecture/unified-settings-integration.md §18).
    implementation(project(":libs:settings"))
    include(project(":libs:settings"))

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

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.encoding = "UTF-8"
}

// ">=<library version compiled against> <<next major>.0.0" (spike rule §18.4: never be served an older contract).
val settingsRange = (project(":libs:settings").property("mod_version") as String).let {
    ">=$it <${it.substringBefore('.').toInt() + 1}.0.0"
}

// Keep the game tests compiling in CI without packaging them.
tasks.check {
    dependsOn(gametest.classesTaskName, dnsprobe.classesTaskName)
}
tasks.matching { it.name == "runGameTestFastipping" }.configureEach {
    dependsOn(dnsprobe.classesTaskName)
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version,
        "minecraft_version" to providers.gradleProperty("minecraft_version").get(),
        "loader_version" to providers.gradleProperty("loader_version").get(),
        "fabric_kotlin_version" to providers.gradleProperty("fabric_kotlin_version").get(),
        "settings_range" to settingsRange,
    )
    inputs.properties(props)
    filesMatching("fabric.mod.json") {
        expand(props)
    }
}
