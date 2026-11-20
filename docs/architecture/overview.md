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

## Horizon [Approved direction]

Horizon (M5) is a separate native implementation. The Java codebase does not
contain Horizon code or abstractions for it. What is shared is the written
behavior in `docs/specifications/` and, where practical, compatible data
formats (configuration, waypoints, exploration data).

## Open questions

- **Q-A1 [Open]** Depend on Fabric API, or use only Fabric Loader and Mixin?
- **Q-A2 [Open]** Build the settings UI with vanilla screen widgets only, or
  use a configuration library? (Any library is a dependency requiring
  approval.)
- **Q-A3 [Open]** Integrate with Mod Menu as an optional entry point?
- **Q-A4 [Open]** Mod ID and Java base package name (e.g. mod id
  `qol_unified`).
- **Q-A5 [Open]** Unit test framework and whether to use Fabric's game test
  tooling for in-game tests.
