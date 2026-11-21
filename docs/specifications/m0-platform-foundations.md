# M0 — Platform Foundations

Status labels follow [AGENTS.md](../../AGENTS.md#decision-status-labels).

**Specification status: Approved (2026-11-21).** The technical lead approved
all M0 contracts (§3–§7) and acceptance criteria (§9), with the corrections
listed in §12. Items still marked **[Proposed]** are outside the approved
contract.

Implementation status is tracked in [§12](#12-implementation-status).

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
| Public plugin API or third-party feature registration | [Approved 2026-11-21] |
| DI framework, event bus, reflection discovery, plugin loader | [Approved] |
| Per-world/per-server configuration | [Deferred] (approved 2026-11-21) |
| Master switch for all features | [Approved 2026-11-21] (not in M0) |
| Mod Menu integration | [Deferred] (approved 2026-11-21; optional) |
| Remote telemetry or report submission | [Approved 2026-11-21] (not in M0; see [diagnostics](../architecture/diagnostics.md)) |
| Startup optimizations, Mixin gating, early configuration loading | [Approved 2026-11-21] (not scheduled; see [discovery](../discovery/startup-performance-minecraft-26.3.md#decisions-2026-11-21)) |
| Navigation data storage | [Deferred] to M4 |
| Horizon implementation | [Deferred] to M5 |
| In-game conflict *resolution* beyond enabling/disabling features | [Proposed] |
| Polished UI styling, icons, animations | [Proposed] |

## 3. Feature registration contract [Approved 2026-11-21]

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

**[Approved 2026-11-21]** Features read current values from an immutable
**effective settings snapshot** owned by the Core. The Core replaces the
snapshot when changes are applied, so no callback is needed. Desired and
effective settings are kept separately (§4.5).

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

**[Approved 2026-11-21]** `disable()` must be safe to call after a partial
or failed `enable()`. It must only undo what is actually installed. This
allows the rollback in §4.3.

### 3.5 Registry

- Features are listed **explicitly in code** in one place (no scanning or
  reflection).
- Registration completes during client initialization; the registry is then
  read-only.
- A duplicate id, invalid id, invalid setting descriptor or invalid default
  fails startup with a clear error. These are developer errors and must be
  caught by tests before release.

## 4. Feature lifecycle requirements

### 4.1 Desired and effective state [Approved 2026-11-21]

Each feature has a **desired state** (enabled or disabled, from
configuration, defaults and conflicts) and an **effective state** (what is
actually running). For `RESTART_REQUIRED` features they may differ until
restart; the UI shows this as *pending restart*.

- **LC-1** At startup, after configuration and compatibility detection, each
  feature whose desired state is enabled is enabled.
- **LC-2** Toggling a `LIVE` feature calls `enable()` or `disable()` once;
  toggling to the current state is a no-op.
- **LC-3** Toggling a `RESTART_REQUIRED` feature only changes the desired
  state and marks it pending restart.
- **LC-6** Disabling a feature never deletes its settings or data (CFG-P7).
- **LC-7** Lifecycle calls happen on the client (render) thread.

### 4.2 Effective states [Approved 2026-11-21]

This revision replaces the earlier `FAILED` state, which treated a feature
as inactive even after `disable()` threw. That was unsafe.

| Effective state | Meaning | Known? |
| --- | --- | --- |
| `ACTIVE` | Last `enable()` returned normally. | Known active |
| `INACTIVE` | Never enabled, or the last `disable()` (or rollback) returned normally. | Known inactive |
| `UNKNOWN` | `disable()` threw, so the feature may be fully or partly active. | Unknown |

Each feature also keeps one optional **last failure** record: the operation
(`ENABLE`, `DISABLE`, or `ENABLE` with a failed rollback), the exception
type, and the time. The record is cleared by the next successful operation
on that feature.

Three states plus a failure record is the whole model; there are no other
transitions.

### 4.3 Transitions [Approved 2026-11-21]

| From | Operation | Result | New state | Failure record |
| --- | --- | --- | --- | --- |
| `INACTIVE` | `enable()` | returns | `ACTIVE` | cleared |
| `INACTIVE` | `enable()` | throws → Core calls `disable()` once as rollback; rollback returns | `INACTIVE` | `ENABLE` |
| `INACTIVE` | `enable()` | throws → rollback also throws | `UNKNOWN` | `ENABLE` + rollback failed |
| `ACTIVE` | `disable()` | returns | `INACTIVE` | cleared |
| `ACTIVE` | `disable()` | throws | `UNKNOWN` | `DISABLE` |
| `UNKNOWN` | `disable()` (user retry) | returns | `INACTIVE` | cleared |
| `UNKNOWN` | `disable()` (user retry) | throws | `UNKNOWN` | `DISABLE` (updated) |
| `UNKNOWN` | `enable()` | **not allowed** | `UNKNOWN` | — |

Requirements:

- **LC-4** Exceptions from `enable()`/`disable()` are caught per feature.
  Other features and the game continue.
- **LC-5** No automatic retries. The user can retry by toggling (enable from
  `INACTIVE`, or disable from `UNKNOWN`).
- **LC-8** A feature in `UNKNOWN` is never treated or reported as inactive
  or disabled. It cannot be enabled again until a cleanup (`disable()`)
  succeeds or the process restarts.
- **LC-13** Lifecycle operations run only when the player changes the
  desired state or explicitly asks for a retry. Nothing retries
  automatically: not acknowledging or closing a results screen, and not
  re-applying an unchanged desired state.
- **LC-9** A game restart starts every feature in `INACTIVE`, then applies
  LC-1 using the saved desired state.

### 4.4 Logging and UI reporting [Approved 2026-11-21]

- **LC-10** Each failure is logged once at `ERROR` with the feature id,
  operation and stack trace. A rollback attempt is logged at `WARN`. A
  successful retry is logged at `INFO`.
- **LC-11** The settings screen shows, per feature:
  - `INACTIVE` with an `ENABLE` failure: *"Failed to enable — see log."*
  - `UNKNOWN`: *"Could not be fully disabled; it may still be active.
    Restart the game to be sure."* The control offers *Retry disable*, not
    *Enable*.
- **LC-12** Messages are translated; exception details appear only in the
  log.

### 4.5 Desired and effective settings [Approved 2026-11-21]

- **ST-1** Each feature has a **desired settings snapshot** (what the player
  saved) and an **effective settings snapshot** (what the running feature
  reads).
- **ST-2** After a successful save, `LIVE` settings are copied into the
  effective snapshot immediately.
- **ST-3** `RESTART_REQUIRED` settings keep their previous effective value
  until the process restarts. The feature is shown as *pending restart*
  while the desired and effective values differ.
- **ST-4** Snapshots are immutable. A new effective snapshot replaces the
  old one as a whole.

## 5. Configuration behavior [Approved 2026-11-21]

Configuration follows [configuration.md](../architecture/configuration.md).
M0 implements only the global file:

- **CFG-1** Load once at startup, before features are enabled.
- **CFG-2** Missing, malformed, newer-version, unknown and invalid values are
  handled as in the [safe handling table](../architecture/configuration.md#safe-handling).
- **CFG-3** M0 ships `schemaVersion: 1` and a migration mechanism with a test
  migration exercised only by tests.
- **CFG-4** Saving is atomic where supported and happens when the user applies
  changes in the settings screen.
- **CFG-5** `enabled` is persisted only after an explicit user choice.
- **CFG-6** Loading then saving an unchanged file preserves all known and
  unknown entries (semantic round-trip; formatting may differ).

## 6. Compatibility detection behavior [Approved 2026-11-21]

- **CMP-1** At startup, for each feature, check whether any declared
  conflicting mod is loaded, using the Fabric Loader API.
- **CMP-2** Effective default: if a conflict is detected and the user has
  **no explicit choice**, the feature starts disabled.
- **CMP-3** An explicit user choice always wins; detection never changes a
  value the user set.
- **CMP-4** Detected conflicts are logged once at startup and shown in the
  settings screen next to the feature, with the reason.
- **CMP-5** The user can enable a conflicting feature. The UI shows a
  visible warning but does not block.
- **CMP-6** Detection only reports mods declared in descriptors; it does not
  guess or scan other mods' contents.

## 7. Settings UI

### 7.1 Requirements [Approved 2026-11-21]

- **UI-1** One screen lists all registered features grouped by category, in
  a stable order.
- **UI-2** Each feature row shows: localized name, enable toggle, and status
  indicators for *pending restart*, *conflict detected* and failure states
  (§4.4).
- **UI-3** A search field filters features by localized name, localized
  description and id, case-insensitively, in the active language.
- **UI-4** Opening a feature shows its settings with widgets for the M0
  setting types; invalid input is rejected before apply.
- **UI-5** *Done* applies and saves changes; *Cancel* discards them. Detailed
  semantics are in §7.2.
- **UI-6** Restart-required changes are indicated in the settings screen.
  There is no notice outside it.
- **UI-7** All strings use translation keys present in `en_us` and `pt_br`.
- **UI-8** The screen works with zero registered features (empty state).
- **UI-9** The screen is opened through a configurable key binding that has
  no default key. Mod Menu integration is optional and deferred.
- **UI-10** The screen is built with native Minecraft screen widgets. No
  configuration UI library is used.

### 7.2 Transaction and failure semantics [Approved 2026-11-21]

- **TX-1 Draft.** Opening the screen copies the current desired states and
  setting values into a draft. All edits change only the draft.
- **TX-2 Cancel.** *Cancel*, or closing the screen with Escape, discards the
  draft. There are no lifecycle calls and no file write.
- **TX-3 Validate.** *Done* validates the whole draft first. If anything is
  invalid, nothing is applied or saved; the screen stays open and marks the
  invalid fields.
- **TX-4 No-op.** If the draft equals the current configuration, *Done*
  closes the screen without writing the file.
- **TX-5 Save, then apply.** After validation, the Core saves the draft
  atomically **before** any runtime change. If the save fails, nothing is
  applied; the screen stays open with an error and the draft is kept. This
  way the running game is never ahead of what is on disk.
- **TX-6 Runtime apply.** After a successful save:
  - `LIVE` settings enter the effective snapshot; `RESTART_REQUIRED`
    settings do not (§4.5).
  - `LIVE` toggles are applied per §4.3, all disables before enables, each
    group in registry order.
  - `RESTART_REQUIRED` changes are marked *pending restart*.
- **TX-7 Failures do not revert the saved choice.** If a runtime transition
  fails, the saved desired state stays as the user chose it, and the
  effective state and failure indicator are shown honestly (§4.4). Failures
  are never reported as successful application. A runtime failure does
  **not** mean the save failed: the file already holds the player's choice.
  Save failures are reported separately (TX-5).
- **TX-8 Result.** If every transition succeeded, the screen closes. If any
  failed, the screen stays open with a summary listing the affected
  features, and the next *Done* or *Cancel* closes it. Acknowledging or
  closing the summary never retries a failed operation (LC-13).
- **TX-9 Read-only configuration.** If the file is read-only because its
  schema version is newer (see configuration), the screen says that changes
  cannot be saved. *Done* is disabled, so runtime and disk cannot diverge.


Test cases (unit tests against fixtures, without a game instance):

| Id | Scenario | Expected |
| --- | --- | --- |
| TX-T1 | Edit, then Cancel | No lifecycle calls; file byte-for-byte unchanged |
| TX-T2 | Done with an invalid integer | No calls, no write; draft kept |
| TX-T3 | Done with an unchanged draft | No calls, no write |
| TX-T4 | Save fails (simulated IO error) | No lifecycle calls; error reported; draft kept |
| TX-T5 | Enable a live fixture whose `enable()` throws | File has `enabled: true`; fixture `INACTIVE` with `ENABLE` failure; rollback called once; other changes applied |
| TX-T6 | Disable a live fixture whose `disable()` throws | File has `enabled: false`; fixture `UNKNOWN`; enable blocked; retry disable allowed |
| TX-T7 | Toggle a restart-required fixture | File updated; no lifecycle call; *pending restart* shown |
| TX-T8 | Configuration read-only (newer schema) | Done disabled; no write |
| TX-T9 | One failing and one succeeding live change | Success applied; failure reported; screen stays open |
| TX-T10 | Re-apply an unchanged desired state after a failure (acknowledge/close) | No lifecycle call |
| TX-T11 | Enable requested while `UNKNOWN` | Rejected; no `enable()` call |
| TX-T12 | Change a `LIVE` and a `RESTART_REQUIRED` setting together | `LIVE` value effective at once; restart-required value keeps its old effective value; *pending restart* |

## 8. Test strategy [Approved 2026-11-21]

| Layer | Scope | How |
| --- | --- | --- |
| Unit | Registry validation, setting validation, config parse/save/round-trip, migrations, malformed and newer-version files, lifecycle states and failures, conflict default rules, search matching, transaction cases TX-T1–TX-T12 | JUnit 5, without a Minecraft instance |
| Localization | Every key used by the Core and registered features exists in both language files | Automated test |
| In-game | Startup, settings screen, toggling live and restart-required features, conflict and failure display | Manual QA checklist in a development client (initially) |
| Fixtures | Test-only sample features (live, restart-required, failing enable, failing disable, conflicting) | Test sources only; never shipped |

## 9. Acceptance criteria [Approved 2026-11-21]

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
9. **AC-9** A fixture whose `enable()` throws ends `INACTIVE` with an
   `ENABLE` failure. A fixture whose `disable()` throws ends `UNKNOWN`,
   never `INACTIVE`. Other fixtures end `ACTIVE` (unit test).
10. **AC-10** A conflicting fixture with no explicit choice starts disabled;
    with an explicit `enabled: true` it starts enabled and shows a warning
    (unit test + manual).
11. **AC-11** The settings screen lists fixtures by category, filters by
    search, shows status indicators, and *Cancel* leaves `config.json`
    byte-for-byte unchanged (manual checklist).
12. **AC-12** No translation key is missing in `en_us` or `pt_br` (automated).
13. **AC-13** No required server-side component; the mod works when joining a
    vanilla server (manual).
14. **AC-14** All automated tests pass and `git diff --check` is clean.
15. **AC-15** Transaction cases TX-T1–TX-T12 pass (unit tests).

## 10. Deferred decisions

| Topic | Deferred to |
| --- | --- |
| Per-world/per-server configuration and precedence (Q-C2, Q-C3) | Later milestone, before first feature needing it |
| Mod Menu integration | Later, optional |
| In-game test automation (rest of Q-A5) | After manual QA proves insufficient |
| Remote diagnostics reporting | See [diagnostics](../architecture/diagnostics.md) |
| Navigation data format and storage | M4 |
| Shared data formats with Horizon (Q-C4) | Before M4 data formats are frozen |
| Additional setting types | When a feature needs them |
| Horizon implementation | M5 |
| License (Q-V1) and distribution (Q-V2) | Before first public release |

## 11. Open questions

Resolved 2026-11-21:

- **Q-M2** Key binding only; Mod Menu optional and deferred.
- **Q-M3** Disable by default only when the player has no explicit choice.
- **Q-M4** No master switch in M0.
- **Q-M5** Restart-required changes are shown in the settings screen only.

- **Q-M1** Lifecycle failure model, transaction semantics and acceptance
  criteria approved, with corrections (§12).
- **Q-M6** Save → Apply confirmed; failed runtime transitions keep the saved
  choice.
- **Q-M7** Immutable settings snapshots and `disable()` after partial
  `enable()` approved.

No open questions block M0.

## 12. Implementation status

Corrections applied on approval (2026-11-21): LC-8 and LC-13 (no use of
`UNKNOWN` as inactive, no automatic retries), §4.5 (desired vs effective
settings), TX-7 (runtime failure is not a save failure), and TX-T10–TX-T12.

| Area | Status |
| --- | --- |
| Descriptors, setting descriptors, registry and freezing (§3) | Implemented (M0.5) |
| Lifecycle states, transitions, rollback, retry, logging (§4.1–§4.4) | Implemented (M0.5) |
| Desired vs effective settings snapshots (§4.5) | Implemented (M0.5) |
| Configuration persistence (§5) | Implemented (M0.6); see [configuration implementation](../architecture/configuration.md#implementation-m06) |
| Startup order: registry → load → lifecycle → start (CFG-1, LC-1) | Implemented (M0.6), `CoreRuntime.start` |
| Save → Apply operation (TX-5–TX-7) | Implemented (M0.6), `CoreRuntime.saveAndApply`; no screen yet |
| Client-thread rule (LC-7) | Enforced in `CoreRuntime`: the initializer's thread (Fabric's render thread) owns the Core, and calls from other threads fail. `FeatureLifecycle` itself is not guarded; all mutation goes through `CoreRuntime` |
| Compatibility detection (§6) | Not started (descriptor field only) |
| Settings UI and transaction screen (§7) | Not started; the Core Save → Apply operation is implemented |
