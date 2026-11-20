# M0 — Platform Foundations

Status labels follow [AGENTS.md](../../AGENTS.md#decision-status-labels).

**Specification status: Draft.** Goals marked **[Approved]** come from the PO
brief. All contracts, requirements and acceptance criteria are
**[Proposed]** until approved. Implementation must not start before approval.

## 1. Goals [Approved]

- Deliver the smallest functional Core able to **register, configure, enable
  and disable** independent features.
- Provide a centralized settings interface with categories and search.
- Detect known conflicts with other mods while preserving user control.
- Represent restart-required changes honestly.
- Prepare the ground for Food HUD (M1) as the first real feature validating
  the architecture.

## 2. Non-goals

| Non-goal | Status |
| --- | --- |
| Any user-facing feature (Food HUD and others start in M1) | [Approved] |
| Public plugin API or third-party feature registration | [Approved] |
| DI framework, event bus, reflection discovery, plugin loader | [Approved] |
| Per-world/per-server configuration | [Deferred] |
| Navigation data storage | [Deferred] to M4 |
| Horizon implementation | [Deferred] to M5 |
| In-game conflict *resolution* beyond enabling/disabling features | [Proposed] |
| Polished UI styling, icons, animations | [Proposed] |

## 3. Feature registration contract [Proposed]

Each feature provides one **feature descriptor** and one **feature
implementation**. Names below describe the contract, not final Java types.

### 3.1 Descriptor

| Field | Type | Rules |
| --- | --- | --- |
| `id` | string | Matches `^[a-z][a-z0-9_]{1,63}$`; unique; stable once released. |
| `category` | enum | One of the [catalog categories](../product/feature-catalog.md#categories-approved). |
| `nameKey`, `descriptionKey` | translation key | Convention `qol_unified.feature.<id>.name` / `.description`. |
| `defaultEnabled` | boolean | Default when the user has made no explicit choice. |
| `toggleMode` | enum | `LIVE` (applies immediately) or `RESTART_REQUIRED`. |
| `settings` | list of setting descriptors | May be empty. |
| `conflicts` | list of conflict descriptors | May be empty. |

### 3.2 Setting descriptor

| Field | Rules |
| --- | --- |
| `key` | Matches `^[a-z][a-z0-9_]{0,63}$`; unique within the feature. |
| `type` | M0 supports `BOOLEAN`, `INTEGER` (with min/max), `ENUM` (fixed values). |
| `default` | Valid for its type and constraints. |
| `applyMode` | `LIVE` or `RESTART_REQUIRED`. |
| `nameKey` | `qol_unified.feature.<id>.setting.<key>`. |

Other setting types (floats, colors, key bindings, lists) are **[Deferred]**
until a feature needs them.

### 3.3 Conflict descriptor

| Field | Rules |
| --- | --- |
| `modId` | Fabric mod id of the conflicting mod. |
| `reasonKey` | Translation key explaining the conflict to the user. |

### 3.4 Implementation

A feature implementation exposes exactly two operations:

- `enable()` — install behavior (register callbacks, start rendering, etc.).
- `disable()` — remove or neutralize all behavior installed by `enable()`.

Features whose behavior is woven in at load time (for example, Mixins) must
check their effective state at run time instead of relying on unloading.

### 3.5 Registry

- Features are listed **explicitly in code** in one place (no scanning or
  reflection).
- Registration completes during client initialization; the registry is then
  read-only.
- A duplicate id, invalid id, invalid setting descriptor or invalid default
  fails startup with a clear error. These are developer errors and must be
  caught by tests before release.

## 4. Feature lifecycle requirements [Proposed]

### 4.1 States

| State | Meaning |
| --- | --- |
| `ENABLED` | Feature is active. |
| `DISABLED` | Feature is inactive. |
| `FAILED` | `enable()` or `disable()` threw; feature is treated as inactive. |

Separately, each feature has a **desired state** (from configuration and
defaults) and an **effective state** (what is actually running). For
`RESTART_REQUIRED` features they may differ until restart; the UI shows this
as *pending restart*.

### 4.2 Requirements

- **LC-1** At startup, after configuration and compatibility detection, each
  feature whose desired state is enabled is enabled.
- **LC-2** Toggling a `LIVE` feature calls `enable()` or `disable()` once;
  toggling to the current state is a no-op.
- **LC-3** Toggling a `RESTART_REQUIRED` feature only changes the desired
  state and marks it pending restart.
- **LC-4** An exception in one feature's `enable()`/`disable()` is caught and
  logged with the feature id; the feature moves to `FAILED`; other features
  and the game continue.
- **LC-5** A `FAILED` feature can be retried by toggling it off and on; it is
  not retried automatically.
- **LC-6** Disabling a feature never deletes its settings or data (CFG-P7).
- **LC-7** Lifecycle calls happen on the client (render) thread.

## 5. Configuration behavior [Proposed]

Configuration follows [configuration.md](../architecture/configuration.md).
M0 implements only the global file:

- **CFG-1** Load once at startup, before features are enabled.
- **CFG-2** Missing, malformed, newer-version, unknown and invalid values are
  handled as in the [safe handling table](../architecture/configuration.md#safe-handling-proposed).
- **CFG-3** M0 ships `schemaVersion: 1` and a migration mechanism with a test
  migration exercised only by tests.
- **CFG-4** Saving is atomic where supported and happens when the user applies
  changes in the settings screen.
- **CFG-5** `enabled` is persisted only after an explicit user choice.
- **CFG-6** Loading then saving an unchanged file preserves all known and
  unknown entries (semantic round-trip; formatting may differ).

## 6. Compatibility detection behavior [Proposed]

- **CMP-1** At startup, for each feature, check whether any declared
  conflicting mod is loaded, using the Fabric Loader API.
- **CMP-2** Effective default: if a conflict is detected and the user has
  **no explicit choice**, the feature starts disabled.
- **CMP-3** An explicit user choice always wins; detection never changes a
  value the user set.
- **CMP-4** Detected conflicts are logged once at startup and shown in the
  settings screen next to the feature, with the reason.
- **CMP-5** The user can enable a conflicting feature; the UI warns but does
  not block.
- **CMP-6** Detection only reports mods declared in descriptors; it does not
  guess or scan other mods' contents.

## 7. Minimal settings UI requirements [Proposed]

- **UI-1** One screen lists all registered features grouped by category, in
  a stable order.
- **UI-2** Each feature row shows: localized name, enable toggle, and status
  indicators for *pending restart*, *conflict detected* and *failed*.
- **UI-3** A search field filters features by localized name, localized
  description and id, case-insensitively, in the active language.
- **UI-4** Opening a feature shows its settings with widgets for the M0
  setting types; invalid input is rejected before apply.
- **UI-5** *Done* applies and saves changes; *Cancel* discards them.
- **UI-6** When applied changes require restart, the screen says so.
- **UI-7** All strings use translation keys present in `en_us` and `pt_br`.
- **UI-8** The screen works with zero registered features (empty state).
- **UI-9** Entry point: a key binding without a default key, plus any entry
  approved in Q-M2.

## 8. Test strategy [Proposed]

| Layer | Scope | How |
| --- | --- | --- |
| Unit | Registry validation, setting validation, config parse/save/round-trip, migrations, malformed and newer-version files, lifecycle state transitions, failure isolation, conflict default rules, search matching | Automated, without a Minecraft instance |
| Localization | Every key used by the Core and registered features exists in both language files | Automated test |
| In-game | Startup, settings screen, toggling live and restart-required features, conflict display | Manual checklist in a development client; automation per Q-A5 |
| Fixtures | Test-only sample features (live, restart-required, failing, conflicting) | Test sources only; never shipped |

## 9. Acceptance criteria [Proposed]

M0 is complete when all of the following are verified and reported:

1. **AC-1** The mod builds into one JAR that loads in a client and declares
   itself client-only.
2. **AC-2** Registering a duplicate or invalid id fails with a message naming
   the id (unit test).
3. **AC-3** With no config file, the client starts, uses defaults and creates
   `config.json` with `schemaVersion: 1` on first save.
4. **AC-4** A syntactically invalid `config.json` results in a startup with
   defaults and a backup copy of the original in `backups/` (unit test).
5. **AC-5** A file with `schemaVersion` greater than supported is never
   overwritten (unit test).
6. **AC-6** Unknown feature ids and unknown setting keys survive a
   load/save cycle (unit test).
7. **AC-7** Toggling a live fixture feature off and on calls `disable()` then
   `enable()` exactly once each; settings are unchanged (unit test).
8. **AC-8** Toggling a restart-required fixture marks it pending restart and
   leaves its effective state unchanged until restart (unit test).
9. **AC-9** A fixture whose `enable()` throws ends `FAILED` while other
   fixtures end `ENABLED` (unit test).
10. **AC-10** A conflicting fixture with no explicit choice starts disabled;
    with an explicit `enabled: true` it starts enabled (unit test).
11. **AC-11** The settings screen lists fixtures by category, filters by
    search, shows status indicators, and *Cancel* leaves `config.json`
    byte-for-byte unchanged (manual checklist).
12. **AC-12** No translation key is missing in `en_us` or `pt_br` (automated).
13. **AC-13** No required server-side component; the mod works when joining a
    vanilla server (manual).
14. **AC-14** All automated tests pass and `git diff --check` is clean.

## 10. Deferred decisions

| Topic | Deferred to |
| --- | --- |
| Per-world/per-server configuration and precedence (Q-C2, Q-C3) | Later milestone, before first feature needing it |
| Navigation data format and storage | M4 |
| Shared data formats with Horizon (Q-C4) | Before M4 data formats are frozen |
| Additional setting types | When a feature needs them |
| Horizon implementation | M5 |
| License (Q-V1) and distribution (Q-V2) | Before first public release |

## 11. Open questions for the PO

- **Q-M1 [Open]** Approve or amend this specification (contracts §3–§7,
  acceptance criteria §9).
- **Q-M2 [Open]** Settings screen entry points: key binding only, Mod Menu
  integration (Q-A3), a button on a vanilla screen, or a combination?
- **Q-M3 [Open]** Should conflict detection auto-disable by default (CMP-2),
  or only warn?
- **Q-M4 [Open]** Is a master switch to disable all features required in M0?
- **Q-M5 [Open]** Should restart-required features offer an in-game
  "restart needed" notice outside the settings screen?
- Also blocking for M0 implementation: Q-A1 (Fabric API), Q-A2 (UI library),
  Q-A4 (mod id and package), Q-A5 (test tooling), Q-C1 (configuration policy)
  — see [overview](../architecture/overview.md#open-questions) and
  [configuration](../architecture/configuration.md#open-questions).
