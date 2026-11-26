# Zoom V1 (M1.8)

Status labels follow [AGENTS.md](../../../AGENTS.md#decision-status-labels).
Approved by the Product Owner on 2026-11-26 (§0). The sections below keep
their original labels; §0 records the resolution of each.

A client-side camera zoom: hold (or toggle) a key to narrow the field of view
smoothly, scroll to zoom further, release to return. Inspired by the behavior
of Zoomify (isXander) and Ok Zoomer. No third-party code, assets,
configuration files or translations are used.

## 0. Approval [Approved 2026-11-26]

- Identity: `zoom`, M1.8, enabled by default, LIVE, fully client-side; no
  packets or server-side component.
- V1 scope (§3, §5–§12) and acceptance criteria Z-AC-1 to Z-AC-17 (§17), with
  the corrections below.
- **Q-Z1:** category `controls`.
- **Q-Z2:** default physical key Z, rebindable in vanilla Controls and
  Keyboard Map.
- **Q-Z3:** 250 ms zoom-in, 150 ms zoom-out, Smooth curve. Defaults changed
  on 2026-11-26 after manual testing (M1.8.1): 1000 ms zoom-in, 0 ms
  (instant) zoom-out.
- **Q-Z4:** initial 4×, default scroll range 2–16×, multiplicative factor 1.25
  per notch. Default maximum changed to 14× on 2026-11-26 (M1.8.1).
- **Q-Z5:** minimum effective FOV 1.0°.
- **Q-Z6:** the scrolled level resets to the initial magnification between
  activations.
- **Q-Z7:** no settings comparison.
- **Q-Z8:** soft conflicts with `zoomify`, `ok_zoomer`, `logical_zoom`; Sodium
  and Iris are not conflicts.
- **Q-Z9:** the three narrow Mixins.
- **Q-Z10:** toggle mode also resets when a menu opens, the game pauses or the
  window loses focus.
- **Correction (scroll):** do not turn every non-zero wheel event into a full
  notch; preserve the magnitude of fractional and high-resolution deltas.
- **Correction (bounds):** minimum ≤ initial ≤ maximum must hold; contradictory
  values are rejected with field-level feedback and never saved or applied.

## 1. Identity, goals and non-goals [Proposed]

| Item | Proposal |
| --- | --- |
| Feature id | `zoom` |
| Milestone | M1.8 |
| Category | Controls (Q-Z1) |
| Default | Enabled |
| Apply mode | LIVE |
| Side | Client only: no server mod, no packets, no gameplay change |

### 1.1 Goals

- A zoom key that works the moment the mod is installed, with hold and
  toggle modes.
- Smooth, frame-rate-independent zoom in and out that can be interrupted and
  reversed without a jump.
- Mouse-wheel magnification while zoomed, without changing the hotbar slot.
- Mouse sensitivity that follows the magnification, so aiming stays
  controllable.
- The player's own FOV and sensitivity options are never written.
- Configured entirely in the Unified Settings Workspace with auto-apply.

### 1.2 Non-goals

- Spyglass overlay or spyglass sounds on ordinary zoom.
- Secondary zoom, presets, cinematic camera, HUD hiding (see §16).
- Long-distance rendering or "see distant entities" (Ok Zoomer has this; it
  touches server-integrated rendering and is out of scope).
- Any change to what the server sees: the FOV is never sent to the server.

## 2. Research

### 2.1 Sources and versions

| Mod | Version studied (Modrinth, Fabric, 26.3) | Fabric id (from the JAR's `fabric.mod.json`) | License (JAR metadata) | Notes |
| --- | --- | --- | --- | --- |
| [Zoomify](https://modrinth.com/mod/zoomify) ([source](https://github.com/isXander/Zoomify)) | `2.17.0+mc26.3`, published 2026-10-08 | `zoomify` | `LGPL-3.0-or-later` (Modrinth page says LGPL-3.0-only) | Requires YetAnotherConfigLib (`yet_another_config_lib_v3`). About 73.8 M downloads. Default key `C` (README). |
| [Ok Zoomer](https://modrinth.com/mod/ok-zoomer) | `19.0.0-beta.3`, 2026-10-26 | `ok_zoomer` | not declared in the JAR | Requires Fabric API. Beta for 26.3. |
| [Logical Zoom](https://modrinth.com/mod/logical-zoom) | `0.0.37`, 2026-09-22 | `logical_zoom` | MIT | One Mixin; minimal hold-to-zoom. |
| WI Zoom | — | — | — | No 26.3 Fabric release found on Modrinth. |

The study used the public pages, option names in each JAR's `en_us`
language file and the list of Mixin class names in each Mixins file. No
source code was read or copied.

### 2.2 Zoomify feature set (2.17.0)

From its README and option names:

- **Basic:** initial zoom, zoom-in time and zoom-out time in seconds, separate
  zoom-in and zoom-out transitions (14 curves: instant, linear and
  in/out/in-out variants of sine, quad, cubic and exponential), "affect hand
  FOV".
- **Scrolling:** enable scroll zoom, scroll step count, zoom per step, scroll
  smoothness, "remember zoom steps", optional key binds to scroll in and out.
- **Spyglass:** combine with or override the spyglass, or allow zoom only while
  holding or carrying one; spyglass overlay visibility; spyglass sounds.
- **Controls:** hold or toggle (separate Hold and Toggle key binds exist),
  relative sensitivity, relative view bobbing, cinematic camera.
- **Secondary zoom:** a second, independent zoom with its own amount, times
  and "hide HUD".
- **Misc:** presets (Default, OptiFine, OK Zoomer), a toast when the zoom key
  conflicts with another binding plus a button to unbind the conflict, config
  migration from Ok Zoomer.

Its Mixin list names `CameraMixin` and `MouseMixin` for the zoom itself and
separate spyglass and HUD Mixins. Ok Zoomer's list also has `CameraMixin` and
`MouseHandlerMixin`. Both confirm the interception points found in §4.

### 2.3 Ok Zoomer extras

Persistent zoom mode, vignette or spyglass overlay, hide crosshair, reduce
view bobbing, spring/balanced transitions in ticks, scroll sounds, "reset
zoom with mouse" (middle click), smart occlusion and "see distant entities".

## 3. Feature comparison matrix [Proposed]

| Capability | Zoomify | Ok Zoomer | QoL V1 | Reason |
| --- | --- | --- | --- | --- |
| Hold to zoom | Yes | Yes | **V1** | Core behavior |
| Toggle mode | Yes | Yes | **V1** | One enum setting |
| Configurable magnification | Yes | Yes | **V1** | |
| Min / max bounds | Partly | Yes | **V1** | Needed for scroll |
| Smooth in/out | Yes | Yes | **V1** | |
| Separate in/out times | Yes | Yes (ticks) | **V1** | Two integers; cheap and useful |
| Many easing curves | 14 | 6 | **V1: 3** (Smooth, Linear, Instant) | Most players never change it |
| Separate in/out curves | Yes | Yes | Exclude | One curve setting covers both |
| Scroll zoom | Yes | Yes | **V1** | |
| Smooth scroll steps | Yes | Yes | **V1, fixed** | Not exposed |
| Remember scroll level | Yes | Yes | **[Open] Q-Z6** | |
| Scroll key binds | Yes | Yes | Future | Mouse covers most players |
| Relative sensitivity | Yes | Yes | **V1** (on/off) | Aiming becomes unusable without it |
| Sensitivity scale % | — | Yes | Future | |
| Relative view bobbing | Yes | Yes | Future | |
| Cinematic camera while zoomed | Yes | Yes | Future | Vanilla already has Smooth Camera |
| Spyglass overlay / sounds | Yes | Yes | Future | Cosmetic |
| Spyglass gating (only while holding) | Yes | Yes | Exclude | Contradicts "works when installed" |
| Combine with spyglass | Yes | — | **V1, fixed** | §6.3 |
| Hide hand while zoomed | Hand FOV option | Zoom hands | Future | Hand is not zoomed by default (§4) |
| Hide HUD / crosshair | Secondary zoom | Yes | Future | |
| Secondary zoom | Yes | — | Exclude | Content-creator niche |
| Presets | Yes | Yes | Exclude | Few settings make presets unnecessary |
| Conflict toast + unbind button | Yes | — | Exclude | Keyboard Map already shows conflicts |
| Persistent zoom mode | — | Yes | Exclude | |
| Very high magnification (> 50×) | Yes | Yes | Exclude | §6.4 floor |
| See distant entities | — | Yes | Exclude | Rendering scope |
| Config migration | Yes | — | Exclude | |

## 4. Minecraft 26.3 technical findings

Verified with `javap` against the deobfuscated 26.3 client
(`minecraft-merged-deobf-26.3.jar`, Fabric API `0.166.2+26.3`).

| # | Finding | Evidence |
| --- | --- | --- |
| **F-1** | In 26.3 the world FOV is computed by **`Camera`**, not `GameRenderer`. `Camera.calculateFov(float partialTick)` (private) returns `options.fov()` × `lerp(partialTick, oldFovModifier, fovModifier)`, then `modifyFovBasedOnDeathOrFluid`. Panoramic mode returns 90. `getFov()` exposes the result. | `Camera` bytecode; `calculateFov` is called once from `Camera`'s per-frame update (offset 98). |
| **F-2** | `Camera.tickFov()` (per tick) asks `AbstractClientPlayer.getFieldOfViewModifier(firstPerson, fovEffectScale)` and eases `fovModifier` toward it, clamped to **0.1–1.5**. Sprint, Speed, Slowness, bow drawing and the **spyglass** (when scoping) all arrive here. | `Camera.tickFov` bytecode |
| **F-3** | The hand uses a **separate** `hudFov` (`calculateHudFov`, `BASE_HUD_FOV`). Changing the world FOV does not zoom the hand. | `Camera` fields and methods |
| **F-4** | `MouseHandler.turnPlayer(double)` computes `s = sensitivity × 0.6 + 0.2`, then `s³ × 8` as the multiplier (or `s³` while the first-person player **is scoping** with a spyglass), optionally passes deltas through `SmoothDouble` when `options.smoothCamera` is on, applies invert X/Y, then `LocalPlayer.turn(dx, dy)`. | `MouseHandler.turnPlayer` bytecode |
| **F-5** | `MouseHandler.onScroll(long, double, double)`: with a screen open it calls `Screen.mouseScrolled` and returns. Without a screen it calls `ScrollWheelHandler.onMouseScroll`, then for a spectator either scrolls the spectator menu or changes **flying speed**, otherwise `Inventory.setSelectedSlot`. | `MouseHandler.onScroll` bytecode |
| **F-6** | Fabric API 0.166.2 has no in-world scroll, camera-FOV or mouse-turn event (`fabric-screen-api-v1` covers screens only). Mixins are needed for F-1, F-4 and F-5. | Fabric API module list |
| **F-7** | Key bindings use **SDL scancodes** (physical positions; see [Keymap V1](keymap-v1.md) F-2). Vanilla 26.3 defaults include `C` = Save Hotbar Activator (`key.saveToolbarActivator`, scancode 6) and `X` = Load Hotbar Activator (27). **`Z` (scancode 29) has no vanilla default.** QoL's own keys (Open Settings, Keyboard Map) are unbound by default. | `Options` constructor bytecode |
| **F-8** | A key registered with `KeyMappingHelper.registerKeyMapping` appears in vanilla Controls and therefore in Keyboard Map with no extra work. | `KeyboardMapHooks`, `SettingsKeyBinding` |
| **F-9** | `options.fov()` and `options.sensitivity()` are `OptionInstance`s persisted in `options.txt`. Reading them is safe; this feature must **never call `set`** on them. | `Options` |

## 5. Zoom state machine [Proposed]

The controller keeps two numbers and no objects per frame:

- **`progress` p ∈ [0, 1]:** how far into the zoom the camera is. 0 means
  no zoom.
- **`level` m ≥ 1:** the magnification the player has chosen (initial value,
  changed by scrolling), with a smoothly following `shownLevel`.

| State | Meaning | Enter when |
| --- | --- | --- |
| IDLE | p = 0, no effect, Mixins return immediately | Start; zoom-out finished; feature disabled |
| ZOOMING_IN | p rises toward 1 | Zoom requested (hold key down, or toggle pressed while not zoomed) |
| ZOOMED | p = 1 | Zoom-in finished |
| ZOOMING_OUT | p falls toward 0 | Zoom released (hold key up, toggle pressed again, or a reset condition) |

- **Reversal:** p is a single value, so releasing during ZOOMING_IN switches
  to ZOOMING_OUT from the current p, and the reverse. Nothing jumps.
- **Reset conditions (zoom ends):** the zoom key is no longer down (hold
  mode); a screen opens; the window loses focus; the game pauses; the player
  dies or respawns; the dimension changes. Toggle mode is also switched off by
  these, so zoom never stays on behind a menu.
- **Instant reset (p = 0 at once):** world disconnect, the feature being
  disabled, a resource reload, and `minecraft.level == null`.
- **Hold-key sampling:** the controller reads `KeyMapping.isDown()` every
  client tick and on each frame, so a release that happens while a screen is
  open (vanilla releases all keys on screen change) cannot leave zoom stuck.

## 6. FOV and camera handling [Proposed]

### 6.1 Composition

One narrow Mixin on `Camera.calculateFov` at `RETURN`:

```
zoomedFov = vanillaFov / zoomFactor
zoomFactor = exp(ln(shownLevel) × ease(p))
```

- `vanillaFov` already contains the player's FOV option, Sprint/Speed/Slowness
  effects scaled by "FOV Effects", the spyglass, death and fluid effects.
  Zoom divides the result, so all of these keep working and the player's
  option changes (even while zoomed) are followed at once.
- The ease is applied in log space, so each part of the transition feels
  equally fast whether zooming 2× or 10×.
- Panoramic mode (90°, used for panorama screenshots) is left untouched.
- The hand (`hudFov`, F-3) is not zoomed. This matches Zoomify's
  "affect hand FOV = off" and avoids a giant hand.

### 6.2 Camera types

Zoom works in first person and both third-person views: it changes only the
projection, not the camera position. Third-person distance (`getMaxZoom`,
a camera collision test despite its name) is unrelated and untouched.

### 6.3 Spyglass

The spyglass already sets `fovModifier` to 0.1 (F-2). The zoom key while
scoping multiplies on top: spyglass × zoom. A floor of **1.0°** effective FOV
(Q-Z5) prevents degenerate projections. No spyglass overlay or sound is added
to ordinary zoom.

### 6.4 Bounds

The magnification is clamped to `[min_magnification, max_magnification]`
and the final FOV to ≥ 1.0°. If a player sets min > max, the controller uses
`max(min, max)` for both ends; settings validation stays per field because
descriptors validate one value at a time.

## 7. Scroll behavior [Proposed]

- **Interception:** a Mixin in `MouseHandler.onScroll` at the
  `ScrollWheelHandler.onMouseScroll` call, cancellable, active only when **no
  screen is open** and the zoom is ZOOMING_IN or ZOOMED, and `scroll_zoom` is
  ON. Screens, inventories and creative tabs keep the vanilla path because the
  screen branch returns earlier (F-5).
- **Effect:** each notch multiplies `level` by a fixed 1.25 (up) or divides by
  1.25 (down), clamped to the bounds. Notches use the sign of the vertical
  delta, so high-resolution and discrete wheels behave alike and the vanilla
  "Scroll Sensitivity" option does not change zoom speed.
- **Consumed:** the event never reaches the hotbar, the spectator menu or
  spectator flying speed while zoom owns it.
- **Smoothing:** `shownLevel` approaches `level` with a frame-rate-independent
  exponential follow (time constant about 80 ms, fixed).
- **During ZOOMING_OUT and IDLE:** scrolling is vanilla again immediately.
  A scroll that arrives during zoom-out changes the hotbar, as expected once the
  key is released.
- **After zoom ends:** `level` returns to `initial_magnification` unless
  Q-Z6 decides otherwise.

## 8. Sensitivity behavior [Proposed]

- **Interception:** a Mixin in `MouseHandler.turnPlayer` that scales the two
  computed deltas just before `LocalPlayer.turn`. The player's sensitivity
  option is never written (F-9).
- **Scale:** `1 / zoomFactor`, so the view moves the same on-screen distance per
  mouse distance at every magnification. It follows `p`, so the transition
  is smooth and it is exactly 1 in IDLE.
- **Spyglass:** vanilla's spyglass reduction stays; the zoom scale multiplies.
- **Smooth Camera:** if vanilla's Smooth Camera is ON it keeps working; the
  zoom does not reset its `SmoothDouble` state.
- **Setting:** `reduce_sensitivity` (ON by default) turns the scale off.
- No effect on movement, attacks or anything the server receives except the
  rotation the player actually turns to, which vanilla already sends.

## 9. Transitions [Proposed]

- **Clock:** real elapsed time from `Util.getNanos()` between frames, clamped
  to 100 ms per frame so a hitch does not skip the animation. No tick
  quantization, so 30, 60, 144 and 240 FPS give the same timing.
- **Step:** `p += dt / zoom_in_ms` while zooming in, `p -= dt / zoom_out_ms`
  while zooming out; 0 ms means instant.
- **Curves:** `smooth` (ease-out cubic, `1 − (1 − p)³`), `linear`,
  `instant`. The same curve shapes both directions; on the way out the ease is
  evaluated on the same p, so a reversal stays continuous.
- No animation framework; a few lines of arithmetic.

## 10. Proposed V1 settings and defaults [Proposed]

| Key | Type | Range | Default | Notes |
| --- | --- | --- | --- | --- |
| `zoom_mode` | Enum | `hold`, `toggle` | `hold` | |
| `initial_magnification` | Integer (×) | 2–10 | 4 | Q-Z4 |
| `min_magnification` | Integer (×) | 1–10 | 2 | Lower bound for scrolling |
| `max_magnification` | Integer (×) | 2–50 | 14 | Upper bound for scrolling |
| `scroll_zoom` | Boolean | — | ON | |
| `zoom_in_ms` | Integer (ms) | 0–2000 | 1000 | Q-Z3 |
| `zoom_out_ms` | Integer (ms) | 0–2000 | 0 | Instant |
| `transition` | Enum | `smooth`, `linear`, `instant` | `smooth` | |
| `reduce_sensitivity` | Boolean | — | ON | |

All settings are LIVE. The key binding is not a setting: it lives in vanilla
Controls (and Keyboard Map) like every other binding.

**Default key (Q-Z2):** a `Zoom` key mapping in the QoL: Unified category.
Proposal: **Z**, the one letter near WASD with no vanilla default (F-7).
`C`, the Zoomify/OptiFine habit, is the vanilla Save Hotbar Activator.
Alternative: unbound, like QoL's other keys, which makes the feature do
nothing until the player binds it. Because bindings are scancodes, the
proposal names the physical key, which is Z on US/ABNT2 and W on AZERTY. No
layout or scancode is hard-coded beyond the default value vanilla stores.

## 11. Unified Settings integration [Proposed]

- Settings appear inline in the `zoom` panel through the existing Boolean,
  Enum and Integer descriptors; no new screen, no settings framework.
- **Auto-apply:** each committed change is read by the controller at the next
  frame. Safe while zoomed:
  - Times and transition: affect the remaining part of the current transition.
  - Bounds and initial magnification: `level` is re-clamped at once and
    `shownLevel` follows smoothly.
  - Mode change from toggle to hold while toggled ON: zoom ends (key is not
    held).
  - `reduce_sensitivity`, `scroll_zoom`: next frame / next scroll.
- **Feature switch OFF while zoomed:** instant reset (§5), Mixins become no-ops.
  The settings screen is open at that moment anyway, which already ended the
  zoom.
- No comparison sample: a static picture of zoom adds little (Q-Z7).
- en_us and pt_br strings for the name, description, setting names, enum
  values and the key binding.

## 12. Technical architecture [Proposed]

| Piece | Responsibility |
| --- | --- |
| `ZoomFeature` | Lifecycle: enable registers nothing new (hooks are registered once at startup, gated by an active flag); disable resets the controller |
| `ZoomController` | State machine, progress, level, timing; a few primitive fields; all math |
| Key mapping | One `KeyMapping` via `KeyMappingHelper`; sampled from `ClientTickEvents.END_CLIENT_TICK` and on frame |
| `CameraFovMixin` | `Camera.calculateFov` RETURN: divide by `zoomFactor` |
| `MouseScrollMixin` | `MouseHandler.onScroll`, cancellable at the scroll-handler call |
| `MouseTurnMixin` | `MouseHandler.turnPlayer`: scale deltas |

- Three injection Mixins; each starts with a single boolean check and returns
  when the zoom is IDLE.
- Resets use `ClientPlayConnectionEvents.DISCONNECT` (Fabric API) and
  checks in the client tick: screen open, window focus, pause, the local
  player instance changing (respawn) and the level changing (dimension).
- No persistent state outside the Configuration Service. The scroll level is
  in-memory only.

## 13. Compatibility [Proposed]

| Mod | Fabric id (verified in JAR) | Overlap | Proposal |
| --- | --- | --- | --- |
| Zoomify | `zoomify` | Duplicate zoom, both modify `Camera` FOV and mouse; zooms would multiply | Known conflict (Core policy: `zoom` auto-disables while present unless the player re-enables it) |
| Ok Zoomer | `ok_zoomer` | Same | Known conflict |
| Logical Zoom | `logical_zoom` | Same | Known conflict |
| Sodium | `sodium` | Uses vanilla's camera FOV; no evidence of conflict | Not a conflict; QA check |
| Iris | `iris` | Shader packs use the game's projection; no evidence of conflict | Not a conflict; QA check with a shader pack |
| Vanilla spyglass | — | Composes (§6.3) | Tested |
| Freecam / camera mods | — | Unknown | Not declared until evidence |
| Resource packs | — | Nothing drawn | None |

No `breaks` entries; the player keeps the choice.

## 14. Performance [Proposed]

- Per frame when IDLE: one boolean read per Mixin.
- Per frame while zooming: a few float operations (`exp`, `log`, one ease);
  no allocation, no collections, no disk or network I/O.
- Settings are read from the active settings snapshot already held by the
  feature; no lookup by string per frame.
- No camera reconstruction, no extra render pass, no matrix rebuild beyond the
  projection vanilla already builds from `getFov()`.
- No startup work beyond registering one key mapping and the Mixins.
- No performance numbers are claimed until measured.

## 15. Open decisions for Product Owner approval

- **Q-Z1 [Open]** Category: Controls (input-driven, next to Keyboard Map;
  recommended) or Visuals.
- **Q-Z2 [Open]** Default key: Z (recommended, no vanilla default) or
  unbound.
- **Q-Z3 [Proposed]** Times: 250 ms in, 150 ms out, `smooth` curve.
- **Q-Z4 [Proposed]** Magnification: initial 4×, scroll range 2–16×, step
  1.25 per notch, Integer settings (no fractional magnifications in V1).
- **Q-Z5 [Proposed]** Effective FOV floor of 1.0°.
- **Q-Z6 [Open]** Remember the scrolled level between activations (session
  only), or always start from `initial_magnification` (recommended for V1).
- **Q-Z7 [Proposed]** No settings-panel comparison for Zoom.
- **Q-Z8 [Proposed]** Declare `zoomify`, `ok_zoomer` and `logical_zoom` as
  known conflicts; do not declare Sodium or Iris.
- **Q-Z9 [Proposed]** Three injection Mixins (§12).
- **Q-Z10 [Proposed]** Toggle mode ends on screen open, focus loss and pause,
  like hold mode.

## 16. Deferred features [Deferred]

Scroll key binds, sensitivity percentage, relative view bobbing, cinematic
camera while zoomed, spyglass overlay and sounds, hide hand, hide HUD or
crosshair, separate in/out curves, more easing curves, zoom presets.

## 17. Acceptance criteria [Proposed]

Game tests use a disposable run directory and never touch a real
`options.txt`. FOV is read from `Camera.getFov()`.

1. **Hold:** key down → FOV reaches `vanillaFov / 4` (±0.5°) after
   `zoom_in_ms`; key up → back to `vanillaFov` (±0.01°) after `zoom_out_ms`.
2. **Toggle:** first press zooms and stays zoomed with the key released;
   second press zooms out.
3. **Smooth:** with 250 ms, FOV at 125 ms is strictly between the endpoints;
   releasing at 125 ms reverses with no FOV jump larger than one frame's step.
4. **Frame-rate independence:** at simulated 30 and 240 FPS steps, FOV at
   the same elapsed time differs by < 1°.
5. **Scroll:** one notch up while ZOOMED → level 4 × 1.25; repeated notches
   stop at `max_magnification`; down stops at `min_magnification`.
6. **No hotbar change:** selected slot unchanged after scrolling while
   zoomed; changes normally after zoom ends; scrolling in an open inventory
   or creative screen behaves as vanilla.
7. **Sensitivity:** with `reduce_sensitivity` ON, the yaw change for a given
   mouse delta at 4× is ¼ (±5 %) of the unzoomed change; OFF gives the
   unzoomed change.
8. **Options untouched:** `options.fov()` and `options.sensitivity()` values
   and the `options.txt` bytes are identical before and after a zoom cycle.
9. **Resets:** zoom ends after opening a screen, losing focus, pausing, death,
   respawn, dimension change; instantly on disconnect.
10. **Disable while zoomed:** switching the feature OFF returns FOV to vanilla
    at once; later scrolls change the hotbar.
11. **Camera types:** criteria 1 and 7 pass in first person and both third
    person views.
12. **Spyglass and effects:** while scoping, zoom multiplies the spyglass FOV
    and stays ≥ 1.0°; Speed/Slowness/sprint FOV changes are still visible
    while zoomed (FOV Effects scale respected).
13. **Keyboard Map:** the Zoom binding is listed, can be rebound, and a
    conflict with another binding is shown.
14. **Persistence and auto-apply:** each setting change takes effect without
    Done, survives restart, and invalid integers are rejected.
15. **Compatibility and localization:** with a mod whose id is `zoomify`
    present, `zoom` starts disabled by Core policy; all strings exist in
    en_us and pt_br.
16. **Client-only:** joining a Paper server without the mod works and zoom
    functions; no custom packets are registered.
17. **No regressions:** all existing unit and game-test scenarios pass.

## 18. Manual QA plan [Proposed]

Run on Windows 11 and CachyOS (Prism, production JAR):

1. Hold Z: smooth zoom in, release: smooth out. Repeat quickly; no jumps.
2. Toggle mode: press, release, look around, press again.
3. Scroll while zoomed: magnification changes, hotbar does not; after release
   the hotbar scrolls normally.
4. Aim at a distant mob at 14×: mouse movement feels proportional; turn
   `reduce_sensitivity` OFF and feel the difference.
5. Sprint and drink a Speed potion while zoomed; use a spyglass and press
   Z while scoping.
6. Press F5 through all views while zoomed.
7. While zoomed: open the inventory, press Escape, Alt-Tab, die, travel
   through a portal. Zoom must never stay on.
8. Change times, curve and bounds in the settings while in a world; check the
   next zoom.
9. Rebind the key in Keyboard Map; check a conflict with C shows.
10. With Sodium and with Iris plus a shader pack: zoom works and nothing
    flickers.
11. Join the Paper test server (no server changes) and zoom.
12. Português: all Zoom texts translated.
13. Vanilla FOV and sensitivity in Options are unchanged afterwards.

## 19. Implementation notes [Implemented 2026-11-26]

### 19.1 Code

- `feature/zoom/ZoomSettings`: descriptor, nine LIVE settings, the three
  conflicts and the bounds rule.
- `feature/zoom/ZoomConfig`: the settings as plain fields plus the three
  curves; rebuilt only when the settings snapshot changes (one reference
  check per frame).
- `feature/zoom/ZoomController`: the state machine and all arithmetic; pure
  Java, primitive fields, no allocation per frame.
- `feature/zoom/ZoomHooks`: the `Zoom` key mapping (default `KEY_Z`, in the
  QoL: Unified category), the client-tick reset checks, the disconnect reset
  and the Mixin entry points.
- `ZoomFeature`: enable/disable only set the active context; disable resets
  the controller.

### 19.2 Mixins (Q-Z9)

| Mixin | Target (verified with `javap`) | Injection |
| --- | --- | --- |
| `ZoomCameraFovMixin` | `Camera.calculateFov(F)F` | MixinExtras `@ModifyReturnValue`; skipped in panoramic mode. Also the per-frame clock. |
| `ZoomMouseMixin` (scroll) | `MouseHandler.onScroll(JDD)V` at the `ScrollWheelHandler.onMouseScroll` call | Cancellable `@Inject`, reached only with no screen open |
| `ZoomMouseMixin` (turn) | `MouseHandler.turnPlayer(D)V` at `LocalPlayer.turn(DD)V` | Two `@ModifyArg` (yaw, pitch); no per-call allocation |

### 19.3 Technical corrections

- **Scroll magnitude (PO correction).** 26.3's `onScroll` multiplies the raw
  wheel delta by Scroll Sensitivity (or replaces it with its sign when
  Discrete Scrolling is on). For the hotbar, `ScrollWheelHandler` then adds it
  to an accumulator, truncates to whole steps and clears the accumulator when
  the direction reverses. Zoom reads the raw vertical delta, or its sign with
  Discrete Scrolling. It applies it as a continuous exponent, so the level is
  multiplied by `1.25^delta`. One notch is ×1.25; four quarter-notches equal
  one notch; a reversal undoes exactly what was scrolled; nothing is
  truncated or lost. Scroll Sensitivity does not change zoom speed.
- **Cross-setting validation.** The descriptor architecture validated one
  value at a time. A narrow `SettingsRule` was added to `FeatureDescriptor`; it
  defaults to none, and the old constructor keeps that default. The rule is
  checked in three places:
  - `SettingsDraft.setValue`: a contradiction becomes a field error and never
    enters the draft.
  - The integer field (`SettingsDraft.ruleViolation`): it shows the reason
    next to the field before anything is written.
  - Configuration load: a hand-edited contradiction falls back to the
    feature's defaults, with a warning.

  Zoom's rule is minimum ≤ initial ≤ maximum. Each message names the value to
  change first, so every coherent combination is reachable one field at a
  time.
- **Respawn and dimension change** are detected in the client tick as a new
  `LocalPlayer` or `ClientLevel` instance.

### 19.4 Measured in the Zoom game test (854×480, FOV option 70)

| Case | Value |
| --- | --- |
| Not zoomed | 70.0° |
| One tick (50 ms) after pressing | 32–36° (mid-transition) |
| Held | 17.5° (exactly 4×) |
| 16× after scrolling (test sets the maximum to 16×) | 4.38° |
| Yaw for a 100 px cursor move, plain / zoomed | 15.0° / 3.75° (ratio 0.250) |
| Spyglass alone / spyglass + zoom | 7.07° / 1.75° |

### 19.5 Known limitations

- Magnifications are whole numbers (Q-Z4).
- The scroll factor ignores vanilla Scroll Sensitivity by design.
- Sodium, Iris and the other zoom mods were not installed in automated tests;
  conflict handling is covered by unit tests with their mod ids.
