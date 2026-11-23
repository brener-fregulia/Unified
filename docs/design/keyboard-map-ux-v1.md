# Keyboard Map UX refinement (M1.3.1)

Status labels follow [AGENTS.md](../../AGENTS.md#decision-status-labels).
**Approved 2026-11-23 (§0)**: Concept A with the §9 improvements,
KMUX-AC-1 to KMUX-AC-13 with the corrections below. Implementation notes are
in §13. Before approval this document was design only. It changes nothing in the approved
[Keymap V1 specification](../specifications/keymap-v1.md), the binding
model, remapping semantics, settings, entry points or persistence. Those
stay as approved on 2026-11-23 and implemented in §17 of that spec.

**Sources.**
- The PO's written feedback (§1).
- Screenshots of the current implementation taken by its game test.
- The public Modrinth gallery images of Keymap [Maintained], Keyboard Wizard CE and Visual Keymap, downloaded and viewed on 2026-11-23.
- The 26.3 client JAR.

The PO's own screenshots of the current screen and of reference interfaces
were **not available in this session**. Nothing here claims to have seen
them; Q-UX1 asks for a check against them.

## 0. Approval [Approved 2026-11-23]

- **Q-UX1, Q-UX2:** Concept A, with the §9 improvements, matches the PO's intended direction.
- **Q-UX3:** muted filled keys with defined outlines (§8.1).
- **Q-UX4:** highlight search matches; do not dim non-matching keys.
- **Q-UX5:** left-handed mirroring deferred.
- **Q-UX6:** keep a visible but secondary "Vanilla Key Binds" button, **outside** Filters. This corrects §8.2 item 4 and §10.
- **Q-UX7:** the wheel tooltip explains that scrolling is not a vanilla remappable key binding.
- **Q-UX8:** spatial arrow-key navigation and advanced narration deferred; existing focus behavior kept.
- **Corrections:**
  - Selecting an **unbound** action from Explore shows that action's own card with Change enabled, never an empty input card (corrects §6.2).
  - An unused key's card shows an empty state with a way to find an unbound action.
  - Selection never writes bindings.
  - Concept A fallback order: (1) keyboard, mouse beside it and panel; (2) mouse below the keyboard; (3) navigation, numpad and mouse under "More keys"; (4) tabs.

## 1. Current usability problems

From the PO's feedback, confirmed against the V1 screenshots (keymap-v1.md
§17):

| Id | Problem | Cause in V1 |
| --- | --- | --- |
| **P-1** | Too many controls compete for attention. | Five toolbar controls and five bottom buttons are always visible, with equal weight. |
| **P-2** | The side panel shows too many unrelated bindings. | The full list of 100+ mappings is always shown, even when a key is selected. |
| **P-3** | The keyboard looks like a dense grid of colored rectangles. | Full-saturation fills, 1 px gaps, no group spacing, every key filled. |
| **P-4** | Red overlap indicators dominate. | 26.3 debug mappings make about 15 keys OVERLAP on a fresh install (keymap-v1.md §17.4), drawn in strong red with `!N` markers. |
| **P-5** | Groups are not separated. | Function row, main block, navigation and numpad differ only by a 2-unit gap. |
| **P-6** | The mouse is a row of text chips. | Chips are a fallback, not a picture. |
| **P-7** | Remapping buttons show with nothing selected. | The bottom bar is static; its buttons are only disabled. |
| **P-8** | Search and filters take too much space. | Four full-width cycle buttons plus a search box. |

## 2. Design principles

- **DP-1 The hardware is the interface.** The keyboard and mouse are the largest, calmest elements. Everything else is secondary.
- **DP-2 Show what is asked.** With nothing selected, browse. With an input selected, show only that input.
- **DP-3 Quiet by default, loud on focus.** Restrained fills. Strong color and outlines only for the selection and the hovered input.
- **DP-4 Actions where they apply.** Change, Clear and Reset live on the binding row they act on, never in a global bar.
- **DP-5 Never color alone.** Every state also has a shape or number marker (keymap-v1.md §0).
- **DP-6 Vanilla-native.** Minecraft font, flat rectangles, vanilla buttons and widgets, no textures, gradients or animation.
- **DP-7 Nothing changes underneath.** Same catalog, states, filters, `RemapSession`, settings and entry points.

## 3. Reference findings

Only public gallery images and descriptions were seen. No code, textures or
translations were used.

