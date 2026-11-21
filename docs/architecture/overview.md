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

## Core decisions [Approved 2026-11-21]

| Topic | Decision |
| --- | --- |
| Feature descriptor and registry | Explicit, code-defined registry per [M0 §3](../specifications/m0-platform-foundations.md#3-feature-registration-contract-approved-2026-11-21) |
| Configuration | Global only in M0; per-world/per-server deferred ([configuration](configuration.md)) |
| Settings UI | Native Minecraft screen widgets; no configuration UI library (resolves Q-A2) |
| Entry point | Configurable key binding, unassigned by default; Mod Menu optional and deferred (resolves Q-A3) |
| Conflicts | Disable by default only without an explicit player choice; explicit choices win with a visible warning |
| Master switch | None in M0 |
| Restart-required changes | Indicated in the settings screen |
| Testing | JUnit 5 unit tests; manual in-game QA initially |
| Plugin API | None |
| Diagnostics | Local, feature-specific logging in Core; no remote telemetry ([diagnostics](diagnostics.md)) |
| Compatibility detection | Pure policy in `core.compat`; Fabric Loader's `isModLoaded` is used only at the client boundary ([M0 implementation](../specifications/m0-platform-foundations.md#compatibility-implementation-m07)) |

Lifecycle failure handling and settings transaction semantics were approved
on 2026-11-21 (see the
[M0 specification](../specifications/m0-platform-foundations.md#11-open-questions)).

## Startup performance [Approved 2026-11-21]

No startup optimization is scheduled. The qualitative startup policy in
[AGENTS.md](../../AGENTS.md#engineering-rules) applies. Early configuration
loading is deferred. See the
[startup performance discovery](../discovery/startup-performance-minecraft-26.3.md#decisions-2026-11-21).

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
- **Q-A2 [Resolved 2026-11-21]** Native Minecraft screen widgets; no
  configuration UI library.
- **Q-A3 [Resolved 2026-11-21]** Mod Menu integration is optional and
  deferred.
- **Q-A4 [Resolved 2026-11-21]** Mod ID `qol_unified`, package
  `io.github.brener_fregulia.qolunified`.
- **Q-A5 [Resolved 2026-11-21]** JUnit 5 for unit tests; manual in-game QA
  initially. In-game test automation is deferred.
