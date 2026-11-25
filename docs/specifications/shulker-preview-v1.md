# Shulker Preview V1

Status labels follow [AGENTS.md](../../AGENTS.md#decision-status-labels).
Specification and discovery only; no code. Items marked **[Approved]** are
the Product Owner's stated direction of 2026-11-25; everything else awaits
approval.

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

## 2. Feature identity [Proposed]

| Item | Proposal |
| --- | --- |
| Feature id | `shulker_preview` (already in the catalog) |
| Category | Inventory (`inventory`) |
| Milestone | M1.5 (catalog says M2; see Q-SP1) |
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
| Shulker Box Tooltip | Same tooltip | Conflict; ids must be read from its JAR (Q-SP6) |
| Simple Shulker Preview | Same slot icon | Conflict; id from its JAR (Q-SP6) |
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

## 13. Open decisions for the Product Owner

- **Q-SP1 [Open]** Milestone: M1.5 (next) or keep M2?
- **Q-SP2 [Open]** Approve the tie rule (lowest first slot wins).
- **Q-SP3 [Open]** "Kind" = same item ignoring components (proposed), or same
  item and components (enchanted books split by enchantment).
- **Q-SP4 [Open]** Optional fill bar under the box, like the PO image
  (proposed: items stored ÷ (27 × max stack size of each slot's item), drawn
  like the durability bar). In V1 or Deferred?
- **Q-SP5 [Open]** Tint the preview with the box color in V1?
- **Q-SP6 [Open]** Declare Shulker Box Tooltip and Simple Shulker Preview as
  conflicts once their Fabric ids are read from their JARs.
- **Q-SP7 [Open]** Approve Mixins SP-1 to SP-3.
- **Q-SP8 [Open]** Approve the three settings in §7.
