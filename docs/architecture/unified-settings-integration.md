# Unified Settings Integration (M2.0.3 discovery)

Status labels follow [AGENTS.md](../../AGENTS.md#decision-status-labels).
The product direction in §1 was approved by the Product Owner on 2026-11-26.
Everything else is **[Proposed]** or **[Open]**; §17 lists the decisions. Nothing
here is implemented.

## 1. Product requirements [Approved 2026-11-26]

- One shared settings screen for the installed Unified mods.
- Each mod stays independently installable. The screen shows only installed
  mods and only available features; nothing appears as a placeholder.
- Unified: QoL's settings work without Optimizations, and Optimizations'
  settings work without QoL. Graphics joins later.
- Each mod owns its configuration, validation and persistence. No mandatory
  dependency between QoL and Optimizations.
- Changes apply immediately (auto-apply) where supported.
- Hierarchy: Unified Settings → mod → category → feature → settings. For
  example, Unified: Optimizations → Network → Fast IP Ping.
- Entry points: one configurable key (proposed default `U`), one button in
  Minecraft's Options screen, the same screen from any installed mod. No
  duplicate keys, buttons or competing screens. Existing QoL settings and key
  preferences are not lost.

## 2. Current state (verified in the repository, 2026-11-26)

- **QoL Core:** `CoreRuntime`, `FeatureRegistry`, `FeatureLifecycle`, and
  `ConfigurationService`, which writes `config/qol_unified/config.json`
  atomically with backups.
- **Descriptors:** `FeatureDescriptor` (id, category, default, toggle mode,
  settings, conflicts, `SettingsRule`) and `SettingDescriptor` (Boolean,
  Integer, Enum, Map).
- **The workspace:** `SettingsScreen` with `SettingsNavigation`, `NavList`,
  `PanelList`, `FeaturePanel`, `SettingRows` and `FeatureSwitch`. It works
  through one `SettingsDraft`, committed after every valid change
  ([settings-ux-v1.md §18](../qol/design/settings-ux-v1.md)).
- **Feature hooks into the workspace:**
  - `FeatureComparisons` (Food HUD, Shulker, Target Info samples);
  - `FeatureActions` (Keyboard Map and Sound Control tool screens);
  - `DraftScreen`, which Sound Control's preview follows.
- **Entry points:**
  - One QoL key mapping, `key.qol_unified.open_settings`, unbound by default,
    in the "Unified: QoL" category; it opens the workspace in game or on the
    title screen.
  - No Options-screen button.
  - Sound Control adds its own button to Music & Sound.
- **Optimizations:** no Core, settings or persistence yet. It owns
  `config/unified_optimizations/`.
- **Fabric facts:**
  - All mods share one class loader (Knot). Two different mod JARs containing
    the same class name collide, and which copy loads is undefined.
  - Nested ("jar-in-jar") mods with the same mod id, carried by several
    mods: Fabric's documentation does not describe how they are resolved.
    Fabric Loader 0.20.1's resolver (`ModResolver`, `ModPrioSorter`)
    selects one candidate per mod id and orders candidates by version, which
    indicates that the highest nested version loads. It also contains a
    "duplicate mod" error path. **Not yet verified at runtime;** see R6.
  - Key mappings must be registered during client initialization.
  - Custom entrypoints (`FabricLoader.getEntrypointContainers(name, type)`)
    list the mods that declare one, without classpath scanning.
- **Key `U`:** no vanilla 26.3 default binding (scancode 24, `KEY_U`, appears
  in none of the `Options` defaults).

## 3. Alternatives

### A. Shared settings library, nested in each mod [Proposed: recommended]

- **What it is:** a small library mod, `unified_settings` (Kotlin, module
  `:libs:settings`, its own version). It holds:
  - the settings contract;
  - the workspace screen (generalized from QoL's);
  - the one key mapping;
  - the Options button.
- **Packaging:** every Unified mod **includes it as a nested JAR**
  (`include(project(":libs:settings"))`). That one copy loads depends on the
  loader's de-duplication of nested mods (§2, R6).
- **At runtime:**
  - Fabric loads exactly one copy, the newest bundled by any installed
    Unified mod. That copy registers the key, the button and the screen.
  - Each mod contributes through a custom entrypoint, `unified-settings`.
- **Classloader:** one copy, one mod id, no duplicate classes.
- **Players:** install nothing extra. A Unified mod alone still carries the
  library.
- **Version skew:** the loader picks the highest version. The contract
  changes only additively within a major version, and each mod declares
  `"unified_settings": ">=x.y <(x+1)"` in `depends`. An incompatible
  combination then fails at startup with Fabric's clear message instead of at
  runtime.
- **Ownership:** unambiguous. The single loaded library owns the key, button
  and screen; the mods own their data.
- **Cost:** it changes the monorepo rule "no shared runtime library" (Q-US1).
  QoL's workspace code moves into the library behind a provider contract.

### B. Optional cross-mod integration with per-mod fallback UIs [Proposed: not recommended]

- **What it is:** each mod keeps its own screen, key and button. When several
  are installed, one becomes the coordinator, for example the first
  alphabetically, or the one with a marker entrypoint. It hides the others'
  keys and buttons and shows a combined view.
- **Problems:**
  - The combined view needs shared types. Without a shared library they must
    be duplicated (class collisions) or reached reflectively (fragile,
    against project rules).
  - Every mod ships a full settings UI: duplicated code, plus drift between
    mods released at different times.
  - Hiding another mod's already-registered key mapping is not possible
    cleanly: key mappings are registered at startup and persist in
    `options.txt`. That leaves two keys.
  - Election order and version skew between two coordinators are
    hard-to-test edge cases.

### C. Dedicated, separately installed settings mod [Proposed: not recommended]

- **What it is:** a standalone "Unified Settings" mod that the player installs.
- **If required:** every mod depends on it, which is an extra install. A mod
  installed without it fails to start.
- **If optional:** each mod still needs its own fallback UI, which brings back
  B's duplication and two-key problem.

It is effectively A without the nesting, with a worse installation
experience.

### Comparison

| Criterion | A (nested library) | B (fallback + coordinator) | C (separate mod) |
| --- | --- | --- | --- |
| Runtime dependencies for players | None extra (nested) | None | One extra mod |
| Duplicate code | None | Full UI in every mod | None (if required) |
| Duplicate keys/buttons | Impossible (one copy) | Hard to avoid | None (if required) |
| Independent distribution | Yes | Yes | Weakened |
| Failure isolation | Per provider (try/catch at the contract) | Per mod | Per provider |
| Upgrading one mod alone | Yes; the newest library wins and stays compatible | Coordination drift | Needs the separate mod updated too |
| Graphics later | Add an entrypoint | Third coordinator candidate | Add an entrypoint |
| Classloader risk | None (one mod id) | High if types are shared | None |
| Complexity | One small library and one contract | Election plus duplicated UIs | Like A, plus an install step |

## 4. Recommendation [Proposed]

**A, the shared library nested in each mod.** It is the only option that gives
one key, one button and one screen without an extra install or duplicated
UIs, and it relies on a standard Fabric mechanism (nested JARs and
de-duplication by mod id). It adds no framework: one interface, one
entrypoint name and one screen.

## 5. Dependency diagram [Proposed]

```text
          build time (Gradle)                           runtime (Fabric Loader)
 :mods:qol ─────────┐                          unified-qol.jar ──┐ nested unified_settings 1.2
                    ├─ compileOnly + include ─►                  ├──► one unified_settings (1.2) loaded
 :mods:optimizations┘   :libs:settings         unified-optimizations.jar ┘ nested unified_settings 1.1
                                                       │
 QoL never depends on Optimizations;                    unified_settings ──entrypoint "unified-settings"──►
 Optimizations never depends on QoL.                      QoL provider, Optimizations provider (each owns its data)
```

## 6. Registration and ownership contract [Proposed]

Kotlin interfaces in `unified.settings.api` (Java-callable), roughly:

```kotlin
interface SettingsProvider {           // a mod's entrypoint "unified-settings"
    val module: ModuleInfo             // id, translated name, order (QoL 10, Optimizations 20, Graphics 30)
    fun categories(): List<CategoryInfo>
    fun features(): List<FeatureEntry> // descriptors: id, category, name/description keys, switch, settings
    fun session(): SettingsSession     // a fresh draft over the mod's own configuration
}

interface SettingsSession {            // owned and implemented by the mod
    fun value(feature: String, key: String): Any?
    fun set(feature: String, key: String, value: Any?): Validation  // field-level errors, incl. cross-field rules
    fun commit(): CommitResult         // validate → save → apply, entirely inside the mod
    fun revert()
    fun status(feature: String): FeatureStatusInfo
}
```

- **Setting types:** the contract's types mirror QoL's `SettingDescriptor`
  kinds (Boolean, Integer with range, Enum, Map summary) and `SettingsRule`
  violations.
- **QoL:** QoL adapts its Java descriptors and `SettingsDraft` to the
  contract. Its Core is unchanged.
- **Optimizations:** implements a small store of its own when its first
  setting is approved (Q-US8).
- **Discovery:** once, on the first opening of the screen, through
  `getEntrypointContainers("unified-settings", SettingsProvider::class.java)`.
  Cached for the session; no scanning and no repeated reflection.
- **Failure isolation:** every provider call is wrapped. A provider that
  throws shows its module with an error line, it is logged with the mod id,
  and the other modules keep working.
- **Extensions:** comparisons and tool actions (Keyboard Map, Sound Control)
  become optional provider capabilities, so QoL keeps them.
- **Live preview:** Sound Control's preview, which follows the shown draft,
  needs a "draft shown / hidden" callback on the session (risk R2).

## 7. Keybinding coordination [Proposed]

- **One key:** the loaded library registers `key.unified_settings.open`,
  "Open Unified Settings", in a "Unified" category, default **`U`**
  (Q-US2). The mods register no settings key of their own.
- **Moving QoL's old key** (`key.qol_unified.open_settings`, unbound by
  default):
  - On the first start with the library, if the player had bound the old
    key, the library assigns that binding to the new key once.
  - The library reads the old value from `options.txt` through vanilla's
    loaded options before it is lost (Q-US3).
  - Players who never bound it simply get `U`.
- **Keyboard Map** lists the new key automatically, as with every key
  mapping.

## 8. Options button [Proposed]

- **Placement:** the loaded library adds one "Unified Settings…" button to
  Minecraft's Options screen through `ScreenEvents.AFTER_INIT`. It goes in
  the grid area that vanilla leaves free, laid out from the screen's own
  widgets, never fixed coordinates (Q-US4).
- **No duplicates:** only one library copy exists.
- **Unchanged:** Sound Control's Music & Sound button.

## 9. Installation scenarios [Proposed]

| Installed | Behavior |
| --- | --- |
| QoL only | The library nested in QoL loads. Key, button and screen show "Unified: QoL" only. Everything QoL has today. |
| Optimizations only | The library nested in Optimizations loads. Its features only, once it has settings. Until then: the module's description and "no settings yet", no placeholder features. |
| Both | One library (the newer copy). Both modules, ordered QoL, Optimizations. One key, one button. |
| Neither | Nothing (the library exists only inside a Unified mod). |
| Graphics later | It adds the nested library and one provider. |
| Mismatched versions | The loader picks the highest library. Compatible within a major version; an incompatible major version gives Fabric's startup error naming the mods. |

## 10. Configuration ownership [Proposed]

- **Data stays with each mod.** The library never reads or writes a
  configuration file. Each `SettingsSession` talks to its own mod's storage:
  - QoL: `ConfigurationService`, `config/qol_unified/config.json`;
  - Optimizations: `config/unified_optimizations/config.json`, when it exists.
- **No shared file and no migration.** QoL's schema is unchanged.
- **Commits are per module.** A save failure is shown in that module's panel
  with Retry and Undo, and never blocks or reverts another module.
  Validation messages stay at the field.
- **Removing a mod** removes its provider. The others are unaffected and
  nothing of the removed mod is touched.

## 11. UI design [Proposed]

- **Baseline:** QoL's approved workspace, unchanged in look: the same
  sidebar, panel, switch, rows, comparisons, auto-apply and single Back.
- **Sidebar:** module groups above categories, for example "▼ Unified: QoL"
  then its categories, collapsible like categories today. With one module
  installed, the module row may be omitted (Q-US6).
- **Panel header:** shows the owning module next to the feature name.
- **Search:** covers every installed module; results show their module.
- **Keep:** keyboard navigation, narration and the responsive drawer.

## 12. Lifecycle and performance [Proposed]

- **Startup:** the library initializer registers the key mapping and the
  Options hook (cheap). Providers are only read when the screen first opens.
- **Data:** settings are read from each mod's already-loaded configuration.
  No background threads, no per-frame work, no scanning.
- **Disable vs removal:** disabling a feature is per mod, as today. Removing a
  mod means its provider simply does not exist.
- **Shutdown:** nothing to do. Each mod has already persisted its own
  commits.

## 13. Acceptance criteria [Proposed]

1. **US-AC-1:** QoL alone: one key (default `U`), one Options button, and the
   workspace with all QoL features as today.
2. **US-AC-2:** Optimizations alone: the same key and button. The screen
   shows only Unified: Optimizations, with no QoL code loaded.
3. **US-AC-3:** both installed: exactly one key mapping and one Options
   button, and one library copy in the loader's mod list.
4. **US-AC-4:** the hierarchy is module → category → feature, and only
   available features appear.
5. **US-AC-5:** search finds features and settings of both modules and shows
   their owner.
6. **US-AC-6:** each module's settings persist only to its own file.
   Restarting keeps both.
7. **US-AC-7:** auto-apply per module, as in QoL today.
8. **US-AC-8:** a simulated save failure in one module shows Retry and Undo
   there and leaves the other module's changes and file untouched.
9. **US-AC-9:** a provider that throws is isolated with an error line, and
   the rest works.
10. **US-AC-10:** an old QoL custom binding carries over to the new key once,
    and players without one get `U`.
11. **US-AC-11:** Keyboard Map lists the key, and Sound Control's specialized
    screen and preview behave as today.
12. **US-AC-12:** removing one mod leaves the other's settings and file
    intact.
13. **US-AC-13:** with two different library versions nested, the newest
    loads and both providers work; an incompatible major version is refused
    at startup with a clear message.

## 14. Testing plan [Proposed]

- **Unit tests (library):**
  - module ordering and the navigation tree;
  - search across modules;
  - failure isolation with a throwing fake provider;
  - commit isolation between two fake sessions;
  - the key carry-over logic on recorded `options.txt` values.
- **Game tests:** a library run configuration with the QoL and Optimizations
  modules selectable, covering three scenarios: QoL only, Optimizations
  only, both.
  - Each asserts the key count, the Options button count, the tree, search,
    persistence and save-failure isolation.
  - Version skew: an older library JAR added through `fabric.addMods`.
- **Regression:** the existing QoL settings, Keyboard Map and Sound Control
  scenarios, then the full QoL suite once when the integration lands.
- **Targeted testing policy:** focused runs during development, the full
  run at milestone closure.

## 15. Risks [Proposed]

- **R1: contract drift.** A provider compiled against a newer library than
  the loaded one cannot happen, because the loader picks the highest version.
  A careless breaking change inside a major version would break older mods.
  Mitigation: additive-only rule and a compatibility test with the previous
  library JAR.
- **R2: Sound Control's preview** depends on QoL's `DraftScreen` and
  `SettingsDraft`. The contract needs a session-visible hook, or the
  specialized screen keeps its own path. To be resolved in the
  implementation spec.
- **R3: the move of QoL's UI code** into the library is the largest change.
  Visual regressions are covered by the existing settings screenshots.
- **R4: carrying over a key binding** touches the player's options. It must
  happen once, only when the old key was customized, and through vanilla's
  options API.
- **R6: nested-library de-duplication is not documented by Fabric.** It is
  inferred from Loader 0.20.1's resolver code (§2). Before any other M2.1
  work, a spike builds two mods nesting different `unified_settings`
  versions and confirms that exactly one, the highest, loads (US-AC-13). If
  it does not, the recommendation is revisited before any code moves.
- **R5: `U` may clash with other mods.** Keyboard Map shows conflicts, and
  the key stays rebindable.

## 16. Next milestone [Proposed]

**M2.1, Unified Settings library:**

0. Spike for R6: confirm nested-library de-duplication with two test mods.
1. Create `:libs:settings` (Kotlin), with the contract, the screen
   generalized from QoL's, the key and the Options button.
2. Nest it in both mods and make QoL a provider.
3. Give Optimizations a provider that shows the module.

Optimizations' own settings follow with its first approved feature (Fast IP
Ping, after its open decisions).

## 17. Open decisions for Product Owner approval

- **Q-US1 [Open]:** adopt alternative A. This amends the monorepo rule "no
  shared runtime library" to allow exactly one library, `unified_settings`,
  nested in each mod, contract-only plus the shared screen.
- **Q-US2 [Proposed]:** default key `U` (no vanilla 26.3 binding).
- **Q-US3 [Open]:** carry an existing custom binding of QoL's old key over to
  the new key once (recommended), or start everyone on `U`.
- **Q-US4 [Open]:** Options button label and placement ("Unified
  Settings…", in the free grid slot of the Options screen).
- **Q-US5 [Proposed]:** fixed module order: QoL, Optimizations, Graphics.
- **Q-US6 [Open]:** with a single module installed, show its module row or go
  straight to its categories.
- **Q-US7 [Proposed]:** the library's own name in mod lists, "Unified
  Settings (library)", with its own version and tag namespace
  (`settings/v…`).
- **Q-US8 [Open]:** Optimizations' minimal storage and feature list (it has
  no Core), decided with Fast IP Ping's Q-FIP7.
