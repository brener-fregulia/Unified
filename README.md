# QoL: Unified

An all-in-one, modular, client-side quality-of-life mod for Minecraft.

QoL: Unified combines independent quality-of-life features (HUD information,
inventory tools, controls, chat, networking and navigation) into a single
client-side mod. Each feature can be enabled or disabled individually from a
central settings screen.

> **Status:** pre-implementation. The repository currently contains
> specifications only (milestone M0.1). There is no build and no release yet.

## Initial target

| Item | Target |
| --- | --- |
| Game | Minecraft Java Edition 26.3+ |
| Loader | Fabric Loader |
| Language / runtime | Java 25 (Temurin) |
| Build | Gradle with Fabric Loom |
| Languages | English, Brazilian Portuguese |
| Distribution | One JAR, client-side only |

Exact library and tool versions are verified at build bootstrap, not assumed.

A future native implementation for Minecraft Horizon (Rust Edition) is planned
(milestone M5). It shares behavioral specifications, not executable code.

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

## License

Not yet selected. Until a license is added, no rights are granted to use,
copy or redistribute this code.
