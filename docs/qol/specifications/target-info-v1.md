# Target Info V1 (M1.4)

Status labels follow [AGENTS.md](../../../AGENTS.md#decision-status-labels).
Approved by the Product Owner on 2026-11-23 (§0). Proposals not listed in §0
keep their label.

A client-side overlay that names the block or entity under the crosshair and
shows a small amount of useful context, inspired by the behavior of Jade and
WTHIT (themselves descendants of WAILA). No third-party code, textures or
translations are used.

## 0. Approval [Approved 2026-11-23]

- Identity: `target_info`, HUD & Information, M1.4, enabled by default, LIVE,
  100% client-side; no server mod, packets or gameplay changes.
- Scope: every row marked **V1** in §3. TI-AC-1 to TI-AC-17 approved, with
  the clarifications below.
- Q-TI1 approved. Q-TI2 approved.
- Q-TI3: smooth progress through a narrowly scoped, read-only accessor Mixin.
- Q-TI4: on tier failure show the status mark and a short requirement text,
  only when a reliable minimum tier is known; otherwise no tier text.
- Q-TI5: fixed top-center, moved below boss bars.
- Q-TI6: hearts up to 10 hearts (max health 20); numeric above.
- Q-TI7: players show the name only, no health.
- Q-TI8: friendly mod display name, namespace as fallback.
- Q-TI9: Jade and WTHIT conflicts, after confirming their Fabric ids from the
  published JARs.
- Q-TI10: keep the eight boolean settings of §9, all LIVE.
- Q-TI11: Future rows stay [Deferred].
- Indicator rules: green check when the held tool suits the shown
  mining/harvesting behavior; red cross only when it fails a verified
  correct-drops requirement; no mark when no requirement exists or it cannot
  be determined. A hand-harvestable block is never marked wrong just because
  a faster tool exists. The game's own tool checks are the final authority;
  a named tier never implies a custom tool's suitability.

## 1. Feature identity [Approved 2026-11-23]

| Item | Proposal |
| --- | --- |
| Feature id | `target_info` (replaces the placeholder `inspector` in the [feature catalog](../product/feature-catalog.md)) |
| Category | HUD & Information (`hud_information`) |
| Default | Enabled |
| Lifecycle | LIVE enable/disable (rendering-only; nothing to restore) |
| Side | 100% client-side; no packets, no server component, no gameplay change |

### 1.1 Goals [Approved 2026-11-23]

- G-1 Identify the targeted block or entity, with icon, localized name and
  source namespace.
- G-2 Tell the player which tool type breaks the block well, whether the held
  item obtains its drops, and the required tier, using the game's own rules.
- G-3 Show mining progress on the targeted block.
- G-4 Show a few cheap, client-known states (honey level, redstone, crops,
  and similar).
- G-5 Show entity health and, for tamed animals, the owner when resolvable.
- G-6 Look and feel close to Jade: compact, top-center, adaptive size.

### 1.2 Non-goals [Approved 2026-11-23]

- Anything that needs data the server does not send to a vanilla client
  (container contents, furnace progress, bee occupants, hidden NBT).
- A public plugin API for other mods.
- Network requests of any kind (profile lookups, version checks).
- Exhaustive Jade parity; each extra provider must justify itself.

## 2. Sources and versions researched

| Source | Version | Status |
| --- | --- | --- |
| Jade (Fabric) | 26.3.5+fabric, released 2026-10-02 ([Modrinth](https://modrinth.com/mod/jade/versions)) | Latest 26.3 release found; license CC BY-NC-SA 4.0 |
| WTHIT (Fabric/Quilt) | 21.0.0 for 26.3, 2026-11-08 ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/wthit/files/all)) | Latest 26.3 release found |
| WAILA | Historical only | Concepts, no version researched |
| Minecraft | 26.3 (`minecraft-merged-deobf-26.3.jar`, inspected with `javap`) | Verified signatures cited below |

An unofficial "experimental" WTHIT 26.3 port also exists on GitHub; it is
not the upstream project and is ignored.

Jade behavior below comes from public feature descriptions and the author's
general knowledge of the mod; rows marked **unverified** were not checked
against the 26.3 build. Fabric mod ids `jade` and `wthit` are the commonly
documented ids and must be confirmed from each JAR's `fabric.mod.json`
before a conflict declaration ships (Q-TI9).

## 3. Feature comparison matrix

Complexity: S (small), M (medium), L (large). "Server" means a server-side
component (Jade/WTHIT on the server) is needed for reliable results.

| # | Feature | Jade behavior | 26.3 client data | Server | Cx | Rec. |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | Block name and icon | Confirmed | `Minecraft.hitResult`, block state, `Block.getName()`, pick item | No | S | **V1** |
| 2 | Mod attribution | Confirmed (mod name from loader) | Registry namespace; mod name via Fabric Loader metadata | No | S | **V1** |
| 3 | Harvest tool icons | Confirmed | `BlockTags.MINEABLE_WITH_*`, `SWORD_EFFICIENT` | No | M | **V1** |
| 4 | Correct-tool / tier check | Confirmed | `ItemStack.isCorrectToolForDrops`, `BlockState.requiresCorrectToolForDrops`, `INCORRECT_FOR_*_TOOL` tags | No | M | **V1** |
| 5 | Breaking progress | Confirmed | `MultiPlayerGameMode` (see §5.3) | No | S–M | **V1** |
| 6 | Block state properties | Confirmed (generic list, opt-in) | Full block state is synced | No | S | Future (generic dump); V1 only curated providers |
| 7 | Redstone details | Confirmed | Block-state properties; comparator output is server-side | No (states) | S | **V1** (subset) |
| 8 | Container contents | Confirmed, needs Jade on server | Not synced (`ChestBlockEntity` has no update packet) | **Yes** | — | **Exclude** |
| 9 | Furnace progress | Confirmed, needs server | Not synced (`AbstractFurnaceBlockEntity` has no update packet) | **Yes** | — | **Exclude** |
| 10 | Crop growth | Confirmed | `AGE` properties | No | S | **V1** |
| 11 | Fluid info | Confirmed (optional) | Fluid state from a fluid-mode ray cast | No | M | Future |
| 12 | Beehive honey | Confirmed | `BeehiveBlock.HONEY_LEVEL` property, `MAX_HONEY_LEVELS` | No | S | **V1** |
| 13 | Beehive occupants | Confirmed, needs server | `BeehiveBlockEntity.getOccupantCount()` exists but the block entity is not synced; client value is always 0 | **Yes** | — | **Exclude** |
| 14 | Entity health | Confirmed | `LivingEntity` health / max health are synced entity data | No | S | **V1** |
| 15 | Tamed owner | Confirmed | `TamableAnimal.DATA_OWNERUUID_ID` synced; name only via tab list | No (partial) | M | **V1** (see §7.2) |
| 16 | Villager profession / level | Confirmed (unverified detail) | Villager data is synced | No | S | Future |
| 17 | Entity armor / equipment | Confirmed (unverified detail) | Visible equipment is synced | No | M | Future |
| 18 | Spawner mob | Confirmed | `SpawnerBlockEntity` has an update packet | No | M | Future |
| 19 | Status effects on entity | Unverified | Only partially synced (particles/colour) | Mostly | — | Exclude |
| 20 | Overlay theming / position | Confirmed, extensive | — | No | M | V1 minimal (§8) |
| 21 | Plugin API | Confirmed | — | — | L | **Exclude** (internal only) |
| 22 | Item frame / armor stand content | Confirmed (unverified detail) | Synced entity data | No | S | Future |

## 4. Minecraft 26.3 data availability (verified with `javap`)

- **Target.** `Minecraft.hitResult` (block, entity or miss) and
  `Minecraft.crosshairPickEntity` are computed every frame by vanilla
  (`GameRenderer.pick`). Reusing them means no extra ray cast. Vanilla's pick
  ignores fluids; fluid targets need a separate cast (hence Future).
- **Block-state properties** are always synced to clients; every value in
  §6 comes from them.
- **Block entities.** `BlockEntity.getUpdatePacket()` is null unless a class
  overrides it. `ChestBlockEntity` and `SpawnerBlockEntity` override it;
  `AbstractFurnaceBlockEntity` and `BeehiveBlockEntity` do not. Chests
  sync only what the client renders (not contents): contents arrive only
  through an open container menu.
- **Entities.** Health, custom name, tame flag and owner reference are synced
  `EntityDataAccessor`s. `OwnableEntity.getOwner()` resolves only entities
  loaded on the client.
- **Players.** `ClientPacketListener.getPlayerInfo(UUID)` returns tab-list
  entries, a local lookup with no network request.

## 5. Tool and tier semantics [Proposed]

### 5.1 Three separate questions

| Question | Game rule used | Never inferred from |
| --- | --- | --- |
| Q1 **Faster?** Is the held item faster than the hand on this block? | `ItemStack.getDestroySpeed(state) > 1.0` (from the `minecraft:tool` component, `Tool.getMiningSpeed`) | Item names |
| Q2 **Drops?** Does breaking yield the block's drops? | If `BlockState.requiresCorrectToolForDrops()` is false: always yes. Else `ItemStack.isCorrectToolForDrops(state)` (`Tool.isCorrectForDrops`) | A material table |
| Q3 **Tier.** Which tier is needed? | The lowest tier whose `INCORRECT_FOR_<tier>_TOOL` tag does not contain the block (wooden, gold, stone, copper, iron, diamond, netherite) | Hardcoded block lists |

Faster breaking and correct drops are reported separately because they
differ: on cobweb a sword is faster and drops string, while shears drop the cobweb itself;
leaves need no tool for drops but shears and hoes are faster.

### 5.2 Display

- **Recommended tool icons.** One vanilla item icon per category whose tag
  contains the block: pickaxe (`MINEABLE_WITH_PICKAXE`), axe, shovel, hoe,
  sword (`SWORD_EFFICIENT`), plus shears for blocks the shears item mines
  faster (derived from the shears' tool component, not a list). Several
  icons sit side by side, e.g. leaves → hoe + shears.
- **Icon material.** Each icon uses the minimum tier from Q3 (iron pickaxe
  icon for diamond ore), or wooden when no tier is required.
- **Mark on the held item** (one mark, right of the icons):
  - ✔ green: the held item is a recommended tool and passes Q2.
  - ✘ red: the block requires a correct tool and the held item fails Q2
    (stone with bare hand, obsidian with an iron pickaxe). The tooltip text
    names the required tier when the category matched but the tier failed.
  - No mark: no tool needed and none recommended.
- **No tool information** when the block is in no tool tag and does not
  require a correct tool (torch, flowers) or breaks instantly (destroy
  speed 0).

Expected results: grass block → shovel; stone → pickaxe, ✘ by hand;
obsidian → diamond pickaxe, ✘ with iron, ✔ with diamond or netherite.

Modded blocks are handled automatically when the mod uses the vanilla tags
and tool component; others show no recommendation rather than a guess.

### 5.3 Mining progress

- `MultiPlayerGameMode.isDestroying()` and `getDestroyStage()` are public;
  `destroyBlockPos` and `destroyProgress` are private.
- `getDestroyStage()` gives 10 steps only. A smooth bar needs read access to
  `destroyProgress` and `destroyBlockPos` through an accessor Mixin
  (read-only, no behavior change). Q-TI3 decides.
- Shown only when `isDestroying()` and the destroy position equals the
  targeted position; hidden the same frame otherwise.
- **Multiplayer.** The client predicts progress itself and the server
  decides the actual break. Server-side differences (lag, plugins changing
  speed, attribute mismatches) can make the bar finish early or late. It is
  a client prediction and labeled as such in the docs, never "server
  progress".
- Creative mode and instant breaks show no bar.

## 6. Block-state providers [Proposed]

All values read directly from synced block-state properties.

| Provider | Blocks | Shows | V1? |
| --- | --- | --- | --- |
| Honey | beehive, bee nest | `Honey 3/5` (`HONEY_LEVEL`/`MAX_HONEY_LEVELS`) | V1 |
| Redstone wire | redstone dust | `Power 7` (`POWER`: the wire's own signal level) | V1 |
| Lever | lever | On / Off (`POWERED`) | V1 |
| Button | buttons | Pressed / Released (`POWERED`) | V1 |
| Repeater | repeater | Delay 1–4 ticks, Powered, Locked | V1 |
| Comparator | comparator | Mode compare/subtract, Powered (not output strength: stored in the server-side block entity) | V1 |
| Lamp | redstone lamp | On / Off (`LIT`) | V1 |
| Crops | crops with `AGE`, nether wart, cocoa, sweet berries | `Growth 57%` or `Mature` | V1 |
| Composter | composter | `Level 4/8` | Future |
| Cauldron | water / powder snow cauldron | `Level 2/3` | Future |
| Doors, trapdoors, gates | | Open / Closed | Future (visible in-world) |
| Facing | any | Direction | Exclude (low value) |

**Power wording.** "Power" in V1 is only the targeted block's own state
(wire `POWER`, `POWERED`/`LIT` flags). Received signal
(`Level.getBestNeighborSignal`) could be computed from client states but may
differ from the server for unloaded or plugin-altered neighbors; it is not
shown in V1 and never labelled as the same thing.

## 7. Entity information [Proposed]

### 7.1 Living entities

- Name: custom name if set, else the localized type name.
- Source namespace of the entity type.
- Health as heart icons (vanilla HUD heart sprites), half hearts honored.
  Above 20 max health (10 hearts): numeric `❤ 34 / 40` instead of rows.
- Max health is shown in the numeric form only.

### 7.2 Owners

- Owner reference (UUID) is synced for `TamableAnimal`, and other
  `OwnableEntity` types.
- Name resolution, local only: (1) the owner entity if loaded, (2) the tab
  list `getPlayerInfo(uuid)`. Otherwise show `Owner: unknown player`.
- A UUID is never displayed. No profile or web lookup.
- Untamed animals show no owner line.

### 7.3 Other entities

- Items on ground: item name and count. Minecarts, boats, item frames,
  armor stands: name and namespace only in V1.
- **Players (privacy-conservative):** display name only; no health, no
  equipment, no ping (Q-TI7).

## 8. Visual layout [Proposed]

```
        ┌─────────────────────────────────────────┐
        │ [icon]  Stone                 ⛏ ✘       │
        │         Minecraft                        │
        │         Honey 3/5                        │  (only when present)
        │▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔                         │  progress bar, 1 px
        └─────────────────────────────────────────┘
```

- Horizontally centered, 4 GUI px below the top edge; does not overlap the
  boss bar (moves below it when bars are present).
- Translucent dark background (about 75% opacity) with a 1 px subtle
  border, drawn with plain fills (no copied textures).
- 16 px item icon left; name white; namespace on its own line in blue
  italics, the conventional mod-name style; tool icons and mark right.
- Width and height follow the content; no empty rows.
- Hidden when the target is air or a miss, while a screen is open, when
  the HUD is hidden (F1) or the debug screen is shown.
- GUI scales 1–4: everything scales with the GUI; long names truncate with
  an ellipsis at 40% of the screen width.

## 9. Configuration [Proposed]

Uses existing Core setting descriptors; all LIVE.

| Setting | Type | Default |
| --- | --- | --- |
| `show_blocks` | boolean | true |
| `show_entities` | boolean | true |
| `show_tools` | boolean | true |
| `show_mining_progress` | boolean | true |
| `show_block_details` (honey, redstone, crops) | boolean | true |
| `show_health` | boolean | true |
| `show_owner` | boolean | true |
| `show_source` (namespace line) | boolean | true |

No position setting in V1 (top-center fixed; Q-TI5).

## 10. Technical architecture [Proposed]

Internal only, within the existing modular monolith:

- `TargetInfoFeature`: descriptor and settings; one HUD render callback
  registered at client init (Fabric HUD API), returning immediately when
  disabled.
- `TargetResolver`: reads `hitResult` / `crosshairPickEntity`; returns a
  small `Target` record; no ray casts.
- Providers, an explicit ordered list in code (no discovery):
  `BlockInfoProvider`, `ToolInfoProvider`, `MiningProgressProvider`,
  `BlockStateProvider`, `EntityInfoProvider`. Each adds lines to a reusable
  `TooltipModel`.
- `TargetInfoRenderer`: measures and draws the model.
- Pure logic (tool evaluation, line building, layout math) kept separate so
  it can be unit-tested; game tests cover live rendering.
- Optional read-only accessor Mixin for smooth progress (Q-TI3). No other
  Mixins expected.

## 11. Performance considerations [Proposed]

- Reuse vanilla's per-frame hit result; never cast again.
- Rebuild the model only when the target identity changes (position +
  block state, or entity id) or once per client tick for changing values
  (health, progress, states); render from the cached model every frame.
- Cache per block state: name component, namespace, tool categories, tier.
  The cache is bounded and cleared on disconnect and resource reload.
- No allocation per frame beyond the draw calls; item icons use vanilla's
  item renderer.
- Nothing is scanned at startup; caches fill lazily.
- No FPS claims; a profiling pass (spark or the debug pie) is part of
  implementation QA.

## 12. Compatibility [Proposed]

| Mod | Fabric id | Conflict | Proposal |
| --- | --- | --- | --- |
| Jade | `jade` (to confirm, Q-TI9) | Duplicate overlay at the same place | Declare as known conflict; by Core policy `target_info` auto-disables while Jade is present unless the player re-enables it |
| WTHIT | `wthit` (to confirm) | Duplicate overlay | Same |
| Other inspectors (e.g. older WAILA forks) | — | — | Not declared until found |

No `breaks` entry in `fabric.mod.json`; the player keeps the choice.

## 13. Acceptance criteria [Proposed]

- TI-AC-1 Looking at stone, grass block, oak log, torch shows the correct
  localized name and icon in en_us and pt_br.
- TI-AC-2 Vanilla blocks show `Minecraft`; a dev-fixture modded block shows
  its mod name.
- TI-AC-3 Stone → pickaxe; grass block → shovel; oak log → axe; torch and
  flowers → no tool information.
- TI-AC-4 Stone: ✘ by hand, ✔ with a wooden pickaxe. Obsidian: ✘ with iron,
  ✔ with diamond and netherite. Iron ore: ✘ with wooden, ✔ with stone.
- TI-AC-5 Leaves show both hoe and shears; cobweb shows sword and shears.
- TI-AC-6 Progress bar appears only while breaking the targeted block,
  reaches full on break and disappears the same frame breaking stops or the
  target changes; breaking time is unchanged with the feature on or off.
- TI-AC-7 Beehive honey shows `0/5` to `5/5`; no bee count is shown.
- TI-AC-8 Redstone wire shows its power 0–15; lever, button, lamp states
  and repeater/comparator properties match the block state.
- TI-AC-9 Wheat shows growth from 0% to `Mature`.
- TI-AC-10 A sheep shows name and hearts; a tamed wolf shows its owner's
  name when the owner is online; untamed wolf has no owner line; no UUID is
  ever shown.
- TI-AC-11 Air, sky and open screens show nothing; F1 hides the overlay.
- TI-AC-12 Readable, centered, inside the screen at GUI scales 1–4 and
  854×480 to 3440×1440; no overlap with boss bars.
- TI-AC-13 Works on a vanilla-protocol server with no server mod; no packet
  is sent by the feature.
- TI-AC-14 Every setting applies without restart; disabling the feature
  removes the overlay immediately.
- TI-AC-15 All text uses translation keys in en_us and pt_br.
- TI-AC-16 With Jade installed, the conflict is reported and no duplicate
  overlay appears by default.
- TI-AC-17 Food HUD, Chat Heads and Keyboard Map game tests still pass.

## 14. QA checklist [Proposed]

Automated (disposable `build/run-gametest-*` folders): a game test places
the blocks above in a test world, aims the camera at each, and captures
screenshots at GUI scales 1–4; one run with Jade added through an optional
`-PjadeJar` property for the comparison/conflict check.

Manual (Product Owner, Prism and the Paper server; no server, plugin,
authentication or world changes):

1. Look at stone, grass, logs, ores, obsidian, leaves, torch; check icons
   and ✔/✘ with several tools.
2. Break blocks; watch the bar; stop midway; switch targets.
3. Find a beehive; check honey; tame a wolf; check owner with and without
   the owner online.
4. Place redstone dust, lever, button, repeater, comparator, lamp.
5. Toggle every setting; change GUI scale and language.
6. Compare side-by-side with Jade 26.3.5 in a disposable profile.
7. Confirm Food HUD, Chat Heads and Keyboard Map still work.

## 15. Decisions (the resolution of each is recorded in §0)

- **Q-TI1 [Approved 2026-11-23]** Approve id `target_info` (replacing `inspector`),
  milestone M1.4, default enabled.
- **Q-TI2 [Approved 2026-11-23]** Approve the V1 scope in §3 (rows marked V1).
- **Q-TI3 [Approved 2026-11-23]** Smooth progress via a read-only accessor Mixin, or
  10-step progress from `getDestroyStage()` without a Mixin.
- **Q-TI4 [Approved 2026-11-23]** Tool marks: ✔/✘ glyphs only, or also a short text
  ("Needs diamond") on tier failure.
- **Q-TI5 [Approved 2026-11-23]** Fixed top-center position in V1, or add a vertical
  offset setting.
- **Q-TI6 [Approved 2026-11-23]** Heart rows vs numeric health threshold (proposed: >10
  hearts → numeric).
- **Q-TI7 [Approved 2026-11-23]** Player targets: name only (proposed), or also health.
- **Q-TI8 [Approved 2026-11-23]** Show the mod display name (Fabric Loader metadata) or the
  raw namespace.
- **Q-TI9 [Approved 2026-11-23]** Confirm Jade/WTHIT mod ids from their JARs and approve
  the soft conflict declaration.
- **Q-TI10 [Approved 2026-11-23]** Whether the setting list in §9 is small enough, or
  merge `show_health`/`show_owner` into one entity-details toggle.
- **Q-TI11 [Deferred]** Fluids, villagers, equipment, spawners, composter,
  cauldron, generic state dump.

## 16. Implementation notes [Implemented 2026-11-23]

### 16.1 Code

Package `feature/targetinfo`:

- `TargetInfoSettings`, `TargetInfoFeature`: descriptor, eight LIVE
  booleans, Jade and WTHIT conflicts; the HUD element is registered once on
  the first enable (`TargetInfoHooks`, after the vanilla boss bar element)
  and returns at once while the feature is inactive.
- Pure, unit-tested rules: `ToolAdvice` (§5), `BlockDetails` (§6),
  `EntityDetails` (health and owner, §7), `OverlayRules` (suppression, boss
  bar offset, progress gating), `DetailLine`.
- `TargetResolver`: reads `Minecraft.hitResult` only (no ray cast); probes
  the vanilla tools of each category and tier with `getDestroySpeed` and
  `isCorrectToolForDrops`, cached per block state (LRU, 256 entries, cleared
  when the level changes). Mod names come from Fabric Loader metadata, with
  the namespace as fallback.
- `TargetInfoRenderer`: rebuilds the view when the game tick, the target
  (block position or entity) or the settings change; mining progress is read
  every frame. Fills, vanilla item icons, vanilla heart sprites and font
  glyphs only; no new textures.
- Fabric mod ids verified from the published JARs' `fabric.mod.json`:
  Jade 26.3.5+fabric → `jade`; WTHIT 21.0.0 (26.3) → `wthit`.

### 16.2 Mixins (read-only accessors, no injections)

| Id | Target | Reads |
| --- | --- | --- |
| TI-1 | `MultiPlayerGameMode` | `destroyProgress`, `destroyBlockPos` |
| TI-2 | `BossHealthOverlay` | `events` (only its size) |

TI-2 is the read-only minimum for the approved "adjusting for boss bars"
rule, because vanilla exposes no boss-bar count. **[Approved 2026-11-23]** by
the Product Owner after implementation: it must stay a read-only accessor that
reads only the size of `events`, and stays covered by the boss-bar game test
and `DetailsTest.overlaySitsBelowBossBars`.

### 16.3 Tool semantics as built

- A category is shown when its best vanilla tool breaks the block faster
  than the hand, or, for blocks that require a correct tool, when any of its
  tiers passes the drop check. Its icon is the lowest passing tier (wooden
  when no tier is required; shears have none).
- Mark: blocks requiring a correct tool get ✔ or ✘ from
  `held.isCorrectToolForDrops(state)`, the final authority also for modded
  tools. Hand-harvestable blocks get ✔ only when the held item is faster,
  never ✘.
- "Requires: <tool>" appears only after a failed check with exactly one
  correct category (cobweb, where sword and shears both pass, shows none).
- Verified results (game test): stone → wooden pickaxe; grass → shovel;
  oak log → axe; iron ore → stone pickaxe; diamond ore → iron pickaxe;
  obsidian → diamond pickaxe (✘ with iron, ✔ with diamond and netherite);
  oak leaves → hoe, sword and shears; cobweb → sword and shears (✘ by hand);
  torch and poppy → nothing.

### 16.4 Tests

- Unit: `ToolAdviceTest`, `DetailsTest`, `TargetInfoFeatureTest` (LIVE
  toggles, Jade/WTHIT conflict default and explicit choice); the
  localization test covers every `TargetInfoTexts` key.
- Client game test `runGameTestTargetinfo` (disposable world): every
  block, tool and state above, smooth progress (a new value each tick) and
  its cancellation, Creative, honey 0–5, redstone, wheat and nether wart,
  sheep hearts, tamed wolf owner, unknown owner, iron golem numeric health,
  sky, F1, open screen, two boss bars, GUI scales 1–4 at 1920×1080, every
  setting and the feature toggle.
- Optional `runGameTestTargetinfoJade -PjadeJar=<path>`: Jade loaded, the
  feature starts disabled by the conflict, the same scenes with Jade alone,
  then with both after an explicit enable.

### 16.5 Comparison with Jade 26.3.5 (screenshots, same scenes)

- Matched: top-center panel, icon left, name, blue italic mod name, tool
  icons right with a check or cross, the same tool set on leaves (hoe,
  sword, shears), hearts for small mobs, owner line, honey level.
- Intentional differences: ours adds a short "Requires: Diamond Pickaxe"
  line on a failed check; the mod name is the second line (Jade puts it
  last); a 4 px top margin; the mark is beside the icons rather than over
  them; mobs above 10 hearts show numbers (Jade draws hearts).
- Not comparable: in singleplayer Jade also runs on the integrated server
  and shows the bee count ("Bees: 0"); ours never shows it (§3 row 13).
- Unverified: Jade on a server without Jade; WTHIT side by side.

### 16.6 Known limitations

- Multiplayer mining progress is the client's prediction; the server
  decides the actual break.
- Owner names resolve only when the owner is loaded nearby or in the player
  list; otherwise "unknown player".
- Thin blocks (redstone dust, young crops) are targeted only where vanilla's
  outline shape is hit, as with the vanilla crosshair.
- Entities have no icon; tamed wolves (40 health) show numeric health.
- Not covered automatically: a real Paper server, other mods' blocks, and
  WTHIT side by side.
