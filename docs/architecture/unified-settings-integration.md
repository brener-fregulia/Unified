# Unified Settings Integration (M2.0.3 discovery)

Status labels follow [AGENTS.md](../../AGENTS.md#decision-status-labels).
The product direction in §1 was approved by the Product Owner on 2026-11-26.
Everything else is **[Proposed]** unless §17 records a decision. Nothing
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
    "duplicate mod" error path. **Verified at runtime in M2.1.0 (§18):** one
    copy loads, namely the highest version that satisfies every `depends`
    range.
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
  - Fabric loads exactly one copy: the highest bundled version that satisfies
    every installed mod's `depends` range (§18), bundled by any installed
    Unified mod. That copy registers the key, the button and the screen.
  - Each mod contributes through a custom entrypoint, `unified-settings`.
- **Classloader:** one copy, one mod id, no duplicate classes.
- **Players:** install nothing extra. A Unified mod alone still carries the
  library.
- **Version skew:** the loader picks the highest version allowed by every
  range (§18). The contract
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
| Mismatched versions | The loader picks the highest library version that every mod's range allows. Compatible within a major version; unsatisfiable ranges give Fabric's startup error naming the mods (§18). |

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
13. **US-AC-13:** with two different library versions nested, the highest
    one that every range allows (normally the newest)
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
  the loaded one cannot happen if each mod's `depends` lower bound is the
  contract version it was compiled against (§18.4).
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
- **R6 [Resolved by the M2.1.0 spike, §18]: nested-library de-duplication
  is not documented by Fabric.** It is
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

## 17. Decisions

Recorded on 2026-11-26 (M2.1.0 brief). Approval of the product decisions
does not authorize implementing the shared workspace: that waits for the
spike result (§18) and a separate implementation brief.

- **Q-US1 [Approved 2026-11-26, conditional]:** alternative A, subject to
  runtime validation (§18). This amends the monorepo rule "no shared runtime
  library" for exactly one library, `unified_settings`, once the condition
  is met.
- **Q-US2 [Approved 2026-11-26]:** default key `U`.
- **Q-US3 [Approved 2026-11-26]:** preserve a custom binding of QoL's old
  settings key through a one-time migration to the new key.
- **Q-US4 [Approved 2026-11-26]:** one "Unified Settings…" button in
  Minecraft's Options screen.
- **Q-US5 [Approved 2026-11-26]:** module order QoL, Optimizations,
  Graphics.
- **Q-US6 [Approved 2026-11-26]:** hide the module navigation layer when only
  one Unified mod is installed.
- **Q-US7 [Approved 2026-11-26]:** library name "Unified Settings (Library)",
  independently versioned.
- **Q-US8 [Approved 2026-11-26]:** Optimizations owns its own minimal
  configuration persistence, with no dependency on QoL's Core.

## 18. Runtime spike results (M2.1.0, 2026-11-26)

### 18.1 Setup

- **Location:** `spikes/unified-settings-nesting/`, a separate Gradle build
  that never touches the production modules. Run it with
  `./gradlew -p spikes/unified-settings-nesting :runner:runSpike<Scenario>`
  after `./gradlew :mods:qol:jar :mods:optimizations:jar`.
- **Versions:** the monorepo's own Minecraft 26.3, Loader 0.20.1, Loom
  1.19.4, Fabric API and Fabric Language Kotlin, read from the root
  `gradle.properties`.
- **Library:** `unified_settings` in three versions with the same classes:
  - 1.0.0: contract `describe()`;
  - 1.1.0: adds `order()` with a default, an additive change;
  - 2.0.0: `describe()` removed and `title()` required, a breaking change.

  Each version registers one unbound key mapping (the production `U` key is
  not registered) and lists providers from the `unified-settings`
  entrypoint, catching each provider's failures.
- **Parents:** stand-ins that nest one library version each, the same way
  Loom's `include` does (`META-INF/jars/` plus a `jars` entry; compared with
  a real `include` build).
  - `spike_parent_a` is compiled against contract 1.0.
  - `spike_parent_b` is compiled against 1.1 and overrides `order()`.
