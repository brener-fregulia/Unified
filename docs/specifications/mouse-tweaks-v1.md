# Inventory Mouse Tweaks V1 (M1.9)

Status labels follow [AGENTS.md](../../AGENTS.md#decision-status-labels).
Approved by the Product Owner on 2026-11-26 (§0). The sections below keep
their original labels; §0 records the resolution of each.

Faster inventory handling with mouse drags and the wheel, inspired by the
behavior of Mouse Tweaks (YaLTeR). Every item movement is an ordinary vanilla
container click sent through vanilla's own path, so the server stays the
authority. No third-party code, assets or translations are used.

## 0. Approval [Approved 2026-11-26]

- Identity: `mouse_tweaks`, M1.9, Inventory, enabled by default, LIVE,
  client-only; no custom networking or server mod.
- V1 scope and MT-AC-1 to MT-AC-17, with the safety qualifications below.
- **Q-MT1:** name "Inventory Mouse Tweaks" (pt-BR "Ajustes do Mouse no
  Inventário").
- **Q-MT2:** right-drag distribution with repeated slot visits.
- **Q-MT3:** left collection starts only when the first visited slot matches
  the cursor; otherwise vanilla behavior.
- **Q-MT4:** default wheel search order `last_to_first`.
- **Q-MT5:** wheel transfers between hotbar and main inventory in the player
  inventory screen.
- **Q-MT6:** at most 8 items per wheel event.
- **Q-MT7:** at most 24 generated vanilla clicks per client tick per screen.
- **Q-MT8:** soft conflicts with `mousetweaks`, `mousewheelie`, `itemscroller`.
- **Q-MT9:** a brief settings note that creative inventory gestures are
  excluded.
- **Safety qualifications:**
  - Validate before acting, and recheck the predicted state after every
    generated click. Stop at the first divergence.
  - Never start a multi-click sequence without enough click budget to finish
    it, and discard excess input instead of queueing it.
  - Skip any operation whose safety cannot be guaranteed, and keep vanilla
    behavior.
  - No claim of atomicity across the network.

## 1. Identity, goals and non-goals [Proposed]

| Item | Proposal |
| --- | --- |
| Feature id | `mouse_tweaks` |
| Milestone | M1.9 |
| Category | Inventory |
| Default | Enabled |
| Apply mode | LIVE |
| Side | Client only: no server mod, no custom packets |

### 1.1 Goals

- Four mouse behaviors, each switchable: right-button drag distribution,
  left-button collection, Shift + drag quick move, and wheel transfers.
- The same result in singleplayer and multiplayer, using only clicks the
  vanilla client could send itself.
- Never drop, duplicate or lose an item. When in doubt, do nothing.
- Vanilla behavior returns exactly when a behavior or the feature is off.

### 1.2 Non-goals

- Sorting, item locking, auto-refill, crafting shortcuts, trading shortcuts
  (planned separately as `inventory_sort`, `item_transfer`,
  `protected_slots`, or deferred, §13).
- The creative inventory (§5).
- Any server-side component or new packet.

## 2. Research

### 2.1 Sources and versions

| Mod | Version (Modrinth, Fabric, 26.3) | Fabric id (from the JAR's `fabric.mod.json`) | License | Notes |
| --- | --- | --- | --- | --- |
| [Mouse Tweaks](https://modrinth.com/mod/mouse-tweaks) ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/mouse-tweaks)) | `26.3-2.31-fabric` (2.31), 2026-09-15 | `mousetweaks` | BSD-3-Clause | Requires Fabric API. Its Mixin file holds a single accessor on `AbstractContainerScreen`, so its behavior is driven from screen mouse events. |
| [More Mouse Tweaks](https://modrinth.com/mod/moremousetweaks) | `2.104.0+26.3`, 2026-09-16 | `moremousetweaks` | Apache-2.0 | Requires Mouse Tweaks. |
| [Mouse Wheelie](https://modrinth.com/mod/mouse-wheelie) | `1.16.4+mc26.3`, 2026-11-22 | `mousewheelie` | Apache-2.0 | Scroll transfers, drag and more; several library dependencies. |
| [Item Scroller](https://modrinth.com/mod/item-scroller) | `0.33.1` (26.3), 2026-09-27 | `itemscroller` | LGPLv3 | Requires MaLiLib. Wheel and drag transfers. |
| [Inventory Profiles Next](https://modrinth.com/mod/inventory-profiles-next) | `fabric-26.3-2.3.8`, 2026-09-27 | `inventoryprofilesnext` | AGPL-3 | Mainly sorting and profiles. |

The study used public pages, each JAR's `fabric.mod.json`, Mixin file names,
the names of Mouse Tweaks' option keys and enum constants, and More Mouse
Tweaks' option names. No method bodies were read or copied.

### 2.2 Mouse Tweaks options (2.31)

| Option | Values seen in the JAR | Behavior (from its description) |
| --- | --- | --- |
| `RMBTweak` | on/off | Right-drag puts one item into each slot passed over |
| `LMBTweakWithItem` | on/off | Left-drag while carrying collects matching items into the cursor; with Shift, quick-moves matching items |
| `LMBTweakWithoutItem` | on/off | Shift + left-drag with an empty cursor quick-moves each stack passed over |
| `WheelTweak` | on/off | Scrolling over a stack moves items between inventory sections |
| `WheelSearchOrder` | `FIRST_TO_LAST`, `LAST_TO_FIRST` | Which matching stack is used first |
| `WheelScrollDirection` | `NORMAL`, `INVERTED`, `INVENTORY_POSITION_AWARE`, `INVENTORY_POSITION_AWARE_INVERTED` | Which wheel direction pushes and which pulls |
| `ScrollItemScaling` | `PROPORTIONAL`, `ALWAYS_ONE` | Whether a large wheel delta moves several items |

### 2.3 More Mouse Tweaks options (2.104.0)

Multiplayer and singleplayer **interaction intervals** (a delay between
generated clicks), hotbar and extra-slot scopes, matching by item type or
tags, scrolling creative and recipe-book tabs, quick crafting and quick
trading, a drop key and a matching-slots key.

## 3. Feature comparison [Proposed]

| Capability | Mouse Tweaks | More Mouse Tweaks | QoL V1 |
| --- | --- | --- | --- |
| Right-drag one item per slot | Yes | — | **V1** |
| Left-drag collect into cursor | Yes | — | **V1** |
| Shift + left-drag quick move | Yes | — | **V1** |
| Wheel push / pull | Yes | Scopes | **V1** |
| Wheel search order | Yes | — | **V1** |
| Invert wheel | Yes | — | **V1** |
| Position-aware wheel direction | Yes | — | Future |
| Proportional wheel scaling | Yes | — | **V1, fixed** (§4.4) |
| Click pacing | — | Yes (intervals) | **V1, fixed** (§6.3) |
| Match by type or tags | — | Yes | Future |
| Creative / recipe-book tab scrolling | — | Yes | Excluded |
| Quick crafting / quick trading | — | Yes | Excluded |
| Drop key, matching-slots key | — | Yes | Excluded |

## 4. Interaction semantics [Proposed]

Terms: a **visit** is the pointer entering a slot that differs from the slot
it was last over during the same drag. A **click** is one call to
`MultiPlayerGameMode.handleContainerInput` with a vanilla `ContainerInput`.
**Excluded slots** are output-only slots (crafting, furnace, merchant,
anvil, smithing, stonecutter, loom, cartography and grindstone results),
fake or inactive slots, and slots for which `allowModification` is false.

### 4.1 Right-button drag (replaces vanilla's right-drag)

- **Start:** right button pressed over a slot while the cursor holds items.
  The pressed slot gets one item at once (a vanilla right click, `PICKUP`
  button 1). Vanilla's own right-drag spread (quick craft) is not started.
- **Each visit:** if the cursor still holds items and the slot accepts one
  (empty and `mayPlace`, or the same item below its `getMaxStackSize`), one
  right click places one item.
- **Revisits:** leaving a slot and coming back is a new visit and places
  another item (Q-MT2).
- **Release, release outside a slot, or the screen closing:** nothing more is
  sent. Items stay on the cursor.
- **Empty cursor:** vanilla right click (take half) is unchanged.
- **Vanilla difference:** vanilla spreads one item per slot when the button
  is released, never twice into a slot. This behavior places items
  immediately and allows revisits.

### 4.2 Left-button collection (extends vanilla)

- **Collection starts** only if the first slot visited after the press holds
  the same item as the cursor. Otherwise vanilla's even-split drag runs
  unchanged (Q-MT3).
- **Each visit** to a slot with the same item (`ItemStack.isSameItemSameComponents`),
  while the cursor is below its maximum stack size, sends two left clicks.
  The first merges the cursor into the slot; the second picks the slot back
  up. The cursor ends with `min(max, cursor + slot)` and any remainder stays
  in the slot. Excluded slots are skipped.
- **Shift held:** each visited slot with the same item is quick-moved
  (`QUICK_MOVE`) instead.

### 4.3 Shift + left drag with an empty cursor (extends vanilla)

- Vanilla quick-moves only the pressed slot. Here each visited slot with an
  item is quick-moved (`QUICK_MOVE`), once per slot per drag.
- The destination is decided by the menu's own `quickMoveStack`, so each
  container keeps its own rules.
- Excluded slots are skipped, so dragging over a crafting result never crafts.

### 4.4 Wheel transfers (new)

Only with an empty cursor, over a non-excluded slot. Vanilla wheel actions
take precedence: if a vanilla `ItemSlotMouseAction` matches the slot (the
**bundle** scroll), vanilla handles it.

- **Sections:** the player's inventory (`slot.container` is the player
  `Inventory`) versus the other container. In the player inventory screen the
  sections are the hotbar versus the main inventory (Q-MT5).
- **Push (scroll down by default):** one item from the hovered stack goes to
  the other section. The target is the first slot there holding the same
  item with room; otherwise the first empty slot that `mayPlace` it, in the
  search order. The clicks are: left click the source (pick up), right click
  the target (place one), left click the source (put the rest back).
- **Pull (scroll up by default):** one matching item from the other section
  comes into the hovered slot, if it has room. The source is the first
  matching stack in the search order. The clicks mirror push.
- **Magnitude:** scroll deltas accumulate per screen, and each whole step of
  1.0 moves one item. A fractional or high-resolution delta keeps its
  magnitude, and reversing direction clears the accumulator, like vanilla's
  `ScrollWheelHandler`. At most **8 items per wheel event** (Q-MT6).
- **Safety:** before each three-click sequence the client checks, on its
  predicted state, that the cursor is empty and the target will accept the
  item. If a step's prediction does not match, the remaining clicks of that
  item are skipped. The cursor stays empty, or holds the stack that the
  final put-back click would have returned.

## 5. Supported screens [Proposed]

The feature acts only inside `AbstractContainerScreen` subclasses, through
the slots the menu exposes.

| Screen | Support | Notes |
| --- | --- | --- |
| Player inventory | Supported | Crafting result excluded; armor and offhand follow `mayPlace` |
| Chest, barrel, ender chest, shulker box | Supported | Shulker slots reject shulker boxes via `mayPlace` |
| Hopper, dispenser, dropper, crafter | Supported | Crafter's disabled slots are inactive |
| Furnace, smoker, blast furnace | Conditional | Fuel slot accepts only fuel; result excluded; quick-move rules are the menu's |
| Crafting table | Conditional | Grid supported; result excluded from every behavior |
| Anvil, smithing, stonecutter, loom, cartography, grindstone | Conditional | Inputs supported; result excluded |
| Villager / wandering trader | Conditional | Payment slots supported; result excluded; trade list untouched |
| Beacon, brewing stand, enchanting table | Conditional | Restricted slots follow `mayPlace` and `getMaxStackSize` |
| Horse / llama / camel inventories | Conditional | Saddle and armor slots follow `mayPlace` |
| Creative inventory | **Excluded** | It uses its own client-side slot handling and creative packets |
| Modded container screens | Conditional | Work when they extend `AbstractContainerScreen` with standard slots. Custom click handling (for example screens that override `slotClicked`) may differ. |
| Recipe book | Unaffected | Only slots are involved; the recipe book's own clicks are untouched |

## 6. Multiplayer synchronization [Proposed]

### 6.1 One path for every change

- **Clicks only.** Every movement is a vanilla click through
  `MultiPlayerGameMode.handleContainerInput(containerId, slot, button, input, player)`.
  In 26.3 this predicts the click locally (`AbstractContainerMenu.clicked`).
  It then sends `ServerboundContainerClickPacket` with the menu's `stateId`,
  hashes of the changed slots and the carried stack. The server applies the
  click with its own rules and corrects the client when they disagree.
- **No direct inventory writes.** Nothing ever writes slots or the carried
  stack directly, and no custom packets exist.
- **Never these inputs:** `THROW`, clicks outside the window (slot −999,
  which drops the cursor), `CLONE` and `SWAP`.
- **Decisions use the predicted state.** A server correction simply becomes
  the next prediction's starting point.

### 6.2 Cancellation

- A drag ends on button release, focus loss, the screen closing or the menu
  changing (`containerId` differs). Wheel sequences are sent within one event,
  so they never span a screen change.
- If `player.containerMenu` is not the screen's menu, nothing is sent.

### 6.3 Pacing

- At most **one visit's clicks per mouse event**, and at most **24 clicks per
  client tick** for each screen (Q-MT7). Further wheel steps that would
  exceed this are dropped, not queued, so input never piles up on a slow
  connection.
- The cap is above normal hand speed (a fast drag crosses only a few slots per
  tick) and keeps a long drag or a fast wheel from flooding the connection.
- Whether Paper or other servers rate-limit container clicks was not
  verified; a QA step covers it.

## 7. Compatibility [Proposed]

| Mod | Fabric id (verified in JAR) | Overlap | Proposal |
| --- | --- | --- | --- |
| Mouse Tweaks | `mousetweaks` | Same gestures; both would send clicks | Known conflict (Core policy: `mouse_tweaks` off by default while present) |
| More Mouse Tweaks | `moremousetweaks` | Requires Mouse Tweaks | Covered by the Mouse Tweaks conflict; not declared |
| Mouse Wheelie | `mousewheelie` | Wheel transfers and drag | Known conflict |
| Item Scroller | `itemscroller` | Wheel and drag transfers | Known conflict |
| Inventory Profiles Next | `inventoryprofilesnext` | Mostly sorting; overlap unverified | Not declared (Q-MT8) |

Existing QoL features:

- **Shulker Preview:** its slot decorations and Shift grid are visual only.
  Shift + drag still quick-moves; the grid tooltip may show while Shift is
  held. No conflict.
- **Zoom:** its scroll Mixin acts only when no screen is open; this feature
  acts only in container screens. No overlap.
- **Keyboard Map:** inventory mouse gestures are not key mappings; nothing to
  list.
- **Settings Workspace:** six inline settings (§8); no new screen.

## 8. Configuration [Proposed]

| Key | Type | Values | Default |
| --- | --- | --- | --- |
| `rmb_drag` | Boolean | — | ON |
| `lmb_collect` | Boolean | — | ON |
| `shift_drag` | Boolean | — | ON |
| `wheel_transfer` | Boolean | — | ON |
| `wheel_direction` | Enum | `normal` (down pushes), `inverted` | `normal` |
| `wheel_search_order` | Enum | `last_to_first`, `first_to_last` | `last_to_first` (Q-MT4) |

All settings are LIVE and take effect at the next mouse event, through the
normal auto-apply. A drag in progress keeps the settings it started with.
Strings in en_us and pt_br.

## 9. Technical approach and performance [Proposed]

- **Integration:** Fabric API `ScreenMouseEvents` (`allowMouseClick`,
  `allowMouseDrag`, `allowMouseRelease`, `allowMouseScroll` and their
  before/after events), registered per screen in `ScreenEvents.AFTER_INIT`
  only for `AbstractContainerScreen`s.
- **Mixins:** one read-only accessor for `hoveredSlot` (or the screen's slot
  lookup) and for suppressing vanilla's right-drag quick craft. Exact need
  verified at implementation.
- **Cost:**
  - Nothing runs while no container screen is open.
  - A mouse event handles only the slot under the pointer; there is no
    per-frame scanning.
  - A wheel step scans the menu's slot list once (tens of slots).
  - Drag state is a few fields and a reused slot reference. No allocation per
    mouse move, no disk I/O on mouse events.
- **Bounded:** click caps per event and per tick (§6.3); no loops over
  unbounded input.

## 10. Acceptance criteria [Proposed]

1. **MT-AC-1:** right-drag with 10 items across 5 empty slots leaves 1 in each
   and 5 on the cursor; revisiting a slot adds one more.
2. **MT-AC-2:** right-drag never places an incompatible item, never exceeds a
   slot's limit, never places into an excluded slot.
3. **MT-AC-3:** left collection fills the cursor to its maximum from matching
   slots, leaving remainders; a non-matching first slot gives vanilla's even
   split.
4. **MT-AC-4:** Shift + drag quick-moves each visited stack once, following
   the menu's rules (chest ↔ inventory, furnace fuel/input).
5. **MT-AC-5:** dragging over a crafting, furnace, anvil or merchant result
   never takes or crafts anything.
6. **MT-AC-6:** one wheel notch down moves exactly one item to the other
   section; one notch up pulls one; inverted swaps them.
7. **MT-AC-7:** four quarter-notch deltas move exactly one item; a reversal
   clears the remainder; one event moves at most 8 items.
8. **MT-AC-8:** the wheel over a bundle keeps vanilla's bundle scrolling.
9. **MT-AC-9:** nonstackable items (tools) move as whole items, never split.
10. **MT-AC-10:** no item is ever dropped, and the total item count of the
    player plus container is identical before and after every scenario.
11. **MT-AC-11:** on a dedicated test server with added latency, client and
    server inventories match after each scenario; no "desync" correction
    leaves a wrong count.
12. **MT-AC-12:** the creative inventory behaves exactly as vanilla.
13. **MT-AC-13:** each setting OFF restores the vanilla gesture at the next
    event; feature OFF restores all.
14. **MT-AC-14:** with `mousetweaks`, `mousewheelie` or `itemscroller`
    present, the feature starts off unless the player enables it.
15. **MT-AC-15:** all strings exist in en_us and pt_br.
16. **MT-AC-16:** Shulker Preview and Zoom behave as before; the full
    regression suite passes at the end of M1.9.
17. **MT-AC-17:** clicks per tick never exceed the cap during a long drag or a
    fast wheel.

## 11. QA [Proposed]

### 11.1 Automated

- **Unit tests (pure logic, no Minecraft types):**
  - visit tracking and revisits;
  - the wheel accumulator (fractional, reversal, cap);
  - section selection and search order;
  - click-sequence planning against a fake slot model: limits, `mayPlace`,
    excluded slots, nonstackables;
  - pacing caps, settings, conflicts.
- **Focused game test** `mousetweaks` in a disposable world:
  - Cursor movement and buttons come from the Fabric client game-test input
    (`setCursorPos`, `holdMouse`, `scroll`).
  - Screens: player inventory, chest, furnace, crafting table, a bundle
    and the creative inventory.
  - Item totals are counted before and after.
  - It runs against the integrated server, and the client's and server's
    inventories are compared.
- **Latency:** a dedicated-server test is not available in the current
  game-test setup. MT-AC-11 is therefore covered by comparing client and
  server state after each step, plus manual QA.
- **Regression:** Shulker Preview and Zoom scenarios at the end of M1.9, then
  the full suite once.

### 11.2 Manual (Windows 11 and CachyOS, Prism, production JAR)

1. Each of the four gestures in the player inventory, a chest, a shulker
   box, a furnace, a crafting table, an anvil and a villager.
2. Fast drags and fast scrolling with a high-resolution wheel or touchpad.
3. Release outside the window, close the screen mid-drag, Alt-Tab mid-drag.
4. On the Paper server, with a disposable chest of test items in a
   test area, not personal storage: all gestures. Watch for kicks,
   corrections and item counts.
5. Creative mode: vanilla behavior.
6. Turn each setting off and on in the workspace while a chest is open
   elsewhere; the next gesture follows the new setting.
7. Português texts.

## 12. Open decisions for Product Owner approval

- **Q-MT1 [Open]:** feature name in the UI: "Mouse Tweaks" (familiar) or
  "Inventory Mouse Tweaks" / "Arrastar e rolar" (avoids the other mod's
  name). Recommended: "Inventory Mouse Tweaks".
- **Q-MT2 [Proposed]:** right-drag replaces vanilla's right-drag spread and
  allows revisits.
- **Q-MT3 [Proposed]:** left collection starts only when the first visited
  slot matches the cursor; otherwise vanilla's even split.
- **Q-MT4 [Proposed]:** wheel search order default `last_to_first`, matching
  Mouse Tweaks' default.
- **Q-MT5 [Open]:** in the player inventory screen, wheel sections are hotbar
  ↔ main inventory (proposed), or wheel is disabled there.
- **Q-MT6 [Proposed]:** at most 8 items per wheel event.
- **Q-MT7 [Proposed]:** at most 24 generated clicks per client tick per
  screen, excess dropped.
- **Q-MT8 [Proposed]:** soft conflicts with `mousetweaks`, `mousewheelie`,
  `itemscroller`; not with `inventoryprofilesnext`.
- **Q-MT9 [Open]:** whether to show a one-line hint in the panel that the
  creative inventory is excluded.

## 13. Deferred features [Deferred]

Position-aware wheel direction, match by type or tags, hotbar/extra-slot
scopes, creative inventory support, quick crafting and quick trading, a drop
key, recipe-book or creative-tab scrolling, configurable click pacing.

## 14. Implementation notes [Implemented 2026-11-26]

### 14.1 Code

- `feature/mousetweaks/MouseTweaksSettings`: descriptor, six LIVE settings,
  three conflicts.
- `MouseTweaksConfig`: the settings as plain fields, rebuilt only when the
  snapshot changes. A gesture reads it once, at the press.
- `WheelAccumulator` (fractional steps, reversal, 8-item cap) and
  `ClickBudget` (24 clicks per tick per screen, whole sequences only): pure
  Java, unit-tested.
- `InventoryGestures`: the four gestures of one screen; `MouseTweaksHooks`
  attaches it in `ScreenEvents.AFTER_INIT` to every `AbstractContainerScreen`
  except `CreativeModeInventoryScreen`.
- `mixin/ContainerScreenSlotsAccessor`: read-only invoker for
  `getHoveredSlot(double, double)` and accessor for `itemSlotMouseActions`. No
  injection.
- `FeaturePanel`: an optional `qol_unified.feature.<id>.note` line under the
  description (Q-MT9: "Not active in the creative inventory").

### 14.2 Input ownership

| Gesture | Press | Drag | Release |
| --- | --- | --- | --- |
| Right-drag (cursor has items, pressed slot accepts one) | Ours: one item, vanilla never sees it | Ours | Ours |
| Left press on a matching stack | Ours (pending) | Ours: collection | Ours: a plain left click if the pointer never left the slot |
| Shift + left with a matching cursor | Ours: quick move | Ours | Ours |
| Shift + left with an empty cursor | Vanilla (its own quick move) | Ours, vanilla also receives it | Vanilla |
| Anything else | Vanilla | Vanilla | Vanilla |

- A new press always resets the previous gesture, so a release missed
  through focus loss cannot leak into the next gesture.
- Focus loss, removing the screen, re-initializing it (Fabric recreates
  screen events on every init) and a menu change cancel the gesture.

### 14.3 Click safety

- Every click goes through `MultiPlayerGameMode.handleContainerInput`.
- After each click the predicted menu is compared with the expected counts.
  The first difference stops the gesture, which then swallows the rest of
  the drag without sending anything.
- Wheel transfers reserve their two or three clicks before the first one.
- Collection runs only when the cursor plus the slot fits in one stack, so
  its second click always empties the slot.
- Excluded:
  - result, fake and inactive slots, and slots whose `allowModification` is
    false;
  - armor, offhand and crafting slots for the wheel;
  - `THROW`, `CLONE`, `SWAP` and clicks outside the window.
- Network transfers are not atomic: the server can still reject a click, and
  vanilla's state-id resynchronization then corrects the client.

### 14.4 Technical findings during implementation

- **SDL mouse buttons.** In 26.3 `MouseButtonEvent.button()` is an SDL button:
  `MOUSE_BUTTON_LEFT = 1`, `MOUSE_BUTTON_RIGHT = 3`. Vanilla maps them to
  container-click buttons 0 and 1 in `getContainerClickButton`. Gestures
  compare event buttons with the SDL constants and send 0/1 as click buttons.
- **Fabric game-test input** sends mouse presses without modifiers, so the
  Shift scenarios send the button through the same `MouseHandler.onButton`
  path with `MOD_SHIFT`.
- **Collection overflow.** When cursor plus slot exceeds one stack, two plain
  clicks cannot take only part of the slot. Such slots are skipped instead of
  partially merged.

### 14.5 Validation

- **Unit tests:** `MouseTweaksLogicTest`, `MouseTweaksSettingsTest`.
- **Game test** `mousetweaks`, in a disposable world. Real cursor positions
  and buttons drive the player inventory, chest, crafting table and furnace.
  After every scenario, item totals are checked and the client's menu is
  compared slot by slot with the integrated server's. Covered:
  - right-drag with revisits, the crafting result and an incompatible slot;
  - collection, including an overflow skip, and vanilla's even split;
  - Shift + drag in a chest;
  - the crafting result never crafted;
  - wheel push and pull, quarter notches, the 8-item cap, the same-tick budget
    (8 then 0), nonstackables, a bundle, inverted direction, both search
    orders;
  - furnace input/fuel routing;
  - cancellation by closing mid-drag, feature OFF (vanilla right-drag),
    creative wheel unchanged.

### 14.6 Known limitations

- Collection skips a stack that would overflow the cursor.
- Latency and Paper rate limits are manual QA (not reproducible with the
  integrated server).
- Modded screens with custom click handling are untested.
