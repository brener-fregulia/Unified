# QoL: Unified

An all-in-one, modular, client-side quality-of-life mod for Minecraft.

QoL: Unified combines independent quality-of-life features (HUD information,
inventory tools, controls, chat, networking and navigation) into a single
client-side mod. Each feature can be enabled or disabled individually from a
central settings screen.

> **Status:** early development (milestone M1). The Core platform is in
> place: feature registry and lifecycle, configuration file, conflict
> detection and a settings screen. The first feature, Food HUD, is
> implemented. Nothing has been released.

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

| Task | Windows (PowerShell or Command Prompt) | Linux, macOS, Git Bash |
| --- | --- | --- |
| Build and unit tests | `.\gradlew.bat clean build` | `./gradlew clean build` |
| Development client | `.\gradlew.bat runClient` | `./gradlew runClient` |

Linux setup (Java 25, WSL2, Prism Launcher): see
[Linux development setup](docs/development/linux-setup.md).

The distributable mod JAR is written to `build/libs/`, for example
`build/libs/qol-unified-26.3.0-alpha.1-SNAPSHOT.jar`.

### Development client

`runClient` launches Minecraft 26.3 with the mod and Fabric API loaded. Game files
are kept in `run/` (ignored by Git). A successful start logs
`QoL: Unified initialized successfully (N features).`

### Development fixtures

For development only, two kinds of Gradle task load a separate fixture mod
with sample features:
- `runFixtureClient` starts a manual QA client in `run-fixtures/`.
- `runGameTestMain`, then `runGameTestRestart`, `runGameTestNewer` and
  `runGameTestCorrupt`, run automated client tests in `build/run-gametest*`.
  `runGameTestFoodhud` covers Food HUD in a disposable world. With
  `-PappleSkinJar=<path to a downloaded AppleSkin JAR>`, `runGameTestAppleskin`
  captures the same situations with AppleSkin for comparison. Screenshots are
  saved there.

Neither affects the distributable JAR.

## Settings

Open **Options → Controls → Key Binds**, find **QoL: Unified → Open QoL:
Unified Settings**, and assign a key; there is no default. Press it in game
or on the title screen to open the settings. Changes apply only when you
press **Done**; **Cancel** or Escape discards them. Settings are stored in
`config/qol_unified/config.json`.

The first feature is **Food HUD** (HUD & Information). It shows your
saturation on the hunger bar, and blinking previews of what held food would
restore. It can also add food values to tooltips and a food line to the F3
screen; turn that line on in the F3 options (F3 + F6). Previews are
estimates, and it never shows exhaustion. It turns itself off by default when
AppleSkin is installed. See the
[specification](docs/specifications/food-hud-v1.md).

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
  - [Linux development setup](docs/development/linux-setup.md)
- [Changelog](CHANGELOG.md)

## Versioning

Versions follow `MC_MAJOR.MC_MINOR.MOD_REVISION[-PRERELEASE]`, where the first
two numbers are the target Minecraft release line (e.g. `26.3.0-alpha.1`,
`26.3.0`, `26.3.1`). The first intended distributable version is
`26.3.0-alpha.1`. See the [versioning policy](docs/development/versioning.md).

## License

Not yet selected. Until a license is added, no rights are granted to use,
copy or redistribute this code.