- **Real mods:** copies of the production Unified: QoL and Unified:
  Optimizations JARs with the library nested into them. The production
  artifacts are only read.
- **Runner** (`spike_runner`): after the client starts, it records:
  - the loaded mod list;
  - each `unified_settings` instance, its version and its parent;
  - the library's init count;
  - the providers' answers;
  - how many copies of the library class exist;
  - how many spike key mappings exist.

  It writes these to `spike-result.txt` in the scenario's disposable run
  directory and closes the game.

### 18.2 Results (actual runtime, not compilation)

Unless a row says otherwise, every run had 1 library instance, init count 1,
1 library class copy, 1 key mapping, and no Fabric warnings.

| Scenario | Nested versions | Loaded | Providers |
| --- | --- | --- | --- |
| ParentsA100B110 | A 1.0.0, B 1.1.0 | **1.1.0** (from B) | Both work (A order 100 by default, B 20) |
| ParentsA110B100 (reversed) | A 1.1.0, B 1.0.0 | **1.1.0** (from A) | Both work |
| ParentsSameVersion | A 1.1.0, B 1.1.0 | 1.1.0 | Both work |
| BreakingWithRange (both `>=1.0.0 <2.0.0`) | A 1.0.0, B 2.0.0 | **1.0.0**: 2.0.0 is excluded by both ranges | Both work; B, compiled against 1.1, is served 1.0 |
| BreakingNoRange (no `depends`) | A 1.0.0, B 2.0.0 | **2.0.0** | Both fail with `AbstractMethodError`; the library's catch isolates them and the game runs |
| BreakingConflictingRanges (A `<2.0.0`, B `>=2.0.0`) | A 1.0.0, B 2.0.0 | Nothing: **startup refused** | Fabric's "Incompatible mods found!" names both mods and their ranges |
| QolOnly | QoL 1.0.0 | 1.0.0 (from `qol_unified`) | QoL starts normally (8 features) |
| OptimizationsOnly | Optimizations 1.1.0 | 1.1.0 (from `unified_optimizations`) | Optimizations starts normally |
| QolAndOptimizations | QoL 1.0.0, Optimizations 1.1.0 | 1.1.0 (from Optimizations) | Both start normally |
| QolAndOptimizationsReversed | QoL 1.1.0, Optimizations 1.0.0 | 1.1.0 (from QoL) | Both start normally |

The parents call the library's static API, and every run reported the
loaded version. Neither real mod has a dependency on the other; the mod list
shows only what each scenario installed.

### 18.3 Actual Fabric Loader behavior

- **One copy:** nested mods with the same id are reduced to one copy. There
  was no duplicate-mod error, no duplicate classes and no second
  initialization.
- **Which version:** the copy is the **highest version that satisfies every
  installed mod's `depends` range**, not simply the highest nested version
  (BreakingWithRange).
- **Unsatisfiable ranges** stop the game at startup with a clear message.
  This is the desired failure.
- **Without a range,** the highest version loads even when it breaks older
  providers. Ranges are therefore mandatory.

### 18.4 Contract rules for the implementation [Proposed]

1. **Lower bound = the contract version compiled against.** Each mod
   declares `"unified_settings": ">=<compiled contract> <<next major>"`.
   BreakingWithRange shows why: a mod compiled against 1.1 can otherwise be
   served 1.0, and any 1.1-only call would fail with `NoSuchMethodError`.
2. **Additive changes only** within a major version. New interface methods
   need defaults, and nothing public is removed or changed. 1.0 providers
   worked unchanged on 1.1.
3. **The library isolates every provider call** (verified with
   `AbstractMethodError`) and sorts providers by the approved module order
   (Q-US5). Entrypoint order is not something to rely on.
4. **The library's initializer stays idempotent and cheap.** It ran exactly
   once in every scenario.

### 18.5 Conclusion

**Alternative A remains viable** and meets the condition of Q-US1: one
instance, one initialization, one key mapping, and both parents reach the
selected API. Incompatibilities surface either at startup (ranges) or as
isolated provider failures.

