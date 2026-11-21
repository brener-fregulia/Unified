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

## Implementation (M0.6)

Implemented in `core.config`. The design decisions below were made during
implementation, within the approved policy.

**Serialization.** Gson 2.14.0, which Minecraft 26.3 already ships as a
library. It adds no new dependency and is not a Minecraft class, so the Core
stays Minecraft-free.
- Files are decoded as strict UTF-8 and parsed with Gson's `STRICT`
  strictness. Comments, trailing commas and trailing content count as
  corruption.
- The parsed document is kept in memory, and saves start from a deep copy of
  it. Unknown features, settings and fields (including nested structures and
  `null`) are written back unchanged, and numbers keep their original form.
- A save writes only explicit enable choices and settings the player changed.
  Defaults are never written, and invalid original values stay until the
  player changes that setting.

**Load outcomes.**

| Outcome | Cause | Persistence |
| --- | --- | --- |
| `LOADED` | Valid file at the current version | Writable |
| `MISSING` | No file | Writable; created on first save |
| `MIGRATED` | Older version, backed up and migrated in memory | Writable; migrated file written on next save |
| `RECOVERED` | Empty, malformed or non-object JSON, invalid UTF-8, missing or invalid `schemaVersion`, or non-object `features`; backed up | Writable; replaced on next save |
| `RECOVERY_BLOCKED` | As above, or a needed migration, but the backup failed | Read-only |
| `MIGRATION_FAILED` | A migration step is missing or threw | Read-only |
| `READ_FAILED` | The file could not be read | Read-only |
| `NEWER_VERSION` | `schemaVersion` above the supported version | Read-only; understood values are used |

**Backups** are written to
`backups/config-<UTC yyyyMMddTHHmmssZ>-<corrupt|pre-migration>[-n].json`.
Backups are never overwritten.

**Saving.** The file is written to `config.json.tmp`, then moved over the
original atomically. If the file system doesn't support atomic moves, the
move falls back to a plain replace, with a warning. On any failure the
temporary file is deleted and the original is untouched.

**Logs** name only the file name, never full paths or file contents.

**Migrations** run in sequence (`n → n+1`) on a deep copy and set
`schemaVersion` after each step. Production has no migrations, since
version 1 is the baseline. The mechanism is exercised by test-only
migrations.

## Open questions

- **Q-C1 [Resolved 2026-11-21]** CFG-P1 to CFG-P8 approved.
- **Q-C2 [Open]** Precedence rules between global and per-world/per-server
  settings, and which settings may be overridden per world/server.
- **Q-C3 [Open]** Should per-server settings key on server address, and how
  are address changes handled?
- **Q-C4 [Open]** Are the configuration and navigation data formats intended
  to be read by the future Horizon implementation? If yes, they need a
  written format specification, not just code.
