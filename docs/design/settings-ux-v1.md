# Settings UX V1 — Unified Settings Workspace (M1.7)

Status labels follow [AGENTS.md](../../AGENTS.md#decision-status-labels).
Concept A was approved by the Product Owner on 2026-11-25 (§0); the rest of
the document is the approved design, with the decisions below.

## 0. Approval [Approved 2026-11-25]

- Q-SU1: Concept A, expandable sidebar.
- Q-SU2: green ON / red OFF `FeatureSwitch` with knob, check/cross and text.
- Q-SU3: settings of OFF features stay editable, slightly dimmed, with an
  "Applies when ON" hint; text contrast stays readable.
- Q-SU4: original rendered comparisons for Food HUD and Shulker Preview in
  M1.7; Target Info and Chat Heads comparisons deferred.
- Q-SU5: empty categories hidden.
- Q-SU6: tool actions visible but disabled until the feature is ACTIVE, with
  an explanation that activation must be confirmed first.
- Q-SU7: action buttons renamed "Open Keyboard Map" and "Open Sound Control",
  localized.
- Q-SU8: contextual entry points preselect their feature.
- Q-SU9: `FeatureSettingsScreen` retired once all callers are migrated.
- SU-AC-1 to SU-AC-12 approved with these decisions.

Direction from the Product Owner, 2026-11-25 **[Approved]**: one settings
workspace with category navigation, expandable feature submenus and a
contextual settings panel, so a feature is configured without opening another
settings screen; native vanilla widgets; it must scale to at least 30
features. The PO's initial preference is Concept A.

## 1. Current UI problems

Observed in the code at `eae539b` (`ui/screen/SettingsScreen.java`,
`FeatureSettingsScreen.java`, `RowList.java`):

- **Two screens per edit.** Enabling is on the list row; every other setting
  is behind a per-row "Settings" button that opens `FeatureSettingsScreen`, then
  "Back". Six features already mean many round trips.
- **The list is wide, the content thin.** Each row repeats name, status,
  description and two buttons; the description is the only explanation of what
  the feature does, and nothing shows its effect.
- **Textual Enabled/Disabled cycle buttons** read the same as option values
  ("On"/"Off" of settings), so the master switch does not stand out.
- **Specialized tools are hidden one level deeper.** "Keyboard Map..." and "Open
  sound list" live inside the feature's settings screen, and only while the
  feature is already active (§3.4).
- **Search finds features only** (name, description, id), not settings: there
  is no way to find "tooltip" or "mining progress".
- **Category buttons shrink** to fit the height (down to 14 px) instead of
  scrolling, which will not hold for more categories.

## 2. Design principles

- P-1 One screen, one draft: everything is edited in place; specialized tools
  are the only sub-screens.
- P-2 The descriptors drive the controls; the screen holds no feature logic.
- P-3 Navigation never changes configuration; only Done saves.
- P-4 Vanilla look: buttons, cycle buttons, edit boxes, scroll lists, vanilla
  sprites and text colors; one new control (the switch, §6).
- P-5 State is never shown by color alone.
- P-6 It scales by scrolling and collapsing, never by shrinking controls
  below 20 px rows.

## 3. Existing settings architecture (findings)

### 3.1 Contracts to keep

| Piece | Contract | Source |
| --- | --- | --- |
| `SettingsDraft` | One draft per settings session; `setEnabled`, `setValue`, `setIntegerText` change the draft only; `commit()` validates, saves, then applies via `CoreRuntime.saveAndApply`; invalid input blocks Done; `editable()` false when read-only; `canToggle` false for UNKNOWN features | M0 TX-1–TX-9 |
| `SettingsScreen` | Done commits; stays open once to announce restart-required changes; Cancel/Escape discard by closing; read-only message; retry-disable for UNKNOWN features | M0 UI-1–UI-10 |
| `FeatureSettingsScreen` | Renders `BooleanSetting` (on/off cycle), `EnumSetting` (cycle of translated values), `IntegerSetting` (edit box with range errors), `MapSetting` (entry-count summary only) | M0, sound-control-v1 §8.2 |
| `DraftScreen` | Any screen editing a draft implements it; Sound Control's live preview lasts exactly while a `DraftScreen` of that draft is shown | sound-control-v1 §16 |
| `FeatureActions` | Internal registry of feature tools (`labelKey`, `open(parent, draft)`): Keyboard Map, Sound Control | keymap-v1, sound-control-v1 |
| `FeatureSearch` | Categories that have features, in enum order; blank query = selected category; non-blank = all categories with headers; matches name, description, id | M0 UI-1, UI-3 |
| `Texts`, `FeatureStatus` | Status line (active, inactive, conflict default, restart pending, unknown) and conflict reasons | M0 |

### 3.2 Setting types in use

| Feature | Settings |
| --- | --- |
| Food HUD | 6 booleans, 1 enum (`tooltip`) |
| Chat Heads | 1 enum (`position`) |
| Keyboard Map | 2 enums (`layout`, `default_filter`), 2 booleans |
| Target Info | 8 booleans |
| Sound Control | 1 map (`rules`, of integer volume and boolean muted) |
| Shulker Preview | 3 booleans, 1 enum (`slot_icon_source`) |

No top-level `IntegerSetting` exists yet; the renderer must still support it.

### 3.3 Categories

`FeatureCategory` has eight values (HUD & Information, Inventory, Visuals,
Controls, Chat & Social, Performance & Network, Maps & Navigation, Audio).
Today only categories with at least one feature are listed; Visuals,
Performance & Network and Maps & Navigation are empty. See Q-SU5.

### 3.4 Contradictions found

- **Actions need a committed state.** `FeatureSettingsScreen` shows a
  feature's action only while the feature is ACTIVE in the lifecycle, not when
  the draft enables it. In the workspace, a player who turns Keyboard Map ON
  sees no "Open Keyboard Map" until Done. Recorded here; Q-SU6 proposes the
  rule for V1 rather than changing the approved specs.
- **The brief's "Save before Apply"** matches the code: `CoreRuntime` saves the
  file first and applies only if saving succeeded (TX-5). No conflict.

## 4. Layout concepts

Wireframes at 854×480, GUI scale 2 (427×240 GUI px). `▶`/`▼` collapsed and
expanded; `»` selected; `[ ON ]` the switch of §6.

### 4.1 Concept A — Expandable sidebar

```
┌───────────────────────────────────────────────────────────────────────┐
│ QoL: Unified Settings          [ Search features and settings…     ] │
├────────────────────┬──────────────────────────────────────────────────┤
│ ▼ HUD & Information│ Target Info                        [■ ON ✔ ]    │
│   » Target Info    │ HUD & Information · Active                       │
│     Food HUD       │ Shows what you are looking at: name, source...   │
│ ▶ Inventory     1  │ ┌ Vanilla ──────────┐ ┌ QoL ON ───────────────┐ │
│ ▶ Controls      1  │ │    (crosshair)    │ │ [▣] Stone      ⛏ ✘    │ │
│ ▶ Chat & Social 1  │ └───────────────────┘ └───────────────────────┘ │
│ ▶ Audio         1  │ Blocks                                   [ On ] │
│                    │ Entities                                 [ On ] │
│                    │ Tool recommendations                     [ On ] │
│                    │ Mining progress                          [ On ] │
│                    │ ...                                  (scrolls)  │
├────────────────────┴──────────────────────────────────────────────────┤
│ status / messages                          [   Done   ] [  Cancel  ] │
└───────────────────────────────────────────────────────────────────────┘
```

- One scrollable column: category rows with a feature count, and the
  features of expanded categories indented below them.
- Clicking a category expands it and selects its first feature; clicking a
  feature selects it. Several categories may stay expanded.
- Clicks to any setting: 1 (feature already visible) or 2 (expand, then
  feature; or search, then result).

### 4.2 Concept B — Three-column workspace

```
┌───────────────────────────────────────────────────────────────────────┐
│ QoL: Unified Settings          [ Search features and settings…     ] │
├──────────────┬───────────────┬────────────────────────────────────────┤
│ » HUD & Info │ » Target Info │ Target Info               [■ ON ✔ ]   │
│   Inventory  │   Food HUD    │ Shows what you are looking at...       │
│   Controls   │               │ Blocks                         [ On ]  │
│   Chat       │               │ Entities                       [ On ]  │
│   Audio      │               │ Tool recommendations           [ On ]  │
│              │               │ ...                        (scrolls)   │
├──────────────┴───────────────┴────────────────────────────────────────┤
│ status / messages                          [   Done   ] [  Cancel  ] │
└───────────────────────────────────────────────────────────────────────┘
```

- Categories, then the selected category's features, then the panel.
- Clicks to any setting: 1–2 (category, feature).

### 4.3 Comparison

| Criterion | A — Expandable sidebar | B — Three columns |
| --- | --- | --- |
| Clicks to a setting | 1–2 | 1–2 |
| Width left for the panel at 427 GUI px | ≈ 290 px (sidebar ≈ 120) | ≈ 190 px (two columns ≈ 220) |
| Small windows (scale 3–4, ≤ 320 px) | Sidebar collapses to a drawer (§10) | Needs two columns collapsed; awkward |
| 30 features in 8 categories | One scrolling tree; expanded categories show context | Short feature columns; categories fixed; scales well |
| Search | Results replace the tree with matching features and settings | Results fill the middle column; categories become noise |
| Keyboard | One list: Up/Down, Right expands, Left collapses, Enter selects | Three lists: Tab between columns; more focus stops |
| Implementation cost | One list with two row kinds | Two lists and their sync |
| Matches vanilla | Like the Controls screen (headers + rows) | No vanilla precedent |

**Recommendation: Concept A** (agrees with the PO's preference). It keeps the
most width for settings at the target 854×480 scale 2, has one focus list,
and collapses cleanly on small windows. B only wins when categories are many
and windows wide; with eight categories that is not the case.

## 5. Contextual settings panel

Top to bottom, all in one scrollable panel (vanilla `ContainerObjectSelectionList`
pattern already used by `RowList`):

1. **Header:** feature name (white), the switch (§6) right-aligned, and a
   status line (category · Active / Inactive / Disabled by default because of
   X / Restart required / Needs attention), reusing `Texts.status`.
2. **Description:** the existing `descriptionKey`, wrapped.
3. **Comparison** (§8), only for features that provide one.
4. **Actions** (§7.3): full-width buttons for specialized tools.
5. **Settings** (§7), in descriptor order.
6. **Empty state:** "This feature has no settings." when there are none.

Settings rows stay editable while the feature is OFF (the draft may be
prepared before enabling), but are drawn dimmed with a hint "Applies when ON"
(Q-SU3).

## 6. ON/OFF switch

A single new widget, `FeatureSwitch extends AbstractButton`, 44×20 GUI px:

```
 ON   ┌──────────────┐        OFF  ┌──────────────┐
      │ ON  ✔ [████] │             │ [████] ✕ OFF │
      └──────────────┘             └──────────────┘
      green track, knob right      red track, knob left
```

- **ON:** green track (`0xFF2E7D32`-ish, final value tuned for contrast),
  knob on the right, white "ON" and a check glyph.
- **OFF:** red track (`0xFFA33A3A`-ish), knob on the left, white "OFF" and a
  cross glyph. Position + glyph + text carry the state without color.
- **Text:** translation keys `qol_unified.settings.on` / `.off`, both "ON"/"OFF"
  in en_us and pt_br, as approved.
- **Knob:** the vanilla button sprite at knob size; the track a 1 px framed
  fill; hover and focus draw the vanilla white focus outline.
- **Input:** click, Space or Enter toggle; it is a normal tab stop.
- **Narration:** "<Feature name>, ON, button" / "OFF", plus the conflict
  tooltip when relevant (from `Texts.conflictReasons`).
- **Locked:** gray track, no glyph change, `active = false`, with a tooltip
  explaining why: configuration read-only, or feature UNKNOWN (then the
  existing "Retry disable" button replaces the switch).
- **Effect:** calls `draft.setEnabled(id, value)` only; nothing is applied
  before Done. No animation.
- **GUI scales 1–4:** integer geometry; text uses the vanilla font; the glyphs
  are vanilla font characters.

Option rows (feature settings) keep the vanilla `CycleButton` "On"/"Off"; only
the master switch uses the new widget, so the two cannot be confused.

## 7. Inline settings

### 7.1 Renderer

The row building in `FeatureSettingsScreen.settingRow` and `integerField`
moves into a shared `SettingRows` helper used by the panel:

| Descriptor | Control |
| --- | --- |
| `BooleanSetting` | Vanilla on/off `CycleButton` |
| `EnumSetting` | `CycleButton` of `UiKeys.enumValue` labels |
| `IntegerSetting` | `EditBox` with the existing range and number errors |
| `MapSetting` | Summary row: "N entries" (Sound Control: "N sound rules") and the feature's action button |

Restart-required settings keep the existing suffix. Labels wrap to two lines
before the control rather than being clipped; controls are right-aligned at a
fixed width (90 px boolean, 120 px enum/integer).

### 7.2 Per feature (from the actual descriptors only)

- **Food HUD:** saturation overlay, food preview, health preview, off-hand
  preview, tooltip (Off / With Shift / Always), match animations, F3 line.
- **Chat Heads:** head position (before the name / start of the line).
- **Keyboard Map:** layout, show unbound keys, contextual labels, default
  filter; action "Open Keyboard Map".
- **Target Info:** blocks, entities, tool recommendations, mining progress,
  block details, entity health, pet owner, source mod.
- **Sound Control:** rule count; action "Open Sound Control".
- **Shulker Preview:** slot content icon, content icon (first occupied /
  predominant), summary on hover, full grid on Shift.

No settings are added.

### 7.3 Specialized tools

- Buttons come from `FeatureActions`; label keys stay the features' own. Two
  wording changes are proposed for consistency (Q-SU7): Keyboard Map
  "Keyboard Map..." → "Open Keyboard Map", Sound Control "Open sound list" →
  "Open Sound Control".
- Opening passes the workspace as parent and the shared draft; Back returns to
  the same feature, same scroll position (the workspace keeps its state in
  fields, §9.3).
- Sound Control keeps its screen (Recent / Rules / All Sounds) and its live
  preview unchanged (§11).

## 8. Visual comparisons

### 8.1 Approaches

| Criterion | A. Bundled screenshots | B. Rendered illustrations (vanilla sprites, items, text) |
| --- | --- | --- |
| Maintenance | Re-capture on every visual change; drift is silent | Uses the feature's own drawing code or the same sprites; changes follow automatically |
| Resolution / GUI scale | Fixed raster; blurry or tiny at scales 1 and 4 | Pixel-exact at every scale |
| Localization | Text baked in one language | Text from translation keys |
| Resource packs | Shows default textures next to a pack's | Uses the active pack |
| Asset size | Tens to hundreds of KB per feature, in the JAR | None |
| Complexity | Low (blit an image) | Medium: a small fixed scene per feature |
| Fidelity | Real game look, including world background | Schematic: no world, just the relevant HUD or tooltip piece |

### 8.2 Recommendation

**B, with a strict scope**: a `FeatureComparison` interface with one method
drawing two panels ("Vanilla" / "QoL ON") in a box of fixed size, implemented
only where the feature's own renderer can draw a frozen example without a
world:

- **Food HUD:** a hunger bar with fixed food/saturation, drawn by the
  existing Food HUD renderer with a fixed player-free state.
- **Shulker Preview:** a sample shulker slot (vanilla) vs the slot icon and
  bar; tooltip shown as the compact component with a fixed summary.
- **Target Info:** an empty crosshair vs the panel with a fixed stone view.
- **Chat Heads:** two chat lines without and with a head, using the default
  Steve/Alex skin (no profile lookups).

Risk: Food HUD and Target Info draw today from live game state (HUD icon
positions, the hit result). Their comparisons need a small entry point that
draws from a fixed sample; that is new code in each feature, kept next to its
renderer.

Keyboard Map and Sound Control get none (their tools are the demonstration).
Q-SU4 asks whether comparisons ship in V1 or a follow-up.

A static screenshot is acceptable only as a last resort, from original game
test captures, never third-party images.

## 9. Search and navigation

### 9.1 Navigation

- **Initial selection:** the first feature of the first non-empty category,
  its category expanded; or, when opened from a feature's own entry point
  (e.g. Controls → Keyboard Map settings), that feature (Q-SU8).
- **States:** hovered row: vanilla highlight; selected feature: white text
  with a `»` marker and a filled row background; selected category header
  stays expanded. Colors are never the only cue.
- **Expand/collapse:** click the arrow or the header; Right/Left on a focused
  header. Expansion state is per session.
- **Scrolling:** the sidebar is a scroll list; no shrinking buttons.
- **Keyboard:** Tab cycles search → sidebar → panel → Done/Cancel. In the
  sidebar, Up/Down move, Enter selects, Right/Left expand/collapse. In the
  panel, Tab walks the controls in order.
- **Remembered for the session:** selected feature, expanded categories,
  sidebar and panel scroll, search text; kept across opening Keyboard Map or
  Sound Control and across window resizes. Not persisted to disk (no config
  write on navigation, §11).

### 9.2 Search

- Matches, case- and accent-insensitive (the Keyboard Map's normalization):
  feature name, description, id, and **setting names** (translated) and enum
  value labels.
- While searching, the sidebar shows a flat result list grouped by category:
  matching features, and under each feature the matching settings as
  indented rows ("Tooltip · Food HUD").
- Selecting a feature result selects it. Selecting a setting result selects
  its feature, scrolls the panel to that row and briefly outlines it.
- Empty result: "No features or settings match" and a clear-search hint.
- Searching never changes configuration; clearing it restores the previous
  expansion state.
- No ranking engine: results follow catalog order.

## 10. Responsive behavior

| Window (GUI px width) | Sidebar | Panel |
| --- | --- | --- |
| ≥ 640 (1280×720 scale 2, 1920×1080 scale 3) | 160 px, full tree | Rest; comparison side by side |
| 400–639 (854×480 scale 2, 1920×1080 scale 4) | 120 px; long names truncated with a tooltip | Rest; comparison stacked if < 260 px |
| < 400 (854×480 scale 3–4) | Collapsed to a 24 px rail with a "☰" button that opens the tree as an overlay drawer | Full width |
| Ultrawide | Sidebar capped at 200 px; panel content capped at 520 px and centered | — |

- Every row is at least 20 px; the panel scrolls vertically; nothing scrolls
  horizontally.
- Labels wrap before they clip; a control never overlaps its label (label
  width = panel width − control width − gap).
- Footer (message, Done, Cancel) is always visible.

## 11. Transaction and rollback rules

- T-1 One `SettingsDraft` for the whole workspace session, created when the
  screen opens (unchanged).
- T-2 Switch, settings and tool screens edit only the draft; navigation and
  search never touch it or the file.
- T-3 Done: `draft.commit()` exactly as today (validate → save → apply;
  restart notice; failure messages). Cancel/Escape: close without commit.
- T-4 Read-only configuration: switch and controls locked, Done disabled, the
  existing message shown.
- T-5 Invalid integer input stays a field error and blocks Done; the error
  line is shown in the panel of that feature, and the sidebar marks the
  feature with "!" so the error is findable after navigating away.
- T-6 The workspace screen implements `DraftScreen`. Sound Control's live
  preview follows the draft while the workspace or its own screen is shown,
  and ends when neither is (Done or Cancel), exactly as now. Opening Sound
  Control from the panel passes the same draft and parent; Back restores the
  workspace with the same feature selected.
- T-7 No configuration migration and no new keys: navigation state is
  in-memory only.
- T-8 The separate `FeatureSettingsScreen` is retired once its row rendering
  moves to `SettingRows`; any entry point that opened it opens the workspace
  with that feature selected.

## 12. Implementation architecture

Reuse: `SettingsDraft`, `DraftScreen`, `FeatureActions`, `FeatureStatus`,
`Texts`, `UiKeys`, `RowList` (row layout), `SettingsKeyBinding`.

Extract or add (small, UI package only):

| Class | Responsibility |
| --- | --- |
| `SettingsNavigation` (pure) | Visible sidebar rows from the registry, expansion set, selection, search results incl. settings; unit-tested like `FeatureSearch` (which it absorbs) |
| `SettingRows` | Descriptor → control row (moved from `FeatureSettingsScreen`) |
| `FeatureSwitch` | The ON/OFF widget |
| `FeaturePanel` | Builds the panel rows for one feature (header, description, comparison, actions, settings) |
| `FeatureComparison` + registry (optional) | Like `FeatureActions`: features register a drawer; the panel shows it if present |
| `SettingsScreen` | Becomes the workspace: sidebar list + panel list + footer, keeps the session state |

No Core, registry, descriptor or configuration changes. No reflection; no new
framework. Features that register a comparison do so in their existing hooks,
next to `FeatureActions.register`.

## 13. Accessibility and localization

- All new text uses translation keys in en_us and pt_br; ON/OFF read "ON"/"OFF"
  in both by approval.
- Narration: sidebar rows narrate "Category, expanded, N features" and
  "Feature, selected, ON/OFF"; the switch as in §6; setting rows keep vanilla
  narration.
- State cues never rely on color alone (§6, §9.1).
- Focus outlines are the vanilla ones; every control is reachable with Tab.
- Long Portuguese labels wrap instead of clipping (checked at scale 4).

## 14. Acceptance criteria

- SU-AC-1 Every setting of every feature can be changed without opening a
  second screen (Keyboard Map and Sound Control tools excepted).
- SU-AC-2 Navigating between features and categories, searching, and opening
  and leaving a tool never writes the configuration file and keeps all
  unsaved edits.
- SU-AC-3 Done saves once and applies as today; Cancel/Escape discard; the
  read-only and invalid-input behavior of M0 is unchanged.
- SU-AC-4 The switch shows ON (green, knob right, check) and OFF (red, knob
  left, cross), works with mouse, Space and Enter, narrates its state, and is
  locked with an explanation when the configuration is read-only.
- SU-AC-5 Boolean, enum, integer and map settings render from descriptors;
  map settings show a summary and the tool button.
- SU-AC-6 Searching "tooltip", "mining" or "sound" finds the matching
  settings and features; a setting result reveals the setting in its panel;
  no result shows the empty state.
- SU-AC-7 Keyboard Map and Sound Control open from the panel and return to
  the same feature; Sound Control's preview stays audible while editing and
  stops after Cancel with the committed volumes restored.
- SU-AC-8 At 854×480 scale 2, 1280×720, 1920×1080 scales 1–4 and an
  ultrawide window: no clipped labels, no overlapping controls, sidebar
  collapses below 400 GUI px.
- SU-AC-9 Full keyboard operation: Tab order, sidebar arrows, Enter, Escape.
- SU-AC-10 en_us and pt_br complete; LocalizationTest covers new keys.
- SU-AC-11 Existing game tests that drive the settings UI are updated and
  pass; all feature game tests still pass.
- SU-AC-12 Comparisons (if in scope) render at every GUI scale and follow the
  active resource pack and language.

## 15. Decisions (resolved in §0)

- **Q-SU1 [Approved 2026-11-25]** Approve Concept A (expandable sidebar).
- **Q-SU2 [Approved 2026-11-25]** Approve the `FeatureSwitch` appearance in §6 (green/red
  track, knob side, check/cross glyph, "ON"/"OFF").
- **Q-SU3 [Approved 2026-11-25]** Settings of an OFF feature: editable but dimmed with
  "Applies when ON" (proposed), or locked until ON.
- **Q-SU4 [Approved 2026-11-25]** Visual comparisons: approach B; ship in M1.7 for Food HUD
  and Shulker Preview only, or defer all comparisons to a follow-up.
- **Q-SU5 [Approved 2026-11-25]** Empty categories: hidden (current behavior, proposed) or
  shown with "No features yet".
- **Q-SU6 [Approved 2026-11-25]** Tool buttons for features turned ON in the draft but not
  yet active: disabled with "Press Done to enable first" (proposed, keeps the
  approved rule), or allowed.
- **Q-SU7 [Approved 2026-11-25]** Rename the action labels to "Open Keyboard Map" and "Open
  Sound Control".
- **Q-SU8 [Approved 2026-11-25]** Opening from a feature's own entry point preselects that
  feature.
- **Q-SU9 [Approved 2026-11-25]** Retire `FeatureSettingsScreen` (T-8).

## 16. Recommended design

Concept A with the `FeatureSwitch`, inline descriptor-driven settings,
setting-level search, a collapsible sidebar below 400 GUI px, and rendered
comparisons limited to features whose own renderer can draw a frozen
example. It is a UI-package refactor around the existing draft and
descriptors, with no Core or configuration change.

## 17. Implementation notes [Implemented 2026-11-25]

### 17.1 Code (UI package only)

| Class | Role |
| --- | --- |
| `ui/SettingsNavigation` | Pure sidebar model: categories with features (empty ones hidden), expansion, selection, preselection, search over names, descriptions, ids, setting names and enum labels (case- and accent-insensitive), expansion restored when search is cleared, invalid-input flags. Replaces `FeatureSearch`. |
| `ui/FeatureComparisons` | Registry of "Vanilla / QoL ON" samples, like `FeatureActions` |
| `ui/screen/SettingsScreen` | The workspace: search, sidebar, sticky header (name, status, switch or Retry disable), scrolling panel, footer. Implements `DraftScreen`; keeps navigation and scroll state in fields across tool screens. |
| `ui/screen/NavList` | Sidebar tree of 20 px rows (arrow, count, `»` marker, `!` flag), Left/Right collapse/expand, tooltips with full names |
| `ui/screen/FeatureSwitch` | The ON/OFF switch (§6) |
| `ui/screen/PanelList`, `FeaturePanel`, `SettingRows` | Panel rows with their own heights; descriptor-driven controls moved unchanged from the retired screen; labels and details wrap |
| `feature/foodhud/FoodHudComparison`, `feature/shulkerpreview/ShulkerComparison` | The two approved comparisons, drawn with the features' own math, sprites and decorator |

Retired: `FeatureSettingsScreen`, `RowList`, `FeatureSearch` (Q-SU9). Core,
registry, descriptors, configuration and `SettingsDraft` are unchanged.

### 17.2 Behavior as built

- Layout tiers: sidebar 160 px from 640 GUI px, 120 px from 400, otherwise a
  "☰" rail with a drawer that closes after choosing; on wide screens the
  sidebar and the panel (max 520 px) are centered together.
- After every rebuild the selected feature is scrolled into view.
- While searching, the panel follows the first match if the selection is not
  among the results; a setting result selects its feature, scrolls to the row
  and highlights it for 1.5 s.
- Invalid integer input is shown in the footer and the feature gets "!" in
  the sidebar until fixed.
- Tool actions ("Open Keyboard Map", "Open Sound Control") and comparisons
  are registered at client start, so they show even for features that were
  never enabled; actions stay disabled with an explanation until the
  feature is ACTIVE.
- The Keyboard Map's Controls button keeps its own "Keyboard Map..." label;
  the panel uses the new `open_tool` key.

### 17.3 Limitation found: items need a world

In Minecraft 26.3 item components are bound only when a world is loaded;
creating an `ItemStack` on the title screen throws. The Shulker Preview
comparison therefore shows "Example available while in a world." outside a
world, and the Food HUD comparison omits its bread icon there (the hunger
bar itself uses sprites only and always draws).

### 17.4 Tests

- Unit: `SettingsNavigationTest` (tree, expansion, preselection, search
  normalization, setting and option results, restore after search, flags).
- Game test `runGameTestSettings`: every real feature navigated in one
  screen, descriptor types, switch by mouse, Enter and Space, draft kept
  across features, nothing written by navigation or Cancel, Tab focus,
  Left/Right on categories, search results and empty state, Keyboard Map
  round trip, Done persistence, tiers at 854×480, 1100×760, 1280×720,
  1920×1080 and 2560×1080 at every available scale, the drawer, comparisons
  in a world.
- Updated: the core scenarios (toggle, invalid input, read-only, Retry
  disable, conflict override, restart notice) and Sound Control's nested
  preview, which now also checks that switching features keeps the preview.
