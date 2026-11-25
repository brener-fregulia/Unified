# Shulker Preview V1

Status labels follow [AGENTS.md](../../AGENTS.md#decision-status-labels).
Approved by the Product Owner on 2026-11-25 (§1, §1.1). Items still marked
[Proposed] in the text are covered by §1.1 unless stated otherwise.

Show what is inside a shulker box item without placing it: a small icon of
the main content on the slot, a compact preview on hover and the exact 9×3
layout with Shift. Inspired by the behavior of Shulker Box Tooltip and Simple
Shulker Preview; no third-party code, textures or translations.

## 1. Direction [Approved 2026-11-25]

- V1 covers shulker box items only (all 17 colors).
- Normal hover shows a compact preview directly; no "contains 27 stacks"
  intermediate step.
- Shift + hover shows the full 9×3 preview in the real slot layout.
- The slot icon shows the predominant item.
- No server mod required.

### 1.1 Decisions [Approved 2026-11-25]

- Q-SP1: milestone **M1.6**, after Sound Control (M1.5).
- Q-SP2: tie rule: the kind whose first stack has the lowest slot index wins.
- Q-SP3: grouping by item, ignoring components; a representative original
  stack is used for icons.
- Q-SP4: capacity bar in V1, following the vanilla bundle bar (§6.1a).
- Q-SP5: the box's dye color as a subtle accent of the preview.
- Q-SP6: soft conflicts with Shulker Box Tooltip and Simple Shulker Preview,
  after verifying their Fabric ids.
- Q-SP7: Mixins SP-1, SP-2 and SP-3, narrowly scoped.
- Q-SP8: the three LIVE settings of §7. With `compact_preview` off, vanilla
  lines stay unless the full preview is showing.
- Identity: `shulker_preview`, Inventory, enabled by default, client-only.
- Unknown contents (no component sent, or ungenerated loot) and known empty
  contents show no icon, no bar and the vanilla tooltip; nothing is invented.

## 2. Feature identity [Approved 2026-11-25]

| Item | Proposal |
| --- | --- |
| Feature id | `shulker_preview` (already in the catalog) |
| Category | Inventory (`inventory`) |
| Milestone | M1.6 |
| Default | Enabled |
| Lifecycle | LIVE (render-only; nothing to restore) |
| Side | Client-only; no packets, no gameplay change |

Non-goals: chests, barrels, bundles, ender chests, opening or editing a box
from the tooltip, any server component.

## 3. Sources

| Source | What was used |
| --- | --- |
| Minecraft 26.3 (`minecraft-merged-deobf-26.3.jar`, `javap`; client JAR listing) | APIs and sprites in §4 |
| [Shulker Box Tooltip](https://curseforge.com/minecraft/mc-mods/shulkerboxtooltip) | Public description only |
| [Simple Shulker Preview](https://modrinth.com/mod/simple-shulker-preview) | Public description only |
| PO reference image (2026-11-25) | Slot icon at the top right of the box, capacity bar below |

## 4. Minecraft 26.3 findings (verified)

- **Data is on the client.** A shulker box item stores its contents in the
  `minecraft:container` data component (`DataComponents.CONTAINER`,
  `ItemContainerContents`, up to 27 slots, empty slots kept so positions are
  exact). Item stacks are sent to clients with all their components
  (`ItemContainerContents.STREAM_CODEC`), so a vanilla client already holds
  the full contents of every shulker in any open inventory or container. No
  server mod or request is needed. Reading API: `copyInto(NonNullList)`
  (exact slots), `nonEmptyItemCopyStream()`, `size()`.
- **Vanilla tooltip.** `ItemContainerContents` is a `TooltipProvider`: it adds
  up to five lines `item.container.item_count` ("Name x16") and then
  `item.container.more_items` ("and N more…"). Our preview replaces those
  lines while the feature is active (§6.4).
- **Tooltip images.** `ItemStack.getTooltipImage` returns empty for shulkers;
  the Food HUD already supplies its own image through the same Mixin point
  (`ItemStackTooltipMixin`) and Fabric's `ClientTooltipComponentCallback`.
  Vanilla's `ClientBundleTooltip` shows the pattern for an item-grid
  tooltip.
- **Slot decorations.** `GuiGraphicsExtractor.itemDecorations(Font,
  ItemStack, int, int[, String])` draws the count and durability bar on every
  slot in every screen (including the hotbar). Fabric API 0.166.2 has no
  decoration hook, so the slot icon needs one Mixin at the end of this
  method.
- **Shift.** `Minecraft.hasShiftDown()` (already used by Food HUD).
- **Vanilla sprites available:** `container/slot` (18×18 slot),
  `container/bundle/slot_background`, `container/bundle/bundle_progressbar_*`.
- **Unopened loot boxes** carry `minecraft:container_loot` instead of
  contents; the client cannot know what will generate.

### 4.1 What a pure client can and cannot do

| Capability | Client-only? |
| --- | --- |
| Contents of shulker items in your inventory, opened containers, creative tabs | Yes |
| Exact slot positions | Yes |
| Contents of a placed shulker box in the world (not opened) | No: block entity contents are not synced |
| Ender chest contents | No (Shulker Box Tooltip needs its server part for this) |
| Loot-table shulkers before generation | No |
| Item stacks hidden by a server plugin | Only what the server sends (§10, R-1) |

## 5. Definitions [Proposed]

- **Kind:** two stacks are the same kind when they are the same item
  (`ItemStack.isSameItem`), ignoring components. So all enchanted books are
  one kind, and named dirt counts with dirt. (Alternative in Q-SP3.)
- **Predominant kind:** the kind with the largest total count (sum of stack
  counts) in the box.
- **Tie rule:** among tied kinds, the one whose first stack is in the lowest
  slot index (top-left first, row by row). Deterministic and matches what the
  player sees in the grid.
- **Representative stack:** the first stack of the predominant kind, by slot
  index; its own components are used for the icon (enchantment glint, etc.).
- **Empty box:** no component, or only empty slots. No icon, no preview;
  vanilla tooltip unchanged.

## 6. Behavior [Proposed]

### 6.1 Slot icon

```
┌──────────────┐
│ ▓▓▓▓▓▓ [it]  │  [it] = predominant item at half size, top-right
│ ▓ box ▓      │
│ ▓▓▓▓▓▓       │
│ ▬▬▬▬▬▬▬▬     │  optional fill bar (Q-SP4)
└──────────────┘
```

- Drawn after the vanilla decorations on any slot showing a non-empty
  shulker box: player inventory, hotbar, containers, creative tabs.
- Representative stack rendered at 8×8 (half scale) in the top-right
  quadrant, as in the PO reference image; no count on the small icon.
- Not drawn for empty boxes.

### 6.1a Capacity bar [Approved 2026-11-25]

- Drawn where and like the vanilla bundle's fill bar: the item-bar position
  at the bottom of the slot icon (13×2 px with a black backing, filled
  width `min(1 + floor(fraction × 12), 13)`), in the bundle's colors: the
  bundle's blue while partly full, the bundle's red when full.
- `fraction = Σ(count ÷ maxStackSize) ÷ 27` over the 27 slots, clamped to
  [0, 1]; stacks with a non-positive max stack size count as full slots. It is
  an approximate normalized storage occupancy, not the number of used slots:
  one full stack of a 64-item is 1/27 (≈ 3.7 %), 27 full stacks or 27
  unstackable items are 100 %.
- Not drawn for empty or unknown boxes, and not drawn when the stack already
  shows a vanilla item bar, so a durability bar is never hidden.
- The predominant icon sits in the top-right quadrant and the bar on the
  bottom two pixels, so they never overlap.

### 6.2 Compact preview (hover)

```
┌──────────────────────────────────────┐
│ Purple Shulker Box                   │  vanilla name line (kept)
│ [ICON] Cobblestone                   │  predominant, 16×16
│        1,664 items                   │
│ [i][i][i][i][i][i]  +3               │  other kinds, largest first
│ 26/27 slots · 1,790 items · 9 kinds  │
│ Shift: full view                     │  dim hint
└──────────────────────────────────────┘
```

- First row: predominant kind (icon, name, total count).
- Second row: one icon per other kind, with its total count drawn on the
  icon like a stack count (counts above 999 shortened, e.g. `1.6k`); up to
  nine icons, then `+N`.
- Summary line: used slots out of 27, total items, number of kinds.
- Hint line `Shift: full view`, shown only when the full view is enabled.
- Other tooltip lines (custom name, lore, advanced tooltips) stay.

### 6.3 Full preview (Shift + hover)

- 9×3 grid of `container/slot` sprites in the exact slot order of the
  component; each slot shows its stack with the vanilla count, durability bar
  and glint, as in a real container.
- Empty slots are drawn empty.
- Releasing Shift returns to the compact preview on the next frame.

### 6.4 Vanilla lines

While the feature is active and the box is not empty, the vanilla
"Name xN / and N more" lines are not added (Mixin on the container
component's tooltip, scoped to shulker box items). Disabling the feature
restores them immediately.

### 6.5 Colors [Proposed, Q-SP5]

V1 uses the vanilla tooltip background. Tinting the grid with the box's dye
color is optional.

## 7. Configuration [Proposed]

| Setting | Type | Default |
| --- | --- | --- |
| `slot_icon` | boolean | true |
| `compact_preview` | boolean | true |
| `full_preview_on_shift` | boolean | true |

All LIVE. With `compact_preview` off, hover keeps the vanilla lines and
Shift still opens the full grid (if on).

## 8. Technical approach [Proposed]

- `ShulkerContents` (pure, unit-tested): from a list of up to 27 stacks,
  compute kinds, totals, predominant kind with the tie rule, used slots and
  the compact rows. Works on a small value model so it needs no game
  bootstrap.
- Read the component once per stack identity; cache the summary in a small
  bounded map keyed by the `ItemContainerContents` instance (it is immutable
  and shared between equal stacks), so hovering or redrawing 50 shulkers
  does not regroup every frame.
- Mixins (all client, no behavior change outside drawing):
  - SP-1: `GuiGraphicsExtractor.itemDecorations` TAIL: draw the slot icon.
  - SP-2: extend the existing `ItemStackTooltipMixin` to supply a shulker
    tooltip image (compact or full).
  - SP-3: skip `ItemContainerContents.addToTooltip` for shulker box items
    while the preview is active.
- Rendering via `ClientTooltipComponentCallback` with two original
  `ClientTooltipComponent`s; vanilla item renderer and sprites only.
- No startup scanning; nothing runs while the feature is inactive.

## 9. Compatibility [Proposed]

| Mod | Overlap | Proposal |
| --- | --- | --- |
| Shulker Box Tooltip | Same tooltip | Soft conflict on `shulkerboxtooltip` (5.4.3+26.3 JAR) |
| Simple Shulker Preview | Same slot icon | Soft conflict on `simpleshulkerpreview` (2.6.0-mc26.3 JAR) |
| REI / EMI / JEI | Use vanilla tooltips | Expected to work, not a V1 requirement |

Core policy applies: off by default when a conflicting mod is present,
explicit player choice wins.

## 10. Risks

- **R-1 Server item obfuscation.** Some server software can strip item
  components before sending them. If the container component is removed, the
  client sees an empty box and shows nothing (never invented data). To check
  on the PO's Paper server.
- **R-2 Tooltip size.** The full grid is 162×54 GUI px plus text; on small
  windows vanilla repositions tooltips, which may cover the box itself.
- **R-3 Slot icon readability.** 8×8 icons of similar items (e.g. ores) can be
  hard to tell apart; scale is fixed in V1.
- **R-4 Other decoration mods** drawing in the same corner may overlap.
- **R-5 Performance.** Many shulkers in view each draw an extra item; the
  summary cache keeps grouping off the frame path.

## 11. Acceptance criteria [Proposed]

- SP-AC-1 A shulker with only cobblestone shows a cobblestone slot icon in
  the inventory, a chest, the hotbar and the creative inventory.
- SP-AC-2 Empty shulkers show no icon and the vanilla tooltip.
- SP-AC-3 Mixed contents: the icon is the kind with the largest total count;
  on a tie, the kind seen first in slot order.
- SP-AC-4 Hover shows the compact preview with predominant item and total,
  other kinds largest first, `+N` beyond nine, and slots/items/kinds.
- SP-AC-5 Shift + hover shows the 9×3 grid with every stack in its exact slot
  and vanilla counts/durability/glint; releasing Shift returns to compact.
- SP-AC-6 The vanilla "Name xN" lines do not appear while the preview shows,
  and return when the feature is disabled.
- SP-AC-7 All 17 colors work.
- SP-AC-8 Works on a vanilla-protocol server with no server mod.
- SP-AC-9 Each setting applies without restart.
- SP-AC-10 en_us and pt_br for every new text.
- SP-AC-11 Food HUD tooltips, Target Info and other features unaffected.

QA: client game test in a disposable world (boxes given with commands,
inventory and chest screens, hover with and without Shift, screenshots);
manual check by the PO on the Paper server.

## 12. Deferred [Deferred]

- Chests, barrels and other container items; bundles.
- Ender chest preview (needs a server component).
- Tooltip lock or scrolling (Alt, Ctrl), dye-color tinting beyond Q-SP5.
- Icon position/scale settings, minimum-count thresholds.
- Preview while hovering a placed box in the world.

## 13. Decisions (resolved in §1.1)

- **Q-SP1 [Approved 2026-11-25]** Milestone: M1.5 (next) or keep M2?
- **Q-SP2 [Approved 2026-11-25]** Approve the tie rule (lowest first slot wins).
- **Q-SP3 [Approved 2026-11-25]** "Kind" = same item ignoring components (proposed), or same
  item and components (enchanted books split by enchantment).
- **Q-SP4 [Approved 2026-11-25]** Optional fill bar under the box, like the PO image
  (proposed: items stored ÷ (27 × max stack size of each slot's item), drawn
  like the durability bar). In V1 or Deferred?
- **Q-SP5 [Approved 2026-11-25]** Tint the preview with the box color in V1?
- **Q-SP6 [Approved 2026-11-25]** Declare Shulker Box Tooltip and Simple Shulker Preview as
  conflicts once their Fabric ids are read from their JARs.
- **Q-SP7 [Approved 2026-11-25]** Approve Mixins SP-1 to SP-3.
- **Q-SP8 [Approved 2026-11-25]** Approve the three settings in §7.

## 14. Implementation notes [Implemented 2026-11-25]

### 14.1 Code

Package `feature/shulkerpreview`:

- `ShulkerPreviewSettings`, `ShulkerPreviewFeature`: descriptor, three LIVE
  booleans, soft conflicts on `shulkerboxtooltip` and `simpleshulkerpreview`
  (ids read from the 26.3 JARs' `fabric.mod.json`), and the mode rule:
  Shift + `full_preview_on_shift` → full; else `compact_preview` → compact;
  else vanilla.
- `ShulkerSummary` (pure, generic, unit-tested): kinds, totals, predominant
  kind and tie rule, used slots, the 27 exact slots, the `+N` limit, the
  normalized capacity and the bundle bar width.
- `ShulkerContents`: reads `minecraft:container`; returns nothing for
  non-shulkers, a missing component, `minecraft:container_loot`, a component
  hidden by the item's `tooltip_display`, malformed contents and empty boxes.
  Summaries are cached by component identity (LRU, 256): components are
  immutable and a changed box carries a new one, so a cached summary can
  never be stale; the cache holds copies made by `copyInto`, never live
  stacks, and no render resources, so resource reloads do not affect it.
- `ShulkerTooltip`, `ShulkerTooltipComponent`, `ShulkerPreviewHooks`: the
  tooltip image and its original component, registered once through
  `ClientTooltipComponentCallback`.
- `ShulkerSlotDecorator`: the slot icon (representative stack at half scale,
  glint kept, no count) and the capacity bar in the bundle's colors at the
  vanilla item-bar position, skipped when the stack already shows a bar.

### 14.2 Mixins

| Id | Target | What |
| --- | --- | --- |
| SP-1 | `GuiGraphicsExtractor.itemDecorations(Font, ItemStack, int, int, String)` TAIL | Slot icon and capacity bar after vanilla's decorations |
| SP-2 | `ItemStack.getTooltipImage` RETURN (the existing Food HUD Mixin, extended) | Shulker image first, otherwise the Food HUD image; never both |
| SP-3 | `ItemStack.addToTooltip(DataComponentType, Getter, …)` HEAD | Cancels only the `minecraft:container` lines of a shulker box whose preview shows |

SP-3 targets `ItemStack` rather than `ItemContainerContents.addToTooltip`
(as first proposed): vanilla passes that method the component map, not the
stack, so the stack could not be identified there. The scope is unchanged:
one component on shulker boxes only.

### 14.3 Tests

- Unit: `ShulkerSummaryTest` (empty and malformed input, predominant, totals,
  grouping without components, tie rule, representative stack, exact slots,
  capacity for max stack sizes 1/16/64 at 0 %, ≈ 3.7 %, 50 % and 100 %,
  oversized and broken stacks, `+N`, short counts, modes) and
  `ShulkerPreviewFeatureTest` (descriptor, LIVE toggles, both conflicts and
  the explicit choice); the localization test covers `ShulkerPreviewTexts`.
- Client game test `runGameTestShulkerpreview` (disposable world): boxes of
  all 17 colors; full, mixed, half, tie, 12-kind, empty, component-less,
  named-with-lore boxes; enchanted sword, damaged pickaxe, three different
  enchanted books; tooltip lines and images read from the game; Shift and
  release; inventory, hotbar, chest (side by side) and creative screens; each
  setting and the feature toggle; GUI scales 1–4 at 1920×1080 and 1–2 at
  854×480 (larger scales are not available there).

### 14.4 Known limitations

- Placed boxes in the world, ender chests and loot boxes are not previewed
  (no client data).
- If server software strips the container component, the box looks unknown
  and only the vanilla tooltip shows.
- The 8×8 slot icon can make similar items hard to tell apart.
- The full grid uses vanilla tooltip placement, which may cover the hovered
  slot on small windows.
- Comparison runs with Shulker Box Tooltip and Simple Shulker Preview were
  not performed; the conflict default is covered by unit tests.
