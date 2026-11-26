version = "1.0.0"

// Compiled against this contract version only; the library is never on the runtime classpath of the build.
dependencies {
    "compileOnly"(project(":lib-1.1.0"))
}

/**
 * Packages this parent nesting library [lib] the way Loom's include does (META-INF/jars + a "jars" entry), optionally
 * with a depends range on unified_settings. Variants are needed to swap versions per runtime scenario.
 */
fun variant(lib: String, range: String?, suffix: String = "") {
    val name = "jarWithLib" + lib.replace(".", "") + (if (range == null) "NoRange" else "") + suffix
    val libJar = project(":lib-$lib").tasks.named<Jar>("jar").flatMap { it.archiveFile }
    val metaDir = layout.buildDirectory.dir("variant-meta/$name")
    val writeMeta = tasks.register(name + "Meta") {
        val out = metaDir
        outputs.dir(out)
        doLast {
            val depends = if (range == null) "" else ",\n    \"unified_settings\": \"$range\""
            out.get().asFile.mkdirs()
            out.get().file("fabric.mod.json").asFile.writeText("""
{
  "schemaVersion": 1,
  "id": "spike_parent_b",
  "version": "1.0.0",
  "name": "Spike parent b (nests unified_settings $lib)",
  "environment": "client",
  "entrypoints": {
    "client": ["unified.spike.parentb.ParentB"],
    "unified-settings": ["unified.spike.parentb.ParentB"]
  },
  "jars": [{ "file": "META-INF/jars/unified-settings-$lib.jar" }],
  "depends": {
    "fabricloader": "*"$depends
  }
}
""".trimStart())
        }
    }
    tasks.register<Jar>(name) {
        dependsOn(writeMeta)
        archiveFileName = "spike-parent-b-lib$lib${if (range == null) "-norange" else ""}$suffix.jar"
        destinationDirectory = layout.buildDirectory.dir("variants")
        from(sourceSets["main"].output.classesDirs)
        from(metaDir)
        from(libJar) { into("META-INF/jars"); rename { "unified-settings-$lib.jar" } }
    }
}

for (lib in listOf("1.0.0", "1.1.0", "2.0.0")) {
    variant(lib, ">=1.0.0 <2.0.0")
    variant(lib, null)
}

// Requires the new major only: unsatisfiable together with a parent that requires <2.0.0.
variant("2.0.0", ">=2.0.0 <3.0.0", "Major2Only")
