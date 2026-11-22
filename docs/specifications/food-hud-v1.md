# Food HUD V1 (M1.1)

Status labels follow [AGENTS.md](../../AGENTS.md#decision-status-labels).

This is a specification only. No code is written until the PO approves the
[Proposed] parts.

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

### 3.1 Health restoration estimate [Proposed]

Vanilla regeneration (`FoodData.tick`, S-1) works like this:

- When food is 20 and saturation > 0, every 10 ticks it heals `min(saturation, 6) / 6` and adds `min(saturation, 6)` exhaustion.
- When food is at least 18, every 80 ticks it heals 1 and adds 6 exhaustion.
- 4 exhaustion uses up 1 saturation, or 1 food once saturation is 0.
- Regeneration only happens when the `natural_health_regeneration` rule is on.

The estimate simulates these rules on the food and saturation after eating,
capped at missing health. It assumes the following, so it is an
**estimate**:

- Exhaustion is 0 (it is unavailable).
- The game rule is on, unless the client has received a value for it.
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

| Behavior | AppleSkin 3.0.10 (status) | QoL: Unified V1 [Proposed] |
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
| Rotten food | `FoodHelper.isRotten` exists (confirmed). How it is shown is assumed. | Shown like normal food. Checked against AppleSkin in QA (FH-AC-15), and adopted if different (Q-FH2). |
| Real-time saturation and the regeneration game rule | `SaturationSyncPayload` and `NaturalRegenerationSyncPayload` (confirmed), only with AppleSkin on the server. | Not possible (R-4). |
| Other mods' API | `AppleSkinApi`, events (confirmed). | Not offered (R-9). |

## 5. Technical approach [Proposed]

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

### 5.2 Proposed Mixins

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

## 6. Configuration [Proposed]

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

## 8. Acceptance criteria [Proposed]

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

## 9. In-game QA checklist [Proposed]

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

## 10. Blocking questions

- **Q-FH1 [Open]** Do you accept the three Mixins M-1 to M-3 as justified (R-7)? If not, `match_animations` and the icon tooltip drop out, and the health preview becomes less accurate.
- **Q-FH2 [Open]** Should differences found in FH-AC-15 that this spec did not predict, such as how rotten food is shown, be adopted without a new approval as long as they stay inside §4? Or should each one come back to you?
