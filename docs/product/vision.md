# Product Vision

Status labels follow [AGENTS.md](../../AGENTS.md#decision-status-labels).

## Summary

QoL: Unified is a public, modular, 100% client-side quality-of-life mod for
Minecraft. It replaces a collection of single-purpose mods with one installed
mod whose features can be enabled, disabled and configured individually.

## Targets

| Target | Decision | Status |
| --- | --- | --- |
| Minecraft Java Edition 26.3+ | Initial game target | [Approved] |
| Fabric Loader | Mod loader | [Approved] |
| Java 25 (Temurin) | Language and runtime | [Approved] |
| Gradle with Fabric Loom | Build system | [Approved] |
| Windows 11 | Primary development environment | [Approved] |
| English, Brazilian Portuguese | Supported languages | [Approved] |
| One client-side JAR | Distribution form | [Approved] |
| Minecraft Horizon (Rust Edition) | Future native implementation (M5) | [Approved] |
| Shared behavior specs and compatible data formats with Horizon, where practical | Cross-edition strategy | [Approved] |
| Shared executable code between Java and Rust | Not required | [Approved] |

Specific versions of Fabric Loader, Fabric Loom, mappings and any library are
not yet chosen; they are verified at build bootstrap. **[Open]**

## Product principles [Approved]

1. One installed mod containing multiple independent features.
2. Features can be enabled or disabled individually.
3. Centralized configuration interface with categories and search.
4. No required server-side component.
5. Detect known conflicts with other mods and preserve user control.
6. Respect vanilla multiplayer synchronization and server authority.
7. Keep existing player data safe, particularly inventory and navigation data.
8. Favor simple implementations and small, maintainable contracts.
9. No public plugin system in V1.
10. No unnecessary architecture or premature abstractions.

## Non-goals [Approved]

- Server-side features or a required server companion mod.
- A public API or plugin system for third-party add-ons (V1).
- Copying third-party code or assets. Referenced mods are behavioral
  inspiration only.

## Milestones [Approved]

| Milestone | Scope |
| --- | --- |
| M0 — Platform Foundations | Smallest Core able to register, configure, enable and disable independent features |
| M1 — Essentials | First features; Food HUD is the first real feature and validates the architecture |
| M2 — Inventory & Information | Inventory and information features |
| M3 — Performance & Networking | Network and world-exit improvements |
| M4 — Maps & Navigation | Minimap, world map, waypoints, exploration data |
| M5 — Horizon Compatibility | Native Horizon implementation |

The assignment of individual features to M1 and M2 is not yet decided; see
the [feature catalog](feature-catalog.md). **[Open]**

## Open questions

- **Q-V1 [Open]** Project license.
- **Q-V2 [Open]** Distribution channels (e.g. Modrinth, CurseForge, GitHub
  Releases).
- **Q-V3 [Open]** Supported Minecraft version range policy: only the latest
  release, or also older versions after 26.3?
