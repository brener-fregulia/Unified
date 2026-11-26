# Project Unified

A family of independent, client-side Fabric mods for Minecraft Java Edition
26.3, developed in one repository. Install only the ones you want; none
requires another.

| Mod | What it is for | State |
| --- | --- | --- |
| **Unified: QoL** | Quality-of-life features: Food HUD, Chat Heads, Keyboard Map, Target Info, Sound Control, Shulker Preview, Zoom, Inventory Mouse Tweaks | In development, `mods/qol` |
| **Unified: Optimizations** | Client-side performance and network optimizations | Foundation only (no optimizations yet), `mods/optimizations` |
| **Unified: Graphics** | Visual and rendering features | Planned; not created yet |

Nothing has been released yet.

## Requirements

- JDK 25 (Temurin recommended) on `PATH` or in `JAVA_HOME`.
- The Gradle Wrapper (`gradlew`) downloads the right Gradle; no separate
  install.
- Minecraft 26.3, Fabric Loader and versions are pinned in
  [`gradle.properties`](gradle.properties). Unified: QoL also needs Fabric
  API at runtime.

## Building and testing

| Goal | Command |
| --- | --- |
| Build and unit-test every mod | `./gradlew build` |
| Unified: QoL only | `./gradlew :mods:qol:build` |
| Unified: Optimizations only | `./gradlew :mods:optimizations:build` |
| Unit tests of one mod | `./gradlew :mods:qol:test` |
| A Unified: QoL client game test | `./gradlew :mods:qol:runGameTestZoom` |
| Development client | `./gradlew :mods:qol:runClient` |

On Windows use `.\gradlew.bat`. Each mod's JAR is written to
`mods/<module>/build/libs/` with that mod's own name and version. Game tests
open a real window and use disposable folders under
`mods/qol/build/run-gametest*`.

## Documentation

- [Monorepo architecture](docs/architecture/monorepo.md)
- [Versioning and releases](docs/development/versioning.md) (per mod,
  tags like `qol/v26.3.0`)
- [Linux development setup](docs/development/linux-setup.md)
- Unified: QoL: [README](mods/qol/README.md),
  [feature catalog](docs/qol/product/feature-catalog.md),
  [specifications](docs/qol/specifications/),
  [changelog](mods/qol/CHANGELOG.md)
- Unified: Optimizations: [overview and catalog](docs/optimizations/README.md),
  [changelog](mods/optimizations/CHANGELOG.md)
- Contributors and agents: [AGENTS.md](AGENTS.md)

The project follows Spec-Driven Development: behavior is specified and
approved before it is implemented.

## License

Not yet selected. Until a license is added, no rights are granted to use,
copy or redistribute this code.