**Remaining risks:**

- The spike library is Java; the production library will be Kotlin, so its
  `depends` must include Fabric Language Kotlin, which both mods already
  require.
- Mod-list UIs such as Mod Menu will show the nested library; the name is
  approved in Q-US7.
- The key-binding migration (Q-US3) and the Options button are untested
  until implemented.
- No implementation is authorized until a separate brief.

**Recommended first implementation step:** create `:libs:settings` (Kotlin,
version 1.0.0) containing only the contract and the coordinator (key `U`,
Options button, provider discovery and ordering). Nest it in both mods with
`include` and the range rule above. Then generalize QoL's workspace into it
in a later step.

## 19. Foundation (M2.1.1, implemented 2026-11-26)

### 19.1 Library

`:libs:settings`, mod id `unified_settings`, "Unified Settings (Library)",
version **1.0.0**, Kotlin, client-only.

- **Depends on:** Loader, Minecraft 26.3, Java 25, Fabric Language Kotlin,
  and Fabric API's key-mapping, screen and lifecycle modules.
- **Never depends on:** a Unified mod.

| Package | Content |
| --- | --- |
| `unified.settings.api` | `UnifiedSettingsProvider` (contract 1.0), `SettingsCategory`, `ModuleOrder` (QoL 10, Optimizations 20, Graphics 30), `Capabilities` (reserved names), `ContractVersion` |
| `unified.settings.registry` | `ProviderRegistry` and `ModuleEntry`: discovery, ordering, failure isolation |
| `unified.settings.ui` | `UnifiedSettingsScreen` (the temporary shell) and `ButtonPlacement` |
| `unified.settings` | `UnifiedSettingsClient`: the key and the Options button |

### 19.2 Contract 1.0

```kotlin
interface UnifiedSettingsProvider {          // Fabric entrypoint "unified-settings", one per mod
    val moduleId: String                     // the mod's Fabric id
    val nameKey: String                      // translation key of the module name (owned by the mod)
    val order: Int                           // ModuleOrder
    fun categories(): List<SettingsCategory> = emptyList()
    fun openSettings(parent: Screen?): Screen?   // the mod's current screen; null = nothing to configure
    fun capabilities(): Set<String> = emptySet()
}
```

- **Java:** implementable directly (`getModuleId()` and so on). Members
  with defaults are real default methods; QoL's provider is Java and a unit
  test covers this.
- **Deliberately absent:** feature descriptors, drafts and commits. Those
  belong to M2.1.2's workspace contract and will be additions with
  defaults, so contract 1.x stays compatible.
- **Discovery:** `ProviderRegistry.modules()` reads
  `getEntrypointContainers("unified-settings", …)` once, when the screen
  first opens, and caches it.
  - **Order:** `order`, then mod id.
  - **Failures:** a provider that throws while being created or read is
    logged with its mod id and listed as failed; the others work.

### 19.3 Packaging (exact declarations)

Both mods:

- `implementation(project(":libs:settings"))`, to compile against it.
- `include(project(":libs:settings"))`, which nests
  `META-INF/jars/unified-settings-1.0.0.jar` with a `jars` entry. No
  library class is copied into the mod's own JAR.
- `"unified_settings": ">=1.0.0 <2.0.0"` in `depends`. The build computes
  it from the library version compiled against: `>=<that> <<next
  major>.0.0`.
- `"unified-settings": [<provider class>]` in `entrypoints`.
- In development, Loom registers the library as its own mod
  (`sourceSet("main", ":libs:settings")`).

