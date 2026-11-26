# Unified: Optimizations

Status labels follow [AGENTS.md](../../AGENTS.md#decision-status-labels).

Client-side performance and network optimizations for Minecraft Java 26.3
(Fabric), part of [Project Unified](../../README.md). Mod id
`unified_optimizations`, module `:mods:optimizations`, changelog
[mods/optimizations/CHANGELOG.md](../../mods/optimizations/CHANGELOG.md).

## Principles [Approved 2026-11-26]

- **Independent:** installs and runs without Unified: QoL or any other Unified
  mod, and shares no runtime library with them.
- **Client-only:** no server component, custom packets or protocol changes.
- **Verified problems only:** an optimization is specified only for a problem
  confirmed in the current Minecraft code, and no speed claim is made without
  a benchmark.

## Foundation (2026-11-26)

- A client entrypoint that only logs. No optimization, Mixin, Fabric API
  dependency or settings UI yet.
- Its own configuration directory, `config/unified_optimizations/`, separate
  from Unified: QoL's `config/qol_unified/`. No file is written until a
  feature with settings is approved.
- en_us and pt_br texts.
- Whether to add a feature registry, settings or conflict handling is decided
  with the first approved feature (Fast IP Ping Q-FIP7). Unified: QoL's Core is
  not copied by default.

## Catalog

Moved from the Unified: QoL catalog on 2026-11-26. Labels and history are
unchanged.

| Feature | Proposed id | Milestone | Status / notes |
| --- | --- | --- | --- |
| Fast IP Ping (no reverse DNS for literal-IP server-list pings) | `fast_ip_ping` [Proposed] | M1.10 [Proposed] | Direction approved by the PO on 2026-11-26; spec [Fast IP Ping V1](specifications/fast-ip-ping-v1.md) [Proposed], not approved for implementation. Open: Q-FIP2, Q-FIP3, Q-FIP6, Q-FIP7 |
| FastQuit (fast world exit) | `fast_world_exit` | M1.11 (planned by the PO, 2026-11-26; previously M3 [Approved]) | Must not compromise world data safety. Not yet researched |
| Server ping optimization | `server_ping` | M3 [Approved] | Likely restart-required. Relationship to `fast_ip_ping`: Q-FIP6 [Open] |

## Documents

- [Fast IP Ping V1](specifications/fast-ip-ping-v1.md)
- [Monorepo architecture](../architecture/monorepo.md)
- [Versioning](../development/versioning.md)