| Mod | What the images show | Useful | Avoid |
| --- | --- | --- | --- |
| Keymap [Maintained] (banner image) | Filled green/red keys; a right-hand list with small colored squares per row; top tabs "Settings / Layouts / Help"; a hover label under a key naming the action and its mod. | A small status square per row instead of colored text. Hover label close to the key. | Saturated fills over the whole keyboard. |
| Keyboard Wizard CE (26.1.2 image) | **Outline-only keys**: dark interior, green/red/white border and text. A left list with the action on one line and its key on a second, dimmer line. Tabs for Main / Numpad / Auxiliary. The mouse is a single labeled box with − / + to step through buttons. Reset / Clear / Reset all at the bottom. Faint decorative grid lines behind. | Outline-only states keep a big keyboard calm. Two-line rows read well. | A mouse as a stepper box. Global action bar. Decorative background lines. |
| Visual Keymap (gallery image) | Flat filled keys, dark green and dark red, amber for modifier-combo matches; **search matches outlined in yellow**. Mouse buttons placed as extra keys in the navigation area (Left, Right, Middle, M4, M5). **A bottom panel listing only the bindings on the selected key**, each with its key button and a Reset button. A single Done button. | The contextual panel for one key is the closest match to the PO's selected-input mode. Muted fills. Search as an outline, not a dim. | Mouse buttons disguised as keys. Amber (excluded in V1). |

**Summary.**
- The best-looking references use either outline-only states or muted fills.
- They show the selected key's bindings in their own panel.
- They put per-binding actions on the binding row.
- None draws a recognizable mouse.

## 4. Two layout alternatives [Concept A approved 2026-11-23]

Both use the same states (§8.1), the same mouse (§5) and the same
interaction model (§6). They differ in where the mouse and the panel go.

### 4.1 Concept A: keyboard and mouse side by side, contextual panel on the right

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ Keyboard Map                     [🔍 Search…            ] [≡ Filters ▾]  [Done] │
├──────────────────────────────────────────────────────┬───────────────────────┤
│  Esc   F1 F2 F3 F4   F5 F6 F7 F8   F9 F10 F11 F12    │ EXPLORE               │
│                                                      │ Movement              │
│  ` 1 2 3 4 5 6 7 8 9 0 - = Bksp   Ins Hm Pu  Nm / * -│  Walk Forward      W  │
│  Tab Q W E R T Y U I O P [ ]  \   Del En Pd  7  8  9 │  Strafe Left    A  2  │
│  Caps A S D F G H J K L ; '  Ent              4  5  6│ Gameplay              │
│  Shift Z X C V B N M , . /  Shift      ↑      1  2  3│  Attack      Mouse L  │
│  Ctrl Sup Alt [  Space  ] Alt Sup Mn Ctrl  ←  ↓  → 0 .│ …                     │
│                                         ╭───╮        │                       │
│                                         │L│R│ mouse  │                       │
│                                         │ ▮ │        │                       │
│                                        ▯│   │        │                       │
│                                        ▯╰───╯        │                       │
├──────────────────────────────────────────────────────┴───────────────────────┤
│ Hover or click a key. ■ free  ■ 1 action  ■² shared                          │
└──────────────────────────────────────────────────────────────────────────────┘
```

- **Keyboard:** centered in the left 70–75%, groups separated by visible gaps.
- **Mouse:** to the right of the numpad, bottom-aligned with the keyboard, same height as 4–5 key rows.
- **Explore mode:** right panel lists bindings (search and filters apply).
- **Selected-input mode:** the right panel becomes the input card (§6.2).
- **Toolbar:** a single row with title, one search box, one Filters dropdown (category plus show-mode) and Done. Layout lives inside Filters.
- **Remapping controls:** only inside the input card, per row.
- **Small windows:** the mouse moves below the numpad. Below the size limits, keyboard and panel become tabs (§7).
- **Advantages:**
  - Mouse and keyboard read as one desk setup at the same scale.
  - Right panel position is familiar from V1 and from Keyboard Wizard CE.
  - Contextual panel and list share one slot, so nothing jumps.
- **Disadvantages:**
  - The mouse needs about 8 units of width; at 854×480 GUI 2 it no longer fits beside the full keyboard plus panel, so it moves.
  - Less vertical room for a long input card on 16:9.

