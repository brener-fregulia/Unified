# Architecture Overview

Status labels follow [AGENTS.md](../../AGENTS.md#decision-status-labels).

## Style [Approved]

A **modular monolith**: one mod, one JAR, with features as independent modules
inside it. Features own their configuration and behavior and do not access
each other's private implementation.

Explicitly excluded unless a demonstrated need is approved:

- Dependency injection frameworks.
- Event buses.
- Reflection-based module discovery.
- Custom plugin loaders.
- A public plugin API (excluded for V1 by product principle).

## Components [Approved as concepts]

The component list is approved; the responsibilities below are
**[Proposed]** refinements.

| Component | Responsibility |
| --- | --- |
| Platform Core | Mod entrypoint; wires the other Core components at client start. |
| Feature Registry | Holds the explicit, code-defined list of features; validates identifiers. |
| Feature Lifecycle | Applies enable/disable transitions; isolates failures; tracks restart-required state. |
| Configuration Service | Loads, validates, migrates and saves configuration. See [configuration](configuration.md). |
| Compatibility Detection | Detects installed mods known to conflict with a feature. |
| UI integration | Central settings screen with categories and search. |
| Feature modules | One per feature; own behavior, settings and translations. |
| Platform adapters | Thin wrappers over Minecraft/Fabric APIs, only where justified. |

## Dependency direction [Proposed]

```text
Feature modules ──► Core (Registry, Lifecycle, Configuration, Compatibility)
UI integration  ──► Core
Core            ──► Minecraft / Fabric (through adapters where justified)
```

- Features depend on the Core, never on other features.
- The Core never depends on a concrete feature; it only sees feature
  descriptors from the registry.
- Core logic that does not need the game (registry validation, configuration
  parsing, migrations, lifecycle state, conflict resolution) is kept free of
  Minecraft classes so it can be unit-tested without a game instance.

## Restart-required behavior [Approved]

Some features may need a game restart when toggled or reconfigured (for
example, when their behavior is installed at load time). The system must
represent this honestly: the UI shows that a change takes effect after
restart, and the runtime state reflects what is actually active.

## Startup performance

See the [startup performance discovery](../discovery/startup-performance-minecraft-26.3.md)
(M0.3). It proposes, but does not approve, an early read-only configuration
read for Mixin gating.

## Horizon [Approved direction]

Horizon (M5) is a separate native implementation. The Java codebase does not
contain Horizon code or abstractions for it. What is shared is the written
behavior in `docs/specifications/` and, where practical, compatible data
formats (configuration, waypoints, exploration data).

## Build bootstrap [Approved 2026-11-21]

Approved in M0.2. Versions verified 2026-11-21.

| Item | Decision |
| --- | --- |
| Minecraft | Java Edition 26.3 (non-obfuscated) |
| Java | 25 (bytecode release 25) |
| Build | Gradle Wrapper 9.9.2, Kotlin DSL |
| Loom | `net.fabricmc.fabric-loom` 1.19.4 (non-remapping plugin for 26.1+) |
| Fabric Loader | 0.20.1 |
| Fabric API | 0.166.2+26.3, approved dependency (resolves Q-A1) |
| Mod ID / package | `qol_unified` / `io.github.brener_fregulia.qolunified` (resolves Q-A4) |
| Environment | Client only; one `client` entrypoint, no `main`/server entrypoint |
| Unit tests | JUnit 5, from the Core milestone (resolves the unit-test part of Q-A5) |
| CI | GitHub Actions build verification only; no publishing or tagging |

Sources: `meta.fabricmc.net` (game and loader versions),
`maven.fabricmc.net` metadata (Loom, Fabric API), `services.gradle.org`
(Gradle), and the official `FabricMC/fabric-example-mod` 26.3 setup.

## Open questions

- **Q-A1 [Resolved 2026-11-21]** Fabric API is an approved dependency.
- **Q-A2 [Open]** Build the settings UI with vanilla screen widgets only, or
  use a configuration library? (Any library is a dependency requiring
  approval.)
- **Q-A3 [Open]** Integrate with Mod Menu as an optional entry point?
- **Q-A4 [Resolved 2026-11-21]** Mod ID `qol_unified`, package
  `io.github.brener_fregulia.qolunified`.
- **Q-A5 [Partly resolved 2026-11-21]** JUnit 5 is approved for unit tests.
  Whether to use Fabric's game test tooling for in-game tests remains open.
