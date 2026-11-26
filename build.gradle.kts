// Project Unified: an aggregator for independent Fabric mods (docs/architecture/monorepo.md).
// Loom is declared once here so all modules share one plugin classloader; each module applies it itself.
plugins {
    base
    id("net.fabricmc.fabric-loom") apply false
    id("org.jetbrains.kotlin.jvm") apply false
}

// ---- dist/: the production JARs of every module, collected by the root build ----

/**
 * Resolves exactly one artifact per listed module: its runtime JAR variant (the `jar` task output), without
 * transitive dependencies, so sources, fixtures, tests and third-party libraries never appear. Gradle derives the
 * producing tasks from this configuration, so only the listed modules' JARs are built for it.
 */
val distJars: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
    }
}

dependencies {
    distJars(project(":mods:qol"))
    distJars(project(":mods:optimizations"))
    // The library also travels nested inside each mod; this copy is for inspection, not for installing.
    distJars(project(":libs:settings"))
}

/** Copies the JARs unchanged into dist/; Sync removes anything else there, e.g. JARs of older versions. */
val collectJars by tasks.registering(Sync::class) {
    group = "build"
    description = "Collects the production JARs of all Unified modules into dist/."
    from(distJars)
    into(layout.projectDirectory.dir("dist"))
    // Always sync: Gradle's up-to-date check does not notice files added to dist/ by hand, and the copy is small.
    outputs.upToDateWhen { false }
}

tasks.build {
    dependsOn(collectJars)
}

tasks.clean {
    delete(layout.projectDirectory.dir("dist"))
}
