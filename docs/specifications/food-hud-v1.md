# Food HUD V1 (M1.1)

Status labels follow [AGENTS.md](../../AGENTS.md#decision-status-labels).

The feature itself and its id, category and milestone are **[Approved]**
([feature catalog](../product/feature-catalog.md)). Everything else in this
document is **[Proposed]** unless marked otherwise. This is a specification
only. No code is written until the PO approves it.

## 1. Goal

Show the player, on the vanilla hunger bar, information the client already
has. It covers how full they are, how much saturation they have, and what the
held food would restore. Gameplay and food mechanics are never changed.

## 2. Technical findings (Minecraft Java 26.3)

All of the following was verified by inspecting the unobfuscated 26.3 client
JAR used by this project (`javap`). The only other source is Fabric API
0.166.2+26.3 (`fabric-rendering-v1` 27.3.1, `fabric-item-api-v1`).

| Finding | Evidence |
| --- | --- |
| **F-1** The server sends the client only health, food level and saturation. | `ClientboundSetHealthPacket` has exactly the fields `health`, `food` and `saturation`. `ClientPacketListener.handleSetHealth` calls `FoodData.setFoodLevel` and `setSaturation`. |
| **F-2** Exhaustion is **not** synchronized. | No packet carries it. `FoodData.tick(ServerPlayer)` (which spends exhaustion) only runs on the server. The client's `exhaustionLevel` is a local value that never matches the server's. |
| **F-3** A food's nutrition and saturation are client-known item data. | `FoodProperties(int nutrition, float saturation, boolean canAlwaysEat)` is an item component with a network codec (`DIRECT_STREAM_CODEC`). `saturation` is the absolute amount added, not a modifier. |
| **F-4** Restoring follows a fixed rule. | `FoodData.add`: `food = clamp(food + nutrition, 0, 20)`, then `saturation = clamp(saturation + foodSaturation, 0, food)`. Constants are in `FoodConstants` (`MAX_FOOD` = 20). |
| **F-5** Some eating results cannot be predicted from `FoodProperties`. | Consume effects (`Consumable`, `ConsumableListener`) such as potion effects, and server data packs or server-side mods, can change what happens. |
| **F-6** The vanilla bar is drawn by the private method `Hud.extractFood(GuiGraphicsExtractor, Player, int, int)`. The Hunger effect changes the sprites, and the icons shake when saturation is 0. | `Hud` bytecode: `MobEffects.HUNGER`, `FOOD_*_HUNGER_SPRITE`, `RandomSource.nextInt`. |
| **F-7** Fabric offers a HUD layer API with a vanilla food-bar id, and tooltip hooks. | `HudElementRegistry.attachElementAfter`, `VanillaHudElements.FOOD_BAR`, `HudStatusBarHeightRegistry`, `ItemTooltipCallback`. |

So the client values are as follows:

- **Known:** current food, current saturation, the held item's nutrition and saturation, Creative and Spectator mode, and whether the Hunger effect is active.
- **Estimated:** the food and saturation after eating, from F-4. A server may differ (F-5).
- **Not available:** exhaustion (F-2), unless a server-side mod sends it. That is out of scope.

## 3. Behavior [Proposed]

- **FH-1 Saturation overlay.** An outline is drawn over the hunger icons to show current saturation, in half-icon steps (1 point = half an icon), and never past the food level. It shows nothing when saturation is 0.
- **FH-2 Food preview.** If the player holds an item with `FoodProperties` in either hand and could eat it, the icons the food would fill blink, using the F-4 result. "Could eat" means food < 20, or the food can always be eaten.
- **FH-3 Saturation preview.** With FH-2, the saturation outline also blinks up to the estimated saturation after eating.
- **FH-4 Tooltip.** Food items get one tooltip line with nutrition and saturation, as numbers, for example `Food: 4 · Saturation: 2.4`. The line reads its values from the item stack, so it covers modified items.
- **FH-5 No exhaustion display.** V1 does not show exhaustion, because the client does not have the real value (F-2). See Q-FH2.
- **FH-6 Game modes.** Nothing is drawn where vanilla hides the hunger bar (Creative, Spectator, hidden GUI). The tooltip line still shows in every mode.
- **FH-7 Missing data.** If there is no player, the item has no `FoodProperties`, or a value is missing, that element draws nothing. It never throws.
- **FH-8 Coexistence with vanilla visuals.** The vanilla bar stays as it is, including the Hunger-effect sprites and the shaking. The overlays move with the icons whenever possible, see Q-FH4.
- **FH-9 No gameplay change.** The feature only reads data. It sends no packets, takes no input, and changes no player, item or world state.
- **FH-10 Labelled as estimates.** Previews are visual only. The spec does not claim they show what the server will do.

## 4. Technical approach [Proposed]

- **TA-1** `food_hud` is registered through `Features.createRegistry`, in category `HUD_INFORMATION`, enabled by default, with toggle mode `LIVE`.
- **TA-2** One HUD element is attached once with `HudElementRegistry.attachElementAfter(VanillaHudElements.FOOD_BAR, …)`, and one `ItemTooltipCallback` listener is registered once. Fabric cannot unregister either one. Both therefore check a volatile "enabled" flag that `enable` and `disable` switch on the client thread. `disable` cannot fail, which satisfies the M0 lifecycle and makes LIVE toggling safe.
- **TA-3** Registration is lazy: it happens on the first `enable`, not in the mod initializer, to keep startup cost minimal (AGENTS.md).
- **TA-4** Bar position: the vanilla code computes it from the window size, the GUI scale and the status-bar heights (`HudStatusBarHeightRegistry`). The implementation repeats that calculation and must check it against `Hud.extractPlayerHealth`.
- **TA-5** **No Mixins in V1.** The overlay is drawn after the vanilla bar, so it does not move with the shaking icons (FH-8). Following the shake exactly would need a Mixin on `Hud.extractFood`. That is left to Q-FH4.
- **TA-6** Estimation (F-4) is pure Java with no Minecraft types, and has unit tests.
- **TA-7** Original sprites and colors only. Nothing is copied from AppleSkin's textures, code or translations.
- **TA-8** New text uses translation keys in `en_us` and `pt_br`.

## 5. Configuration [Proposed]

| Key | Type | Default | Apply |
| --- | --- | --- | --- |
| (feature toggle) | — | enabled | LIVE |
| `saturation_overlay` | BOOLEAN | `true` | LIVE |
| `food_preview` | BOOLEAN | `true` | LIVE. Also controls FH-3. |
| `tooltip` | BOOLEAN | `true` | LIVE |

No setting needs a restart. Colors, opacity, positions and blink speed are
left out on purpose. The existing BOOLEAN descriptor is enough.

## 6. Compatibility [Proposed]

- **AppleSkin** (`appleskin`): version `3.0.10+mc26.3` for Fabric is published for 26.3 ([Modrinth](https://modrinth.com/mod/appleskin)). Its `fabric.mod.json` lists client Mixins and a description of "food-related HUD improvements". With both mods enabled, the same overlays and tooltip values would be drawn twice. This is a duplicate-output conflict, not a crash; no crash is documented. The proposal is to declare `ConflictDescriptor("appleskin", …)`, so `food_hud` starts disabled when AppleSkin is present. As M0 requires, the player can still enable it explicitly, and that choice is kept.
- **Other HUD mods.** No documented conflicts were found. Mods that move or replace the vanilla food bar could misplace the overlay (TA-4). Those are handled as bug reports, not declared conflicts in advance.
- **Vanilla servers.** Everything the feature uses arrives through vanilla packets (F-1, F-3), so it works on any vanilla server.

## 7. Acceptance criteria [Proposed]

| Id | Criterion |
| --- | --- |
| **FH-AC-1** | In Survival, with food 20 and saturation 5, the overlay covers 2.5 icons. With saturation 0, no overlay is drawn. |
| **FH-AC-2** | Holding bread (5 / 6.0) at food 10 and saturation 0, the preview fills icons up to food 15 and saturation 6. Steak (8 / 12.8) at food 16 shows food 20 and saturation 12.8 (clamped). |
| **FH-AC-3** | At food 20, normal food shows no preview, and food that can always be eaten (for example a golden apple) does. |
| **FH-AC-4** | In Creative and Spectator no overlay is drawn. The tooltip line is still shown. |
| **FH-AC-5** | Turning the feature or any setting on or off in the settings screen takes effect in the next frame, without a restart, and the log shows no errors. |
| **FH-AC-6** | Settings survive a restart. |
| **FH-AC-7** | At GUI scales 1–4 and the smallest supported window (854×480), the overlay lines up with the vanilla icons, including with armor, air bubbles or a mount's health bar visible. |
| **FH-AC-8** | With no world loaded, a non-food item held, or the HUD hidden (F1), nothing is drawn and nothing is logged as an error. |
| **FH-AC-9** | On a vanilla dedicated server, every element works and the player is never disconnected. |
| **FH-AC-10** | Food, saturation and the inventory are identical whether the feature is enabled or not. No packets are sent by the feature. |
| **FH-AC-11** | With AppleSkin installed, `food_hud` starts disabled with the conflict status. An explicit enable is kept after a restart. |
| **FH-AC-12** | The estimation rule (F-4) has unit tests for clamping, food that can always be eaten, and saturation capped by the food level. |
| **FH-AC-13** | The tooltip line is correct in English and Brazilian Portuguese. |

## 8. In-game QA checklist [Proposed]

1. In a new Survival world, lower food with `/effect give @s hunger`, eat to gain saturation, then check FH-AC-1.
2. Hold bread, steak, a golden apple and rotten flesh at several food levels, then check FH-AC-2 and FH-AC-3. Rotten flesh gives Hunger in vanilla; the preview shows only nutrition and saturation.
3. Switch with `/gamemode creative` and `/gamemode spectator`, then back (FH-AC-4).
4. Open the settings screen and toggle each option while watching the bar (FH-AC-5). Restart (FH-AC-6).
5. Try GUI scales 1–4, wear armor, go underwater, and ride a horse (FH-AC-7).
6. Press F1, and hold a non-food item (FH-AC-8).
7. Join a vanilla 26.3 server and repeat steps 1–2 (FH-AC-9).
8. Install AppleSkin in a separate test profile, not the main installation, and check FH-AC-11.
9. Switch the game to Português (Brasil) and check the tooltip (FH-AC-13).

## 9. Open questions

- **Q-FH1 [Open]** Should the preview also show nutrition from the off-hand when the main hand holds a different food? Vanilla eats the main hand first. The proposal is to preview only the item that would be eaten.
- **Q-FH2 [Open]** Should exhaustion ever be shown? The client only has a local value that does not match the server's (F-2). The proposal is no for V1.
- **Q-FH3 [Open]** Should the tooltip show the line only while Shift is held, or always? The proposal is always.
- **Q-FH4 [Open]** Is a Mixin on `Hud.extractFood` acceptable later, so the overlay can follow the shaking icons? V1 proposes no Mixin and accepts the small mismatch.
- **Q-FH5 [Open]** Should the conflict with AppleSkin be declared (§6), or should both run side by side?
