version = "1.0.0"

val fabricKotlinVersion = project.extra["fabric_kotlin_version"].toString()

dependencies {
    // The real Unified mods need Fabric Language Kotlin at runtime.
    "implementation"("net.fabricmc:fabric-language-kotlin:$fabricKotlinVersion")
}

/**
 * A copy of a production Unified JAR with unified_settings [lib] nested into it, as Loom's include would do. The
 * production artifact is only read (build it first: ./gradlew :mods:qol:jar :mods:optimizations:jar).
 */
val repacked = mutableMapOf<String, TaskProvider<Jar>>()

fun repack(mod: String, jarName: String, lib: String): TaskProvider<Jar> =
    repacked.getOrPut("$mod-$lib") { createRepack(mod, jarName, lib) }

fun createRepack(mod: String, jarName: String, lib: String): TaskProvider<Jar> {
    val source = rootDir.resolve("../../mods/$mod/build/libs/$jarName")
    val libJar = project(":lib-$lib").tasks.named<Jar>("jar").flatMap { it.archiveFile }
    val meta = layout.buildDirectory.dir("repack-meta/$mod-$lib")
    val writeMeta = tasks.register("repack${mod.replaceFirstChar { it.uppercase() }}Lib${lib.replace(".", "")}Meta") {
        inputs.file(source)
        outputs.dir(meta)
        doLast {
            val json = zipTree(source).matching { include("fabric.mod.json") }.singleFile.readText()
            val nested = json.replaceFirst("{", "{\n  \"jars\": [{ \"file\": \"META-INF/jars/unified-settings-$lib.jar\" }],")
            meta.get().asFile.mkdirs()
            meta.get().file("fabric.mod.json").asFile.writeText(nested)
        }
    }
    return tasks.register<Jar>("repack${mod.replaceFirstChar { it.uppercase() }}Lib${lib.replace(".", "")}") {
        dependsOn(writeMeta)
        archiveFileName = "$mod-with-unified-settings-$lib.jar"
        destinationDirectory = layout.buildDirectory.dir("repacked")
        from(zipTree(source)) { exclude("fabric.mod.json") }
        from(meta)
        from(libJar) { into("META-INF/jars"); rename { "unified-settings-$lib.jar" } }
    }
}

val qolJar = "unified-qol-26.3.0-alpha.1-SNAPSHOT.jar"
val optJar = "unified-optimizations-26.3.0-alpha.1-SNAPSHOT.jar"

fun parent(p: String, task: String) = project(":parent-$p").tasks.named<Jar>(task)

/** Scenario name to the JARs added to the run with fabric.addMods. */
val scenarios: Map<String, List<TaskProvider<Jar>>> = mapOf(
    // Two parents, different nested versions, both orders.
    "ParentsA100B110" to listOf(parent("a", "jarWithLib100"), parent("b", "jarWithLib110")),
    "ParentsA110B100" to listOf(parent("a", "jarWithLib110"), parent("b", "jarWithLib100")),
    "ParentsSameVersion" to listOf(parent("a", "jarWithLib110"), parent("b", "jarWithLib110")),
    // Breaking major version, with and without the depends range.
    "BreakingWithRange" to listOf(parent("a", "jarWithLib100"), parent("b", "jarWithLib200")),
    "BreakingConflictingRanges" to listOf(parent("a", "jarWithLib100"), parent("b", "jarWithLib200Major2Only")),
    "BreakingNoRange" to listOf(parent("a", "jarWithLib100NoRange"), parent("b", "jarWithLib200NoRange")),
    // Installation matrix with the real mods.
    "QolOnly" to listOf(repack("qol", qolJar, "1.0.0")),
    "OptimizationsOnly" to listOf(repack("optimizations", optJar, "1.1.0")),
    "QolAndOptimizations" to listOf(repack("qol", qolJar, "1.0.0"), repack("optimizations", optJar, "1.1.0")),
    "QolAndOptimizationsReversed" to listOf(repack("qol", qolJar, "1.1.0"), repack("optimizations", optJar, "1.0.0")),
)

loom {
    runs {
        scenarios.forEach { (name, jars) ->
            register("spike$name") {
                client()
                runDir("build/run-$name")
                property("spike.scenario", name)
                property("fabric.addMods", jars.joinToString(File.pathSeparator) {
                    it.get().archiveFile.get().asFile.absolutePath
                })
                ideConfigGenerated(false)
            }
        }
    }
}

scenarios.forEach { (name, jars) ->
    tasks.matching { it.name == "runSpike$name" }.configureEach { dependsOn(jars) }
}