### 4.2 Concept B: keyboard on top, mouse below beside the controls, collapsible side panel

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ Keyboard Map              [🔍 Search…        ] [≡ Filters ▾]   [◧ Panel] [Done] │
├──────────────────────────────────────────────────────────────────────────────┤
│   Esc  F1 F2 F3 F4  F5 F6 F7 F8  F9 F10 F11 F12   PrS ScL Pau                 │
│   ` 1 2 3 4 5 6 7 8 9 0 - = Bksp     Ins Hom PgU   Nm / * -                   │
│   Tab Q W E R T Y U I O P [ ] \      Del End PgD   7  8  9  +                 │
│   Caps A S D F G H J K L ; ' Enter                 4  5  6                    │
│   Shift Z X C V B N M , . / Shift        ↑         1  2  3  Ent               │
│   Ctrl Sup Alt [   Space   ] Alt Sup Mn Ctrl  ← ↓ →   0     .                 │
├───────────────┬──────────────────────────────────────────────────────────────┤
│   ╭───┬───╮   │ SELECTED: Q · 2 actions                              [✕ Back] │
│   │ L │ R │   │  Drop Selected Item       Q   [Change] [Clear] [Reset]       │
│  ▯│  ▮│   │   │  Sample Mod Action        Q   [Change] [Clear] [Reset]       │
│  ▯│       │   │  2 actions use Q; Minecraft runs all of them.                │
│   ╰───────╯   │                                                              │
└───────────────┴──────────────────────────────────────────────────────────────┘
```

- **Keyboard:** full width across the top, the most dominant possible.
- **Mouse:** bottom-left, beside the contextual strip.
- **Explore mode:** the binding list is a collapsible right **drawer** ("◧ Panel") over the keyboard's right edge. Closed, the bottom strip shows the legend.
- **Selected-input mode:** the bottom strip is the input card, wide enough for one row per binding with inline actions.
- **Toolbar:** as in A, plus the panel toggle.
- **Small windows:** keyboard on top, mouse and card below. The drawer covers the keyboard when open.
- **Advantages:**
  - Maximum keyboard size.
  - Wide rows fit long translated names and three buttons on one line.
  - The mouse sits next to the actions it often carries (Attack, Use, Pick).
- **Disadvantages:**
  - Two places hold binding information: drawer and strip.
  - The drawer hides keys while open.
  - The bottom strip has little height on 16:9, so more than about 4 bindings on one key needs scrolling.
  - Browsing all bindings is one click further away.

### 4.3 Comparison

| Criterion | A | B |
| --- | --- | --- |
| Keyboard dominance | High | Highest |
| One place for binding info | Yes | No (drawer + strip) |
| Browsing all bindings | Always visible | One click |
| Long names (pt_br) | Panel width ~30% | Strip full width |
| 854×480 GUI 2 | Mouse below numpad; panel stays | Strip very short |
| Implementation change from V1 | Smaller (same panel slot) | Larger (drawer and strip) |

## 5. Mouse diagram specification [Approved 2026-11-23]

- **MD-1 Silhouette.** A rounded, symmetric body 8 units wide × 12 units tall, in the keyboard's quarter-key units, drawn with the same outline and fill style as keys. Rounding comes from stepped corner rectangles (2-unit steps), not textures. No brand shape.
- **MD-2 Regions (clickable, each its own input):**

| Region | Input | Position |
| --- | --- | --- |
| Left button | mouse 1 | top-left, 4 × 5 units |
| Right button | mouse 3 | top-right, 4 × 5 units, with a 1 px divider |
| Wheel | mouse 2 (middle click) | 1.5 × 3 units centered on the divider, lighter outline, with a small tick for "wheel" |
| Side button 4 ("back") | mouse 4 | left flank, lower, 1 × 2 units outside the body |
| Side button 5 ("forward") | mouse 5 | left flank, upper, 1 × 2 units outside the body |
| Body | — | not an input; neutral |