Unified: Optimizations therefore now needs Fabric API at runtime (the
library's key-mapping, screen and lifecycle modules). It also compiles with
Fabric API for development runs.

### 19.4 Entry points

- **Key:** `key.unified_settings.open`, "Open Unified Settings", default
  physical **`U`**, in the "Unified" category. Registered only by the
  library, so it exists once. It opens the shell when no screen is open.
- **Legacy key (temporary, removed in M2.1.2, §20.6):** Unified: QoL still registers
  `key.qol_unified.open_settings` (unbound by default), unchanged. With QoL
  installed there are therefore **two settings keys** in this stage: the
  shared one and QoL's legacy one. Duplicates are not eliminated yet; M2.1.2
  migrates a custom binding once (Q-US3) and removes the legacy key.
- **Options button:** "Unified Settings…" ("Configurações do Unified…"),
  added in `ScreenEvents.AFTER_INIT` of `OptionsScreen`.
  - `ButtonPlacement` takes the first spot that overlaps no widget: beside
    the Done button in the footer (left, then right), otherwise the header
    corners. If nothing fits, no button is added.
  - After a resize the old button is found by its translation key and
    replaced, so there is never a second one.
  - It is a vanilla button, so keyboard focus and narration work. It opens
    the shell with the Options screen as parent.

### 19.5 Temporary shell

`UnifiedSettingsScreen` is the integration shell, not the final workspace.

- **What it shows:** the title, one button per installed module in order,
  and Back. Escape and Back return to the parent.
- **QoL:** its button opens the existing workspace with the shell as
  parent. Its Back returns to the shell.
- **Optimizations:** shows "Unified: Optimizations has no configurable
  features yet."
- **Deferred:** Q-US6 (hiding the module layer with a single mod) applies
  to M2.1.2's workspace; the shell always lists the modules.
- **Configuration:** untouched by the library. QoL keeps
  `config/qol_unified/config.json`, its descriptors and auto-apply.
  Optimizations keeps its own directory and still has no settings file.

### 19.6 Validation

- **Unit tests (library):** 9.
  - Ordering by module order, never load order.
  - Equal-order tie-break.
  - Failure isolation for creation and identity reads.
  - One entry per module.
  - Defaults usable from Java.
  - Button placement at four window/scale combinations, the narrow-footer
    fallback, and "nothing rather than an overlap".
- **Runtime:** `:tests:settings-runtime` loads the **real production
  JARs** through `fabric.addMods`; the library is only on its compile
  classpath. The three scenarios QolOnly, OptimizationsOnly and Both all
  passed. Each checks:
  - exactly one `unified_settings`, version 1.0.0, nested in an installed
    mod;
  - exactly one `key.unified_settings.open` with default `U` and no other
    default-`U` binding;
  - the legacy QoL key only with QoL;
  - exactly one Options button, overlapping nothing, at GUI scales 1–2 at
    854×480 and 1–4 at 1920×1080, rebuilt on every scale change (a resize);
  - module order;
  - QoL opens its workspace and Back returns to the shell and then to
    Options;
  - Optimizations shows its "no configurable features yet" message;
  - `U` in a singleplayer world opens the shell and Escape returns to the
    game.
- **Logs:** no Mixin, class-loading or resolution errors.
- **QoL regressions run:** its Settings and Keyboard Map game tests pass
  with the library loaded in development.

### 19.7 Remaining for M2.1.2

- One merged workspace: module groups in the sidebar (hidden with a single
  module, Q-US6), QoL's panels hosted through an extended contract (feature
  descriptors, session, commit results as additions with defaults), and
  search across modules.
- Migrate a custom binding of `key.qol_unified.open_settings` to
  `key.unified_settings.open` once, then remove the legacy key (Q-US3).
- Sound Control's preview hook (R2) and Keyboard Map/Sound Control action
  screens through the contract.
- Optimizations' own minimal persistence once it has a setting (Q-US8).

## 20. Integrated workspace (M2.1.2, implemented 2026-11-26)

The PO rejected the M2.1.1 module-selection screen after manual testing. `U`
and Options → Unified Settings… now open one workspace directly.

### 20.1 UI ownership

| Owner | Responsibility |
| --- | --- |
| Library (`unified.settings.ui`) | `UnifiedSettingsScreen`: title, search, sidebar (`NavList`, `WorkspaceNavigation`), compact drawer below 400 GUI px, layout breakpoints (120/150/170 px sidebar, 400 px panel, centered on wide screens), footer message line, Back, Retry/Undo after a failed save, provider-failure isolation; the `U` key (in game and on the title screen) and the Options button. |
| Module (`SettingsWorkspace`) | Feature list and categories, the feature panel (switch, status, description, comparison, tools, setting rows), values, validation, persistence and applying. |

