# Food HUD V1 (M1.1)

Status labels follow [AGENTS.md](../../AGENTS.md#decision-status-labels).

**[Approved 2026-11-22]** The technical lead approved this specification,
including the configuration and acceptance criteria FH-AC-1 to FH-AC-18,
with the clarifications recorded in §10. Implementation notes are in §11.

## 1. Goal and approved requirements

**[Approved 2026-11-22]** Food HUD V1 reproduces the AppleSkin user
experience as closely as practical, with an independent implementation and
100% client-side operation.

| Id | Requirement [Approved 2026-11-22] |
| --- | --- |
| **R-1** | Feature id `food_hud`, category HUD & Information. |
| **R-2** | Enabled by default unless a known conflict applies. |
| **R-3** | Turning it on and off applies live where that is safe. |
| **R-4** | No gameplay change, no custom packets, and no required server-side component. |
| **R-5** | AppleSkin is a declared conflict. An explicit choice by the player wins. |
| **R-6** | Keep the familiar AppleSkin presentation as closely as practical. |
| **R-7** | Mixins are allowed when they are needed and justified, but are not required. |
| **R-8** | Never display made-up exhaustion values. |
| **R-9** | No extra, speculative QoL features in V1. |

No code, textures or translations are copied from AppleSkin. AppleSkin's
license is Unlicense, but the project rule in AGENTS.md still applies. It was
studied for behavior only.

## 2. Sources

- **S-1** The unobfuscated Minecraft Java 26.3 JAR used by this project, inspected with `javap`.
- **S-2** Fabric API 0.166.2+26.3: `fabric-rendering-v1` 27.3.1, `fabric-item-api-v1` and `fabric-debug-api-v1` 1.0.5.
- **S-3** AppleSkin `3.0.10+mc26.3` for Fabric ([Modrinth](https://modrinth.com/mod/appleskin), [source](https://github.com/squeek502/AppleSkin)). Only its published JAR was inspected: the config defaults (`ModConfig`), the option texts (`en_us.json`), the Mixin list and targets, and the class and payload names. Its source code was not read.

"Confirmed" in the matrix below means visible in S-3: an option, a default,
a Mixin target or a network payload. "Assumed" means it was inferred and must
be checked against AppleSkin in game (FH-AC-15).

## 3. Data accuracy (Minecraft 26.3, S-1)

| Value | Class | Evidence and limits |
| --- | --- | --- |
| Food level | **Known / synchronized** | `ClientboundSetHealthPacket` (`health`, `food`, `saturation`). The client applies it through `FoodData.setFoodLevel`. |
| Saturation | **Known, but may be stale** | Sent in the same packet. The server (`ServerPlayer`, fields `lastSentHealth`, `lastSentFood` and `lastFoodSaturationZero`) only sends that packet when health or food changes, or when saturation reaches or leaves zero. Saturation that exhaustion uses up without a change in food is **not** sent. The client value can therefore be higher than the real one. |
| Health, max health, absorption | Known / synchronized | Vanilla entity data, already used by the vanilla hearts. |
| Food nutrition and saturation per item | Known | The `FoodProperties(nutrition, saturation, canAlwaysEat)` item component, synchronized with the item stack. `saturation` is an absolute amount. |
| Consume effects (potion effects, teleport, clearing effects) | Known as data, outcome not predicted | The `Consumable` component. Effects can be random, and data packs or server mods can change results. |
| Difficulty | Known | `ClientboundChangeDifficultyPacket`. Peaceful changes regeneration. |
| `natural_health_regeneration` game rule | **Unavailable** for most players | Only sent in `ClientboundGameRuleValuesPacket`, which `ServerGamePacketListenerImpl.sendGameRuleValues` sends only after a permission check (operators). |
| Exhaustion | **Unavailable** | No vanilla packet carries it. `FoodData.tick(ServerPlayer)` spends it on the server only. The client `exhaustionLevel` is a local value that is never corrected. |
| Food and saturation after eating | **Calculated** from known values | `FoodData.add`: `food = clamp(food + nutrition, 0, 20)`, then `saturation = clamp(saturation + foodSaturation, 0, food)`. |
| Health after eating | **Estimated with limitations** | See §3.1. |

### 3.1 Health restoration estimate [Approved 2026-11-22]

Vanilla regeneration (`FoodData.tick`, S-1) works like this:

- When food is 20 and saturation > 0, every 10 ticks it heals `min(saturation, 6) / 6` and adds `min(saturation, 6)` exhaustion.
- When food is at least 18, every 80 ticks it heals 1 and adds 6 exhaustion.
- 4 exhaustion uses up 1 saturation, or 1 food once saturation is 0.
- Regeneration only happens when the `natural_health_regeneration` rule is on.

The estimate simulates these rules on the food and saturation after eating,
capped at missing health. It assumes the following, so it is an
**estimate**:

- Exhaustion is 0 (it is unavailable).
- The game rule is on. Even an operator's client does not keep the value after the game-rules screen closes, so V1 always assumes it is on (§11.5).
- There is no other exhaustion, such as sprinting or taking damage.
- Food effects are ignored, for example Regeneration from a golden apple.

Saturation that may be stale (see the table) also makes the estimate too
high.

### 3.2 When a prediction can be wrong

- Food with consume effects: Hunger from rotten flesh, Regeneration, a random teleport from chorus fruit, effects cleared by milk or honey.
- Servers with data packs or mods that change food values or eating.
- Stale saturation (§3).
- Regeneration disabled by the game rule on a server where the player is not an operator.

## 4. AppleSkin parity matrix

| Behavior | AppleSkin 3.0.10 (status) | QoL: Unified V1 [Approved 2026-11-22] |
| --- | --- | --- |
| Saturation HUD overlay | Option `showSaturationHudOverlay`, default on (confirmed). Outline over the hunger icons (assumed). | Same. Original sprites. Uses the client saturation, which may be stale (§3). |
| Hunger restoration preview | `showFoodValuesHudOverlay`, default on (confirmed). Icons blink, up to a peak opacity of 0.65 (`maxHudOverlayFlashAlpha`, confirmed). | Same, with fixed blinking that looks similar. No opacity option. |
| Saturation restoration preview | Part of `showFoodValuesHudOverlay`, when the saturation overlay is on (confirmed by the option text). | Same coupling. |
| Health restoration preview | `showFoodHealthHudOverlay`, default on, described as "estimated" (confirmed). Mixin on `Hud.extractHearts` (confirmed). | Same, using the §3.1 estimate. |
| Food tooltip | Shows hunger and saturation (confirmed). Drawn with icons through a tooltip component; Mixins on `ItemStack.getTooltipLines` and `ClientTooltipComponent.create` (confirmed). | Icon tooltip, with original art. |
| Tooltip and Shift | `showFoodValuesInTooltip` (on Shift) and `showFoodValuesInTooltipAlways`, both default on (confirmed). | One ENUM: `off` / `shift` / `always`, default `always`. |
| Exhaustion underlay | `showFoodExhaustionHudUnderlay`, default on (confirmed). Needs data from the server: `ExhaustionSyncPayload` from a server-side `ServerPlayerMixin` / `FoodDataMixin` (confirmed). Without the server side it cannot have real data (assumed: AppleSkin then shows nothing or a stale value). | **Not offered** (R-8, §3). |
| F3 debug info | `showFoodDebugInfo` (confirmed). A `DebugScreenEntry` showing hunger, saturation and exhaustion (confirmed). | F3 entry with food and saturation. Exhaustion is never shown. |
| Following the HUD animation | `showVanillaAnimationsOverlay`, default on: the overlay shakes with the vanilla icons (confirmed). | Same, through Mixins (§5). |
| Game modes | Assumed: nothing where vanilla hides the hunger and health bars (Creative, Spectator). | Same. The tooltip shows in every mode. |
| Off-hand | `showFoodValuesHudOverlayWhenOffhand`, default on (confirmed). | Same. The main hand comes first, as in vanilla eating. |
| Food that can't be eaten now | `FoodHelper.canConsume` exists (confirmed). Assumed: no preview when the player could not eat. | No preview unless food < 20 or the food can always be eaten. |
| Rotten food | `FoodHelper.isRotten` exists (confirmed). How it is shown is assumed. | Shown like normal food. Checked against AppleSkin in QA (FH-AC-15), and adopted if different (§10). |
| Real-time saturation and the regeneration game rule | `SaturationSyncPayload` and `NaturalRegenerationSyncPayload` (confirmed), only with AppleSkin on the server. | Not possible (R-4). |
| Other mods' API | `AppleSkinApi`, events (confirmed). | Not offered (R-9). |

## 5. Technical approach [Approved 2026-11-22]

- **TA-1 Registration.** Registered through `Features.createRegistry`, in category `HUD_INFORMATION`, enabled by default, with toggle mode `LIVE`, and the `appleskin` conflict (R-5).
- **TA-2 LIVE design.** All hooks (Mixins, the HUD element, the debug entry) are installed once and stay. Each one checks a volatile enabled flag and the matching setting before doing anything. `enable` and `disable` only flip flags on the client thread, so `disable` cannot fail.
- **TA-3 Startup.** Mixins apply when `Hud`, `ItemStack` and `ClientTooltipComponent` load, which vanilla does anyway. No other work happens at startup. The debug entry is registered on the first `enable`.
- **TA-4 Calculation.** The §3 rules and the §3.1 estimate are pure Java without Minecraft types, with unit tests.
- **TA-5 Art.** Original sprites in `assets/qol_unified/`.
- **TA-6 Text.** All text uses translation keys in `en_us` and `pt_br`.

### 5.1 Fabric API vs Mixins

| Need | Fabric API | Enough? |
| --- | --- | --- |
| Draw after the food bar | `HudElementRegistry.attachElementAfter(VanillaHudElements.FOOD_BAR, …)` | Draws after the layer, but has no access to the icon positions the vanilla code picked. Those are random shake offsets and heart rows. |
| Text line in a tooltip | `ItemTooltipCallback` | Text only. It can't draw icons. |
| Map tooltip data to a drawn component | `ClientTooltipComponentCallback` | Only for a `TooltipComponent` the item provides, and food items provide none. |
| F3 entry | Vanilla `DebugScreenEntries.register` (public) | Yes. No Mixin needed. |

### 5.2 Mixins

**M-1 `Hud.extractFood(GuiGraphicsExtractor, Player, int, int)`** (private)
- *Why:* vanilla picks each icon's shake offset from `Hud`'s seeded `random` while drawing. Rebuilding that sequence outside the method is fragile, because the hearts use the same random source first. The shake is not cosmetic noise: it happens at saturation 0, which is exactly when players look at the preview. AppleSkin has a default-on option just for this.
- *How:* record each icon's position where `blitSprite` is called inside the method, then draw the overlay at `RETURN` using the recorded positions. This also removes the need to work out the bar position again.
- *Compatibility:* additive injections only (`@Inject`), no `@Overwrite` or redirect. Other mods' injections into the same method still work. AppleSkin targets the same method, so the declared conflict covers that case.
- *Tests:* a client game test checks that the injection applied, then saves screenshots at saturation 0 with food held. The Mixin config uses `require = 1`, so a missing target fails at launch in development and CI, not silently.
- *Restart / startup:* none. The cost is applied at class load, and the injection does nothing while the feature is off.

**M-2 `Hud.extractHearts(GuiGraphicsExtractor, Player, int, int, int, int, float, int, int, int, boolean)`** (private)
- *Why:* the health preview has to line up with each heart. Positions depend on rows, absorption, hardcore mode, the regeneration bounce and the low-health shake. All of that is computed inside this method and not exposed. Fabric only offers a layer after the health bar.
- *How:* record heart positions at the per-heart draw call, then draw at `RETURN`.
- *Compatibility, tests, restart:* the same as M-1.

**M-3 Tooltip icons**
- *Why:* icon rows need a `ClientTooltipComponent`. Fabric cannot attach one to items that provide no tooltip image (§5.1).
- *How:* the smallest hook that adds a client tooltip component for food stacks. The exact target, for example the method that builds the tooltip component list, is checked during implementation. A text line through `ItemTooltipCallback` is the fallback if no narrow target exists. That fallback would be reported as a parity gap.
- *Tests:* a game test hovers bread in the inventory, saves a screenshot, and checks the Shift modes.

No Mixin touches gameplay classes (`FoodData`, `Player`, `ServerPlayer`) or
networking.

## 6. Configuration [Approved 2026-11-22]

All settings apply live. None needs a restart.

| Key | Type | Default | AppleSkin equivalent |
| --- | --- | --- | --- |
| (feature toggle) | — | enabled (disabled when AppleSkin is present, R-5) | — |
| `saturation_overlay` | BOOLEAN | `true` | `showSaturationHudOverlay` |
| `food_preview` | BOOLEAN | `true` | `showFoodValuesHudOverlay` (hunger and saturation) |
| `health_preview` | BOOLEAN | `true` | `showFoodHealthHudOverlay` |
| `offhand_preview` | BOOLEAN | `true` | `showFoodValuesHudOverlayWhenOffhand` |
| `tooltip` | ENUM `off` / `shift` / `always` | `always` | the two tooltip options |
| `match_animations` | BOOLEAN | `true` | `showVanillaAnimationsOverlay` |
| `debug_info` | BOOLEAN | `true` | `showFoodDebugInfo` |

These are left out on purpose:
- the flash opacity: a fixed value is used;
- the exhaustion underlay: the data is unavailable;
- colors and positions.

## 7. Differences that cannot be avoided client-side

These hold on vanilla servers and in singleplayer, where the game also uses
the packets from §3. They cannot be fixed without a server-side component:

1. **No exhaustion display.** There is no underlay and no F3 value. This is unavailable data (R-8).
2. **Saturation may be stale.** It is only updated when health or food changes, or when saturation reaches zero, so the overlay can show more saturation than the player has.
3. **Health estimate.** It assumes exhaustion is 0 and regeneration is on, and is wrong on servers that disable regeneration for players who aren't operators.
4. **Effect outcomes are not predicted.**

AppleSkin running only on the client has the same limits. It avoids them only
when it is also installed on the server.

## 8. Acceptance criteria [Approved 2026-11-22]

| Id | Criterion |
| --- | --- |
| **FH-AC-1** | In Survival with food 20 and saturation 5, the overlay covers 2.5 icons. At saturation 0 there is no overlay. |
| **FH-AC-2** | Bread (5 / 6.0) held at food 10 and saturation 0 previews food 15 and saturation 6. Steak (8 / 12.8) at food 16 previews food 20 and saturation 12.8. |
| **FH-AC-3** | At food 20 normal food shows no preview, and a golden apple does. |
| **FH-AC-4** | The health preview matches the §3.1 estimate, with unit tests for: full food plus saturation, food 18–19, Peaceful, missing health smaller than the estimate, and the game rule known to be off. |
| **FH-AC-5** | Off-hand food is previewed only when `offhand_preview` is on and the main hand holds no food. |
| **FH-AC-6** | Tooltip modes: `always` shows the icons, `shift` only while Shift is held, `off` never. |
| **FH-AC-7** | With `match_animations` on, at saturation 0 the overlay icons move with the shaking vanilla icons in every saved frame. With it off, they stay still. |
| **FH-AC-8** | The F3 entry shows food and saturation, and never shows exhaustion. |
| **FH-AC-9** | In Creative and Spectator no HUD overlay is drawn, and the tooltip still works. |
| **FH-AC-10** | Every setting and the feature toggle take effect in the next frame, without a restart or errors, and persist across restarts. |
| **FH-AC-11** | At GUI scales 1–4 and 854×480, overlays line up with the icons, with armor, air bubbles, absorption, two heart rows, hardcore hearts and a mount's health. |
| **FH-AC-12** | With no world, the HUD hidden (F1) or a non-food item held, nothing is drawn and no errors are logged. |
| **FH-AC-13** | On a vanilla dedicated server everything except the impossible items in §7 works, and the feature sends no packets. |
| **FH-AC-14** | Food, saturation, health and the inventory are identical with the feature on or off. |
| **FH-AC-15** | **Visual comparison with AppleSkin.** Two separate disposable profiles, one with AppleSkin 3.0.10+mc26.3 and one with QoL: Unified, both with only Fabric API. Same world copy, same foods, same food levels and GUI scale. Screenshots side by side for each FH-AC-1 to FH-AC-9 situation. Differences are listed. Each difference is either fixed or accepted by the PO, except the art, which is original by rule. |
| **FH-AC-16** | With AppleSkin installed, `food_hud` starts disabled with the conflict status. A player's explicit enable is kept after a restart. |
| **FH-AC-17** | The Mixins apply (`require = 1`) and the game tests pass. |
| **FH-AC-18** | All new text is correct in English and Brazilian Portuguese. |

## 9. In-game QA checklist [Approved 2026-11-22]

Use disposable profiles and worlds only, never your main installation.

1. In a Survival test world, use `/effect give @s hunger` and then eat, to reach the FH-AC-1 and FH-AC-2 values.
2. Hold bread, steak, a golden apple, rotten flesh and chorus fruit at several food levels, in each hand (FH-AC-2, -3, -5).
3. Take damage, then check the health preview at food 20 and food 18 and in Peaceful (FH-AC-4).
4. Hover food in the inventory with each tooltip mode, with and without Shift (FH-AC-6).
5. Starve to saturation 0 and watch the shaking icons with `match_animations` on and off (FH-AC-7).
6. Open F3 (FH-AC-8). Switch to Creative and Spectator (FH-AC-9).
7. Toggle every setting while playing, then restart (FH-AC-10).
8. Check GUI scales 1–4, armor, swimming, absorption, more than 10 hearts, hardcore and riding a horse (FH-AC-11). Press F1 (FH-AC-12).
9. Join a vanilla 26.3 server and repeat steps 1–5 (FH-AC-13).
10. Run the AppleSkin comparison (FH-AC-15), then put AppleSkin and QoL: Unified in one profile (FH-AC-16).
11. Switch to Português (Brasil) (FH-AC-18).

## 10. Decisions [Approved 2026-11-22]

- **D-1** Mixins M-1 and M-2 are approved.
- **D-2** M-3 is approved on condition that a narrow, reliable injection point exists. Otherwise the text fallback is used and the parity gap is reported.
- **D-3** Differences found in the AppleSkin comparison that stay inside the §4 parity matrix may be corrected without a new approval.
- **D-4** Off-hand selection follows vanilla 26.3 item-use order as closely as client-visible data allows. It does not simply ignore the off-hand whenever the main hand holds food (§11).
- **D-5** No unrelated Mixins, dependencies or architecture changes.

## 11. Implementation notes [Implemented 2026-11-22]

What was built, where it differs from the text above, and the evidence.

### 11.1 Code

- `feature/foodhud/FoodHudSettings` (descriptor), `FoodHudFeature` (lifecycle), `FoodMath` and `HandSelection` (pure Java, unit tested), `HeldFood`, `FoodHudRenderer`, `FoodTooltip` and `FoodTooltipComponent`, `FoodDebugEntry`, and `FoodHudHooks`.
- Mixins in `qol_unified.mixins.json`, all client-only, with `defaultRequire: 1`: `HudFoodMixin` (M-1), `HudHeartsMixin` (M-2) and `ItemStackTooltipMixin` (M-3).
- Original saturation sprites, 9×9, in four quarter steps: `textures/gui/sprites/food_hud/saturation_1..4.png`. Each is a hand-drawn golden drumstick (meat top-left, bone bottom-right, like the vanilla hunger icon), with partial steps keeping the left side. Refined 2026-11-22 after PO review; the first version was a gold frame. At the PO's request, partial icons fill from left to right: half hunger shanks in previews and tooltips are vanilla's full shank clipped to its left 5 pixels, and saturation quarters keep the left 3, 5, 7 or 9 pixels. Vanilla's own half shank on the bar keeps its right side and is not changed. Previews reuse vanilla's own food and heart sprites.
- No Fabric HUD element is used. The Mixins already run inside the vanilla bar drawing, so TA-2's HUD element was not needed.

### 11.1a Tooltip values [Approved 2026-11-22]

At the PO's request, the tooltip shows the exact values next to the icons, in
one aligned column after the wider icon row:
- hunger as a signed whole number (`+5`);
- saturation with one decimal in the game language's separator (`+1.2` in English, `+1,2` in Portuguese), in gold.

Values come from the stack's own food component, so custom foods show their
real numbers. Icons keep the 2-points-per-icon scale: 1 point is half a
drumstick, and partial icons fill from left to right. Icons stop at 10 per
row; the number always shows the full value. The visibility modes are
unchanged. Formatting is in the pure `FoodTooltipText`, which has unit tests.

### 11.2 Mixins as built

- **M-1** `@Inject` at `HEAD` and `RETURN` of `Hud.extractFood`, plus a MixinExtras `@WrapOperation` on the first `blitSprite` call (the icon background). The wrap calls the original and records its `y`. MixinExtras ships with Fabric Loader 0.20.1, so no dependency was added. Unlike a redirect, `@WrapOperation` composes with other mods' wraps.
- **M-2** `@Inject` at `HEAD` and `RETURN` of `Hud.extractHearts`, plus `@WrapOperation` on the single `blitSprite` call in `Hud.extractHeart`. Positions are recorded only while `extractHearts` runs, and only for the vanilla `hud/heart/container*` sprites (one per heart). `@ModifyArgs` on the `extractHeart` call was tried first and failed at runtime with an `IllegalAccessError`: its generated argument class cannot see the package-private `Hud.HeartType`.
- **M-3** `@Inject` at `RETURN` of `ItemStack.getTooltipImage()`, which is public and one line in vanilla. A food stack that has no other tooltip image gets a `FoodTooltip`, and Fabric's `ClientTooltipComponentCallback` turns it into the icon rows. This is narrow and reliable, so the text fallback was **not** needed (D-2). Vanilla inserts tooltip images after the item name, which is where AppleSkin draws its rows too (§11.6).
- Recorded positions live in fixed arrays that are reset at each `HEAD` and consumed at `RETURN`. An incomplete record (other than all 10 hunger icons, or no hearts) draws nothing, so positions never go stale and overlays are never drawn twice.
- While the feature is off, the injections only check one volatile field.

### 11.3 LIVE behavior

`enable` installs the tooltip callback and debug entry once, then publishes
the feature's live settings. `disable` clears them and cannot fail. Every
hook reads the current effective settings each frame, so all seven settings
and the toggle apply in the next frame. This is checked in unit tests and the
game test.

### 11.4 Off-hand selection (D-4)

Vanilla 26.3 `Minecraft.startUseItem` tries the main hand, then the
off-hand, and stops at the first hand whose use succeeds.
`Item.use` → `Consumable.startConsuming` fails when `Player.canEat` is false
(food 20, not "can always eat", not invulnerable), so the off-hand is tried
next. V1 sorts each held stack using only what the client can see:

| Held stack | Effect on the off-hand |
| --- | --- |
| Empty, or an item with no known use | The off-hand is reached. |
| Food that can be eaten now | Previewed. The main hand wins. |
| Food that cannot be eaten now | The off-hand is reached (for example a golden apple behind full-hunger bread). |
| Any other consumable (potion, milk), or an item with a use animation (bow, shield, spyglass, …) | The off-hand is not reached. |
| A block item while looking at a block | The off-hand is not reached (the block is placed). |

**Cannot be predicted exactly:**
- Using an item on a block or an entity first runs that interaction, for example opening a chest or feeding or trading with a mob.
- Items whose use the client can't see without running it (throwables, buckets, fishing rods, flint and steel) count as "no known use", so the off-hand food may be previewed even though vanilla would use the main-hand item.
- Server-side mods can change any of this.

The AppleSkin comparison showed AppleSkin also previews the off-hand golden
apple behind full-hunger bread.

### 11.5 Data findings during implementation

- The `natural_health_regeneration` value an operator receives is only shown in the game-rules screen and is not kept by the client, so V1 assumes regeneration is on.
- New debug entries start as `NEVER` in vanilla's debug options. Players turn the "qol_unified:food_hud" line on in the F3 options screen (F3 + F6). V1 never changes the player's debug profile. AppleSkin's entry works the same way.
- In Peaceful, V1 shows no health preview, because regeneration there does not depend on food.

### 11.6 AppleSkin comparison (FH-AC-15)

Method: `runGameTestAppleskin -PappleSkinJar=<path>` loads the downloaded
AppleSkin `3.0.10+mc26.3` JAR into a separate disposable game directory
(`build/run-gametest-appleskin`) through Fabric's `fabric.addMods`. Its
SHA-512 matched Modrinth's published hash. The same world settings, foods,
food levels, window (854×480) and GUI scale (2) are used as in the `foodhud`
scenario, and screenshots are compared by eye. AppleSkin is not added to the
build.

| Situation | Result |
| --- | --- |
| Saturation overlay, food 20 and saturation 5 | **Matches** in coverage (2.5 icons). Both read as golden shanks. AppleSkin draws an outline that lets the shank show through; Food HUD draws an original filled golden drumstick over it (original art, by rule). |
| Bread, steak and golden-apple previews | **Matches**: the same icons blink, and saturation outlines blink up to the predicted value. |
| Health preview (steak at 8 HP) | **Matches**: hearts blink up to the estimate. |
| Off-hand golden apple behind full-hunger bread | **Matches**. |
| Hunger effect at saturation 0 | **Matches**: the preview uses the green hunger sprites. |
| Tooltip (bread, rotten flesh) | **Matches** the layout: name, then a hunger row and a saturation row. Food HUD drew empty-shank backgrounds behind the icons and AppleSkin does not; this was **corrected** (D-3). Rotten flesh shows like normal food in both. |
| F3 | **Differs on purpose**: AppleSkin shows `exh: 0.19/4` from the client's unsynchronized exhaustion. Food HUD shows only food and saturation (R-8). |
| Conflict (FH-AC-16) | With AppleSkin loaded, `food_hud` starts disabled with the conflict status. An explicit enable makes it active and is recorded. Keeping it across a restart is covered by unit tests. |

**Pending:** the exact blink timing and opacity curve, and following the
shake frame by frame. Single screenshots can't show these reliably; they are
on the manual QA list.

### 11.7 Known limitations

- §7 applies in full: no exhaustion, saturation may be stale, the health estimate has its stated assumptions, and food effects are not predicted.
- The off-hand cases in §11.4.
- FH-AC-13 (dedicated server) is automated, but the step only runs once the developer accepts the Minecraft EULA in `build/run-gametest-foodhud/eula.txt`. The test never accepts it. It was not run for this implementation.
- The F3 line must be turned on in vanilla's F3 options.
- GUI scale 4 could not be shown at the 854×480 test window, where vanilla caps the scale at 2. Scales 1 and 2 were captured.