- **MD-3 States and selection** exactly as keys: fill and marker by state (§8.1), blue outline when selected, white outline on hover, one click selects, a second click clears.
- **MD-4 Labels.** Regions too small for text show no label. Hover shows a tooltip with the OS/vanilla button name ("Left Button", "Button 4") and its actions. Side buttons are tagged "4" and "5" inside, once there is room for 1 glyph.
- **MD-5 More buttons.** Buttons 6 and up that carry bindings appear as small numbered tabs under the mouse ("6", "7", …). They are always reachable from the list and through search, `key:` included.
- **MD-6 Wheel scrolling (finding).** In 26.3 wheel scrolling is not a key mapping. `MouseHandler.onScroll` handles it directly (hotbar slot, spectator menu, zoom-like uses by vanilla screens), and `InputConstants` has no wheel-up or wheel-down input. The wheel region is therefore **only middle click** (mouse 2). A non-interactive note in its tooltip can read "Scrolling changes the hotbar slot (not a key binding)". Wheel up and down are **not** offered as remappable inputs.
- **MD-7 Handedness.** The side buttons sit on the left flank (right-handed mice). Mirroring for left-handed players is Q-UX5.

## 6. Interaction model [Approved 2026-11-23, with §0 corrections]

### 6.1 Explore mode (nothing selected)

- Keyboard and mouse show every input with its state.
- The panel lists bindings grouped by category, two lines per row: the action in white on line 1, its key dim on line 2 (as in Keyboard Wizard CE). A small state square sits at the left of the row (□ free, ■ one, ■² shared), and the count shows only when shared.
- **No remapping buttons.**
- Search and Filters (category, show-mode, layout) apply to the list. On the keyboard, **matches get a thin yellow-white outline** (Visual Keymap pattern) instead of dimming everything else.
- **Hover** on a key or mouse region shows a compact label next to it (§6.3).

### 6.2 Selected-input mode (a key or mouse button selected)

