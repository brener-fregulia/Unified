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

loom {
    mods {
        register("unified_settings") {
            sourceSet("main", ":libs:settings")
        }
        register("unified_optimizations") {
            sourceSet(sourceSets.main.get())
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