The frame is QoL's approved workspace code moved into the library (layout
constants, sidebar rendering, narration, search normalization). QoL's panel
code (`FeaturePanel`, `PanelList`, `FeatureSwitch`, `SettingRows`) stays in
QoL, unchanged, because it is bound to QoL's descriptors and draft. Moving
those controls into the library would need a descriptor/session contract;
that is left **[Open]** (Q-US9) until a second module has settings.

### 20.2 Contract 1.1 (library 1.1.0)

Additive, every new member has a default; 1.0 providers keep working.

- `UnifiedSettingsProvider.workspace(): SettingsWorkspace?` (default null):
  one session per opened workspace screen.
- `SettingsWorkspace`: `features()`, `attach(WorkspaceHost)`, `opened()`
  (every rebuild, e.g. back from a tool screen), `panel(featureId,
  PanelArea)`, `beforeRebuild()`, `flagged()`, `saveFailed()`,
  `retrySave()`, `undoChange()`, `closed()`.
- `WorkspaceHost`: footer message, rebuild requests.
- `PanelArea`: bounds, font, parent screen for tool screens, the setting a
  search result should reveal, `add(widget)`.
- `WorkspaceFeature` / `WorkspaceSetting`: ids, translation keys and
  translated texts for search.
- `openSettings` is no longer called for a module that offers a workspace.

The library never reads or writes module configuration. Every provider call
is wrapped: a throw is logged with the module id; that module shows "Could not
be loaded" (or a panel error) and the others keep working.

### 20.3 Navigation

- Both mods: `Unified: QoL` and `Unified: Optimizations` top-level groups,
  expandable (click, Enter, Left/Right). Optimizations shows a disabled
  "No configurable features yet" row; no placeholder features.
- One mod: its group row is omitted; categories are shown directly, exactly
  as before (Q-US6).
- Optimizations only: the workspace says "Unified: Optimizations has no
  configurable features yet." No QoL class is loaded.
- Search spans all modules; results are listed under their owning module
  group (module rows only when more than one module is installed); modules
  without matches are hidden. Feature ids are qualified by module.

### 20.4 QoL adapter

`QolWorkspace` holds a `SettingsDraft` per session and commits each valid
change through QoL's Configuration Service (validate, save, apply), exactly
as the former `SettingsScreen`. Pending invalid number text, sidebar flags,
save failure with Retry/Undo, the read-only message, Retry disable and the
restart message are unchanged. `QolWorkspace.open(parent, featureId)` is the
contextual entry point. Sound Control's preview follows the draft of the
shown screen through `QolWorkspace.draftShown`, so Live Preview and rollback
end when the workspace closes. Keyboard Map and Sound Control open over the
workspace and return to the same selected feature and scroll position.

Removed from QoL: `SettingsScreen`, `NavList`, `SettingsNavigation` (moved
to the library) and their now-unused translation keys.

### 20.5 Configuration

Unchanged: `config/qol_unified/config.json`, its schema and auto-apply. No
global config and no migration.

### 20.6 Key binding migration

QoL no longer registers `key.qol_unified.open_settings`; only
`key.unified_settings.open` (default `U`) remains. `LegacyKeyMigration`
reads options.txt (read-only) during QoL's client initialization, before
anything saves options. If the legacy line names a key and the shared key is
still at its default, at client start it calls `KeyMapping.setKey`,
`KeyMapping.resetMapping()` and `Options.save()`. Minecraft's save drops the
unregistered legacy line, so the migration runs once. A customized shared key
is never overwritten. Errors leave the bindings as they were. Limitation: if
the player had bound the legacy key while the shared key was customized, the
legacy binding is dropped (logged).

### 20.7 Validation

See the M2.1.2 report: library, QoL and Optimizations unit tests; every QoL
game-test scenario; the three runtime scenarios with the production JARs
(including migration from a seeded options.txt and search across modules);
screenshots of combined, single-mod, narrow and ultrawide layouts.