Triggered by clicking an input or by clicking a row (its input becomes
selected and the row's binding is pre-focused). The panel becomes an **input
card**:

```
┌ Q ─────────────────────────── 2 actions ─ [✕] ┐
│ ■ Drop Selected Item            Inventory     │
│   [Change] [Clear] [Reset]  (default: Q)      │
│ ■ Sample Mod Action on Q       QoL Dev Samples │
│   [Change] [Clear] [Reset]  (default: Q)      │
│ ───────────────────────────────────────────── │
│ ⓘ 2 actions use Q. Minecraft runs all of them │
│   when it is pressed.                         │
└───────────────────────────────────────────────┘
```

- **Header:** input name (OS label), count, and ✕ to return to Explore.
- **Body:** **only** the bindings on that input, each with its category and its own Change / Clear / Reset. Clear and Reset are disabled when they would change nothing. For an unbound input the body reads "No actions use this key" plus "Assign an action…", which returns to Explore with the "Unbound" filter preset.
- **Overlap note:** neutral wording, shown only when count ≥ 2. Nothing is labeled harmful.
- **Remapping:**
  - Change puts that row into capture ("Press a key or mouse button… Esc cancels").
  - The confirmation for a new overlap appears **inside the row**: "Also used by: …  [Use anyway] [Cancel]".
  - Same `RemapSession` semantics as V1.
  - After a change, the card follows the **binding** to its new input.
- **Leaving:** ✕, Escape (when not capturing), clicking the selected input again, or clicking empty space on the keyboard area.
- **Search and filters while selected:** the simplest consistent rule. Search and Filters stay visible and keep their values, but they affect **only the Explore list and the keyboard match outlines**, never the input card. Typing in search leaves selected-input mode and returns to Explore with the query applied, because the player is looking for something else.

### 6.3 Contextual labels

- Only for the **hovered** input (selection uses the panel), never for more than one input.
- A compact tag with up to 3 action names plus "+N", placed in the nearest free space outside the keyboard block. That means above the function row, below the bottom row, or beside the mouse. One straight connector line, 1 px, in the outline color, from the key edge to the tag.
- Tags never overlap keys. If no free space is close enough (small windows), no tag is drawn and the hover is shown only as the outline plus a vanilla tooltip.
- The `contextual_labels` setting turns tags off (tooltips remain).

## 7. Responsive layout rules [Approved 2026-11-23, §0 order]

Quarter-key unit `u`, in GUI pixels. Approved minimum key height: 12 px
(`u ≥ 3`, keymap-v1.md §12 and §17.3).

| Window, GUI size | Concept A | Concept B |
| --- | --- | --- |
| 854×480, GUI 2 → 427×240 | Main block + function row at `u = 4`; "More keys" for nav/numpad; mouse below the main block at `u = 3`; panel 130 px. Tags off (tooltips only). | Keyboard main block at `u = 4`; strip ≤ 60 px: about 2 rows, then scroll. |
| 1280×720, GUI 2 → 640×360 | Full keyboard at `u = 4`, mouse below numpad, panel ~190 px. | Full keyboard at `u = 5`; strip ~90 px. |
| 1280×720, GUI 1 / 1920×1080, GUI 2 → 960×540 | Full keyboard + mouse beside at `u = 6`, panel ~260 px. Tags on. | Full keyboard at `u = 7`; strip ~140 px; drawer 280 px. |
| 1920×1080, GUI 1 | `u` capped at 9 (36 px keys); extra space goes to margins and panel (max 320 px). | Same cap. |
| Ultrawide 3440×1440 | Content centered, max width ~1600 GUI px; world visible at the sides. | Same. |
| GUI 3–4 on small windows | Vanilla lowers the effective scale; same rules apply to the resulting GUI size. | Same. |

**Mouse placement rules (A):**
1. Beside the numpad if `width ≥ keyboard + 10u + panel + margins`.
2. Otherwise below the numpad or main block.
3. Otherwise in the "More keys" view, together with nav and numpad.

**Narrow windows (both):** below 60 × `u_min` + panel width, keyboard/mouse
and panel become **tabs** ("Controls" / "Actions"). Selecting an input
switches to the Actions tab with its card.

**Fixed rules:** keys are never under 12 px; nothing overlaps; every input is
reachable directly, through "More keys", or from the list.

## 8. Visual hierarchy, palette and accessibility [Approved 2026-11-23]

### 8.1 States [Approved 2026-11-23]

| State | Interior fill | Outline | Text | Extra marker |
| --- | --- | --- | --- | --- |
| No binding | `#2A2A2A` at ~80% | `#3C3C3C` | `#9A9A9A` | — |
| One binding | `#2F5D3A` (muted green) at ~85% | `#3F7A4C` | white | — |
| Two or more | `#6B2E2E` (muted red) at ~85% | `#8A3B3B` | white | count badge, top-right, `²` style small digits |
| Selected | (state fill) | **2 px `#4FC3F7`** | white | — |
| Hover | (state fill, +8% light) | 1 px white 60% | white | — |
| Search match | (state fill) | 1 px `#F0E6A0` | white | — |

- The muted red and green differ in **lightness** as well as hue, so they stay distinct for deuteranopia and protanopia. A simulation check is in KMUX-AC-9.
- The overlap badge (a number) is the non-color cue; free keys are visibly dimmer.
- Key interiors are 1 px inset from the outline, with 1 px gaps between keys inside a group. Group gaps are half a key between function-row blocks and a full key between main, navigation and numpad. The function row sits half a key above the number row.

### 8.2 Hierarchy

1. **Keyboard and mouse:** the largest area, highest contrast outlines.
2. **Selected input and its actions:** blue outline plus the input card.
3. **Search and filters:** one compact toolbar row; filters collapsed in a dropdown, with a dot on the Filters button when any filter is active.
4. **Secondary actions:** Done; "Key Binds…" moves into the Filters dropdown footer as "Open vanilla Key Binds".
5. **Status and help:** one dim line at the bottom (legend in Explore, capture prompt while capturing), plus a "?" tooltip.

### 8.3 Background

- Vanilla in-world background (blur and dimming) stays.
- Two translucent panels (`#101010` at ~70%), behind the controls area and behind the side panel.
- No full-screen opaque overlay.
- Contrast target: text at least 4.5:1 against the panel color, independent of the world behind (panel opacity is chosen for that).

### 8.4 Keyboard and screen-reader access

- Tab order: search → Filters → controls area → panel → Done.
- In the controls area, the arrow keys move a focus outline between keys (spatial nearest neighbor). Enter selects, Escape returns to Explore, then closes.
- Narration announces the focused input, its state and its actions (vanilla narration API).

## 9. Recommendation [Approved 2026-11-23]

**Concept A, with two improvements taken from B:**
1. Full-width input-card rows inside the right panel (two-line rows with actions on line 2).
2. Mouse-below placement as the first fallback.

**Rationale:**
- One place for all binding information.
- Browsing stays one glance away.
- Smallest change from the V1 code structure (same panel slot, same `RemapSession`).
- The mouse reads as part of the hardware at the same scale.

B's maximum keyboard size does not outweigh splitting information between a
drawer and a strip, especially at 854×480 where B's strip shows only about
2 rows.

## 10. Implementation implications

None of these are implemented here.

- **Unchanged:** `InputId`, `KeyboardLayouts` scancodes, `BindingCatalog`, `BindingFilter`, `Selection`, `RemapSession`, `MinecraftBindings`, settings, entry points, persistence, Core.
- **`KeyboardLayouts`:** group-gap geometry only (function row offset, wider gaps between groups). Existing tests stay; the no-overlap and coverage tests apply.
- **New pure `MouseLayout`:** region rectangles in the same units, with hit-testing and a no-overlap test, so the mouse is tested like the keyboard.
- **`KeyboardMapGeometry`:** mouse placement rules (§7) and tab mode with two named tabs. Extend `KeyboardMapGeometryTest` with the §7 window table.
- **`KeyboardMapScreen`:** split into small drawing/handling parts (keyboard view, mouse view, explore list, input card, toolbar with a Filters dropdown) inside the same feature package. No new framework. Remove the global bottom action bar.
- **Filters dropdown:** vanilla widgets only (`CycleButton`s in a small popup panel). No new dependency.
- **Settings:** no new setting is needed. `show_unbound_keys` maps to the "free key" dimming; `contextual_labels` maps to hover tags.
- **Game tests:** update `KeyboardMapGameTest` to:
  - click mouse regions;
  - assert selected-input mode shows only that input's bindings;
  - assert no remap buttons in Explore;
  - check per-row Change, Clear and Reset;
  - check that the overlap confirmation sits in the row;
  - check that typing returns to Explore.
  - Screenshots stay at the same window and scale matrix.
- **Risk:** the arrow-key navigation inside the controls area must not conflict with search editing. It is active only when the controls area has focus.

## 11. Acceptance criteria [Approved 2026-11-23]

| Id | Criterion |
| --- | --- |
| **KMUX-AC-1** | In Explore mode no Change, Clear or Reset control is visible. |
| **KMUX-AC-2** | Selecting an input shows a card listing exactly the bindings on that input, with its name and count; ✕, Escape and re-clicking return to Explore. |
| **KMUX-AC-3** | Clicking a list row selects its input and opens the same card; an unbound row opens an empty card with a way back. |
| **KMUX-AC-4** | Change, Clear and Reset work per row with V1 semantics, including the overlap confirmation and Escape cancel (V1 game-test assertions still pass). |
| **KMUX-AC-5** | The mouse diagram has five clickable regions (1, 2, 3, 4, 5) with the same states and selection as keys; bound buttons ≥ 6 are reachable. |
| **KMUX-AC-6** | The wheel is offered only as middle click; no wheel-up or wheel-down input appears anywhere. |
| **KMUX-AC-7** | At most one connector line and one tag are drawn at a time, never overlapping a key; none at 427×240. |
| **KMUX-AC-8** | Function row, main block, navigation and numpad are separated by the §8.1 gaps on both layouts; no key under 12 px at any size in §7. |
| **KMUX-AC-9** | States are distinguishable in grayscale and in deuteranopia and protanopia simulations of the screenshots. |
| **KMUX-AC-10** | The toolbar is one row at every size in §7, with filters collapsed into one control. |
| **KMUX-AC-11** | Typing in search while an input is selected returns to Explore with the query applied. |
| **KMUX-AC-12** | Every V1 behavior in keymap-v1.md §0 still holds: US ANSI and ABNT2, modded mappings, search, filters, LIVE settings, entry points, `options.txt`, localization; Food HUD and Chat Heads tests pass. |
| **KMUX-AC-13** | The PO compares the screenshots at 854×480, 1280×720 and 1920×1080 with the previous version and approves them (Q-UX1). |

## 12. Open PO decisions

- **Q-UX1 [Approved 2026-11-23]** Concept A matches the intended direction.
- **Q-UX2 [Approved 2026-11-23]** Concept A.
- **Q-UX3 [Approved 2026-11-23]** Muted filled keys with defined outlines.
- **Q-UX4 [Approved 2026-11-23]** Highlight matches.
- **Q-UX5 [Deferred]** Left-handed mirroring.
- **Q-UX6 [Approved 2026-11-23]** Visible secondary button outside Filters.
- **Q-UX7 [Approved 2026-11-23]** Explain it in the wheel tooltip.
- **Q-UX8 [Deferred 2026-11-23]** Spatial arrow-key navigation and advanced narration; existing focus behavior kept.
