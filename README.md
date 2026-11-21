# QoL: Unified

An all-in-one, modular, client-side quality-of-life mod for Minecraft.

QoL: Unified combines independent quality-of-life features (HUD information,
inventory tools, controls, chat, networking and navigation) into a single
client-side mod. Each feature can be enabled or disabled individually from a
central settings screen.

> **Status:** early development (milestone M0). The Core platform is in
> place: feature registry and lifecycle, configuration file, conflict
> detection and a settings screen. No gameplay features exist yet, and
> nothing has been released.

## Initial target

| Item | Target |
| --- | --- |
| Game | Minecraft Java Edition 26.3+ |
| Loader | Fabric Loader |
| Language / runtime | Java 25 (Temurin) |
| Build | Gradle with Fabric Loom |
| Languages | English, Brazilian Portuguese |
| Distribution | One JAR, client-side only |

Exact dependency versions live in [`gradle.properties`](gradle.properties)
and are recorded in the [architecture overview](docs/architecture/overview.md#build-bootstrap-approved-2026-11-21).

A future native implementation for Minecraft Horizon (Rust Edition) is planned
(milestone M5). It shares behavioral specifications, not executable code.

## Building

Requires **JDK 25** (Temurin recommended) on `PATH` or in `JAVA_HOME`. The
Gradle Wrapper downloads the correct Gradle version; no separate Gradle
install is needed.

On Windows (PowerShell or Command Prompt):

```powershell
.\gradlew.bat clean build
```

On macOS/Linux or Git Bash, use `./gradlew clean build`.

The distributable mod JAR is written to `build/libs/`, for example
`build/libs/qol-unified-26.3.0-alpha.1-SNAPSHOT.jar`.

### Development client

```powershell
.\gradlew.bat runClient
```

This launches Minecraft 26.3 with the mod and Fabric API loaded. Game files
are kept in `run/` (ignored by Git). A successful start logs
`QoL: Unified initialized successfully (0 features).`

### Development fixtures

For development only, two kinds of Gradle task load a separate fixture mod
with sample features:
- `runFixtureClient` starts a manual QA client in `run-fixtures/`.
- `runGameTestMain`, then `runGameTestRestart`, `runGameTestNewer` and
  `runGameTestCorrupt`, run automated client tests in `build/run-gametest*`.
  Screenshots are saved there.

Neither affects the distributable JAR.

## Settings

Open **Options → Controls → Key Binds**, find **QoL: Unified → Open QoL:
Unified Settings**, and assign a key; there is no default. Press it in game
or on the title screen to open the settings. Changes apply only when you
press **Done**; **Cancel** or Escape discards them. Settings are stored in
`config/qol_unified/config.json`.

No features are available yet, so the screen currently shows an empty state.

## Documentation

This project follows **Spec-Driven Development (SDD)**: behavior is specified
and approved before it is implemented. See [AGENTS.md](AGENTS.md) for the
workflow.

- Product
  - [Vision](docs/product/vision.md)
  - [Feature catalog](docs/product/feature-catalog.md)
- Architecture
  - [Overview](docs/architecture/overview.md)
  - [Configuration](docs/architecture/configuration.md)
- Specifications
  - [M0 — Platform Foundations](docs/specifications/m0-platform-foundations.md)
- Development
  - [Versioning and release policy](docs/development/versioning.md)
- [Changelog](CHANGELOG.md)

## Versioning

Versions follow `MC_MAJOR.MC_MINOR.MOD_REVISION[-PRERELEASE]`, where the first
two numbers are the target Minecraft release line (e.g. `26.3.0-alpha.1`,
`26.3.0`, `26.3.1`). The first intended distributable version is
`26.3.0-alpha.1`. See the [versioning policy](docs/development/versioning.md).

## License

Not yet selected. Until a license is added, no rights are granted to use,
copy or redistribute this code.
