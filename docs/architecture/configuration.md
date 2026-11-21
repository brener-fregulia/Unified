# Configuration and Data Policy

Status labels follow [AGENTS.md](../../AGENTS.md#decision-status-labels).

The policy requirements and the technical policy below were **approved for M0
on 2026-11-21** (resolves Q-C1). M0 implements the global configuration only.
No configuration code exists yet.

## Policy requirements [Approved 2026-11-21]

| Id | Requirement |
| --- | --- |
| CFG-P1 | Configuration is versioned JSON. |
| CFG-P2 | Global settings, with optional per-world/per-server settings. M0: global only; per-world/per-server is **[Deferred]**. |
| CFG-P3 | Persistent navigation data is stored separately from configuration. |
| CFG-P4 | Features are keyed by stable identifiers. |
| CFG-P5 | Schema migrations are applied when needed. |
| CFG-P6 | Malformed or unknown values are handled safely. |
| CFG-P7 | Disabling a feature never deletes its settings or player data. |
| CFG-P8 | Important files are persisted atomically when practical. |

## Technical policy [Approved 2026-11-21]

### File layout

```text
<game dir>/config/qol_unified/
  config.json              global configuration (M0)
  backups/                 copies made before migration or after corruption
<game dir>/qol_unified/    reserved for navigation data (M4, not specified)
```

Directory names use the approved mod id `qol_unified` (see the
[overview](overview.md#build-bootstrap-approved-2026-11-21)). Per-world/per-server files are
**[Deferred]** to a later milestone.

### Document shape

```json
{
  "schemaVersion": 1,
  "features": {
    "food_hud": {
      "enabled": true,
      "settings": {
        "show_saturation": true
      }
    }
  }
}
```

- `schemaVersion` is an integer, starting at 1, incremented only when a
  migration is required.
- `enabled` is written only when the user has made an explicit choice. If
  absent, the feature's effective default applies (see compatibility rules
  in the [M0 specification](../specifications/m0-platform-foundations.md)).
- A setting absent from `settings` uses its declared default.

### Safe handling

| Situation | Behavior |
| --- | --- |
| File missing | Use defaults; create the file on first save. |
| File is not valid JSON | Copy it to `backups/`, log a warning, use defaults. Never silently overwrite the original without a backup. |
| `schemaVersion` older than current | Back up, then apply sequential migrations (`n → n+1`). |
| `schemaVersion` newer than current | Load what is understood; do not save (read-only) to avoid destroying data written by a newer version; warn the user. |
| Unknown feature id | Preserve its entry unchanged on save. |
| Unknown setting key | Preserve unchanged on save. |
| Value of wrong type or out of range | Use the default at runtime, log a warning, keep the original value in the file until the user changes that setting. |

### Persistence

- Write to a temporary file in the same directory, then replace the target
  with an atomic move; fall back to a non-atomic replace only if the file
  system does not support atomic moves, and log it.
- Save when the settings screen applies changes, not on every widget change.
  Saving happens before runtime apply, as approved in the
  [M0 transaction semantics](../specifications/m0-platform-foundations.md#72-transaction-and-failure-semantics-approved-2026-11-21).
- Disabling a feature changes only its `enabled` value (CFG-P7).

## Open questions

- **Q-C1 [Resolved 2026-11-21]** CFG-P1 to CFG-P8 approved.
- **Q-C2 [Open]** Precedence rules between global and per-world/per-server
  settings, and which settings may be overridden per world/server.
- **Q-C3 [Open]** Should per-server settings key on server address, and how
  are address changes handled?
- **Q-C4 [Open]** Are the configuration and navigation data formats intended
  to be read by the future Horizon implementation? If yes, they need a
  written format specification, not just code.
