// Project Unified: an aggregator for independent Fabric mods (docs/architecture/monorepo.md).
// Loom is declared once here so all modules share one plugin classloader; each module applies it itself.
plugins {
    id("net.fabricmc.fabric-loom") apply false
    id("org.jetbrains.kotlin.jvm") apply false
}
