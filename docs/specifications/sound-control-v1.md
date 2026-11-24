# Sound Control V1 (M1.5)

Status labels follow [AGENTS.md](../../AGENTS.md#decision-status-labels).
Nothing in this document is approved yet. Verified facts about Minecraft
and other mods carry no label; every decision is **[Proposed]**,
**[Open]** or **[Deferred]**. The decisions the Product Owner needs to make
are listed in §13.

A client-side feature that lowers or mutes individual Minecraft sound
events without muting a whole vanilla sound category. It is inspired by the
behavior of Extreme Sound Muffler and similar mods. No third-party code,
assets, translations or configurations are used.

## 1. Feature identity and goals

### 1.1 Identity [Proposed]

| Item | Proposal |
| --- | --- |
| Feature id | `sound_control` |
| Milestone | M1.5 |
| Category | Audio (`audio`). This is a new category; the approved list has none for audio (Q-SC2) |
| Default | Enabled. With no rules, playback is exactly vanilla |
| Lifecycle | LIVE enable/disable. Disabling stops all adjustments, including on sounds that are already playing (§7.4) |
| Platform | Minecraft Java 26.3, Fabric, Java 25 |
| Side | Client only. No server mod, no packets, no change to the world or gameplay |

### 1.2 Goals [Proposed]

- G-1 Set a volume from 0% to 100% for each sound-event id; 0% mutes it.
- G-2 Show recently heard sounds so the player can find the annoying one
  without knowing its id.
- G-3 Search every sound the client knows about by name, id, mod or
  category, vanilla and modded alike, with no hardcoded list.
- G-4 Keep rules across restarts in the existing configuration file.
- G-5 Leave Minecraft's master and category volumes, pitch, positioning and
  scheduling untouched.

### 1.3 Non-goals [Proposed]

- Telling sources apart when they share one sound event, such as a TNT
  explosion and a Creeper explosion (§4).
- Making any sound louder than vanilla.
- Replacing the audio engine, changing OpenAL output or adding effects.
- Controlling audio that bypasses Minecraft's `SoundManager`, such as voice
  chat mods with their own OpenAL sources.
- Server-side or per-server rules, regions, profiles or sharing (§14).

## 2. Research into existing mods

### 2.1 Sources and versions

Versions were checked on 2026-11-23 through the public Modrinth API
(`api.modrinth.com/v2/project/<slug>/version`, filtered to Fabric and
26.3). Fabric mod ids, declared environments and Mixin target classes come
from each published JAR's `fabric.mod.json` and Mixin configuration. No
code was read beyond those names.

| Mod | Version researched | 26.3 Fabric build | Fabric id (from JAR) | License |
| --- | --- | --- | --- | --- |
| [Extreme Sound Muffler](https://modrinth.com/mod/extreme_sound_muffler) (LeoBeliik) | 4.06-26.3, 2026-11-21 | Yes | `extremesoundmuffler` (environment `*`) | LGPL-3.0-only |
| [Sound Controller](https://modrinth.com/mod/sound-controller) (BVengo) | 1.4.1-mc26.3, 2026-09-16 | Yes | `soundcontroller` (environment `client`) | LGPL-3.0-only |
| [Decibel](https://modrinth.com/mod/decibel-shasin) | 1.0.2+26.3, 2026-10-20 | Yes | `decibel` (environment `client`) | All rights reserved |
| [Sound Control](https://modrinth.com/mod/soundcontrol) (pohci) | 26.1-v1.1 | **No** (latest: 26.1.2) | not checked | MIT |
| [Enhanced Sound Control](https://modrinth.com/project/enhanced-sound-control) | latest for 26.2 | **No** (latest: 26.2) | not checked | GPL-3.0-only |

The PO named "Sound Control" in the brief. The mod with that exact name
(pohci) has no 26.3 build and calls itself early in development. Sound
Controller (BVengo) is the closest mature equivalent on 26.3.

Mixin targets found in the 26.3 JARs (used for §10):

| Mod | Mixin classes | Minecraft targets |
| --- | --- | --- |
| Extreme Sound Muffler | `SoundMixin`, `InventoryScreenMixin`, `CreativeInventoryScreenMixin` | `SoundEngine.calculateVolume(SoundInstance)` and `calculateVolume(float, SoundSource)`, inventory screens |
| Sound Controller | `SoundEngineMixin`, `SubtitleOverlayMixin`, `OptionsSubScreenMixin` | `SoundEngine.play`, `SoundEngine.calculateVolume(SoundInstance)`, the `canStartSilent()` call in `play`, `SubtitleOverlay` |
| Decibel | `AbstractSoundInstanceMixin`, `SoundEngineMixin`, `SoundOptionsScreenMixin` | `AbstractSoundInstance.getVolume` (RETURN), `SoundEngine.calculateVolume(SoundInstance)` (RETURN), sound options screen |

### 2.2 Comparison matrix

"Confirmed" means the behavior is stated on the mod's own 26.3-era project
page. "Unverified" means it is commonly described but was not checked in
the 26.3 build. All five mods are client-only for their core feature.

| Idea | Extreme Sound Muffler | Sound Controller | Decibel | Sound Control / Enhanced SC | Client-only feasible? | For QoL: Unified |
| --- | --- | --- | --- | --- | --- | --- |
| Per-event mute | Confirmed | Confirmed (slider to 0) | Confirmed | Confirmed (no 26.3 build) | Yes | **V1** |
| Per-event volume slider | Confirmed | Confirmed, 0–200% | Confirmed | Enhanced SC: 0–200% | Yes | **V1**, 0–100% only |
| Recently heard sounds | Unverified (list of recent sounds in the main screen) | Not listed | Not listed | — | Yes (vanilla listener, §3.5) | **V1** |
| Search | Unverified | Confirmed | Confirmed, human-readable names plus raw id tooltip | Sound Control: confirmed | Yes | **V1** |
| Filter by mod / vanilla | — | Filter to changed sounds | Confirmed (Vanilla / Mods / All tabs) | — | Yes | **V1** (namespace and category filters) |
| Show only changed sounds | — | Confirmed | — | — | Yes | **V1** (Rules tab) |
| Preview (play the sound) | Confirmed | Confirmed | — | — | Yes | Future (§14) |
| Reset one sound | — | Confirmed | Confirmed | — | Yes | **V1** |
| Volume above 100% | — | Confirmed | — | Confirmed | Partly: vanilla clamps the final gain to 1.0, so a boost only helps quiet sounds or low category sliders | **Exclude** from V1 |
| Pitch control | — | — | — | Enhanced SC: confirmed | Yes | Exclude (not asked for) |
| Ranged / region muting | Confirmed ("Anchors", 10 slots) | Confirmed (regions, stored per server) | — | — | Yes: `SoundInstance` has coordinates | Future |
| Presets / profiles | — | Confirmed | Confirmed | — | Yes | Future |
| Per-server configuration | — | Regions are per server | — | — | Yes | Future (CFG-P2 is deferred) |
| Show ids instead of subtitles | — | Confirmed (toggle) | — | — | Yes | Future; the recent list covers the need |
| Hide sounds from the list (blacklist) | Confirmed | — | — | — | Yes | Exclude; the bounded recent list is enough |
| Open from the inventory screen | Confirmed (draggable button) | — | — | — | Yes | Exclude; screen clutter |
| Entry from the vanilla sound options | — | Confirmed ("Individual Sounds" button) | Confirmed | — | Yes, via Fabric screen events | Open (Q-SC9) |
| Source-specific rules (TNT vs Creeper) | None found | None found | None found | None found | **No** for shared events (§4) | Exclude |

**Takeaways.**
- Every mod researched works at the level of sound events. None claims to
  separate TNT from Creeper explosions, which matches §4.
- Extreme Sound Muffler and Decibel cap at the vanilla level for muting;
  Sound Controller and Enhanced Sound Control boost to 200%. Since vanilla
  clamps the final gain to 1.0 (§3.3), a boost is uneven, so it stays out.
- Regions, presets and preview are popular, but each adds UI and storage.
  They are deferred, not rejected.

## 3. Verified Minecraft 26.3 audio pipeline

All of this section was verified on 2026-11-23 with `javap` (signatures
and bytecode) against `minecraft-merged-deobf-26.3.jar` from the Loom
cache, plus the 26.3 asset index (`26.3-34.json`, `minecraft/sounds.json`).
Fabric API 0.166.2+26.3 was checked in the same way.

### 3.1 Five concepts that are not interchangeable

| Concept | 26.3 type | Example | Stable? | What the rule uses |
| --- | --- | --- | --- | --- |
| **Sound-event id** | `Identifier`, from `SoundInstance.getIdentifier()`; the registry form is `SoundEvent(Identifier location, Optional<Float> fixedRange)` | `minecraft:entity.generic.explode` | Yes, namespaced | **The rule key** |
| **Event definition** | `WeighedSoundEvents`: the weighted variant list and subtitle from `sounds.json`, returned by `SoundManager.getSoundEvent(Identifier)` | four variants, subtitle `subtitles.entity.generic.explode` | Changes when resource packs change | Display name (subtitle) only |
| **Resolved asset / variant** | `Sound`: `getLocation()`, `getPath()`, `shouldStream()`, its own volume and pitch (`SampledFloat`), weight, attenuation distance. `Sound.Type` is a file or a nested event | `random/explode3` | No: chosen randomly per play, replaceable by resource packs | Never |
| **Category** | `SoundSource` enum: `MASTER, MUSIC, RECORDS, WEATHER, BLOCKS, HOSTILE, NEUTRAL, PLAYERS, AMBIENT, VOICE, UI` | `BLOCKS` | Yes | Display and filter only |
| **Sound instance** | `SoundInstance` (one playback request): id, category, volume, pitch, x/y/z, `isRelative`, `isLooping`, delay, attenuation; `TickableSoundInstance` adds `tick()` and `isStopped()` | one explosion | Lives for one playback | Carries the id to the hook |
| **Producer** (entity, block, location) | Not part of `SoundInstance`. Only `EntityBoundSoundInstance` keeps a private `Entity`; positional sounds have coordinates only | the TNT entity | Often not available at all (§4) | Never in V1 |

Two vanilla examples show why the id, and not the asset, is the right key:
- `entity.tnt.primed` and `entity.creeper.primed` both play the asset
  `random/fuse`, but they are separate events. Muting one leaves the other.
- `entity.generic.explode` is one event with four assets
  (`random/explode1` to `explode4`). Keying on assets would need four rules
  and would break when a resource pack renames files.

The vanilla client defines 1,991 sound events in `sounds.json`.

### 3.2 Request and playback path

| Step | Verified signature | Behavior |
| --- | --- | --- |
| World request | `ClientLevel.playSound(...)`, `playLocalSound(...)`, `playSeededSound(...)`, `playPlayerSound(...)` | Builds a `SimpleSoundInstance` or `EntityBoundSoundInstance` |
| Server sound | `ClientboundSoundPacket` (`Holder<SoundEvent>`, category, x/y/z, volume, pitch, seed); `ClientboundSoundEntityPacket` | Positional sounds carry no producer |
| Entry | `SoundManager.play(SoundInstance) → SoundEngine.PlayResult` | Delegates to `SoundEngine.play` |
| Delayed | `SoundManager.playDelayed(SoundInstance, int)` | Queued in `SoundEngine.queuedSounds`; `play` runs when due |
| Ticking | `SoundManager.queueTickingSound(TickableSoundInstance)` | Started on the next engine tick |
| Resolution | `SoundInstance.getOrResolve(SoundManager) → WeighedSoundEvents` | Picks the variant `Sound`; unknown id → warning once, not played |
| Listener notification | `SoundEngine.notifyListeners(...)` → `SoundEventListener.onPlaySound(SoundInstance, WeighedSoundEvents, float range)` | Runs **before** the zero-volume check, so listeners hear about silent sounds too |
| Volume | `SoundEngine.calculateVolume(float, SoundSource)` | `clamp(volume, 0, 1) × clamp(Options.getFinalSoundSourceVolume(source), 0, 1) × gainBySource[source]` |
| Zero volume | in `play` | If the result is exactly 0: started silently only if `canStartSilent()` or the category is `MUSIC`; otherwise not played (`NOT_STARTED`) |
| Pitch | `SoundEngine.calculatePitch(SoundInstance)` | Unaffected by volume |
| Channel | `ChannelAccess.createHandle(Library.Pool)`: `STREAMING` if `Sound.shouldStream()`, else `STATIC` | The channel lambda sets pitch, volume, attenuation, looping, position and relative mode |
| Bookkeeping | `instanceToChannel`, `instanceBySource`, `soundDeleteTime`, `tickingSounds` | Tickable instances are added to `tickingSounds` |

`AbstractSoundInstance.getVolume()` returns the instance volume times the
variant's sampled `sounds.json` volume, so the variant volume is already
inside the value that `calculateVolume` receives.

### 3.3 Volume after start

| Path | Verified behavior |
| --- | --- |
| Ticking sounds | `tickInGameSound()` calls `tick()` on each `TickableSoundInstance`, then `calculateVolume(SoundInstance)` and `calculatePitch`, then sets volume, pitch and position on the channel every tick |
| Category change | `SoundManager.updateCategoryVolume(SoundSource, float)` and `refreshCategoryVolume(SoundSource)` recompute `calculateVolume(SoundInstance)` for every playing instance of that category. `MASTER` matches every instance |
| Looping subtitles | Every `LOOPING_SOUND_SUBTITLE_INTERVAL_TICKS` ticks, looping tickables notify listeners again, but only if their volume is above 0 |
| Manual loops | Sounds that loop and have a delay are re-queued by the engine (`shouldLoopManually`) and pass through `play` again |

All three paths run through the two `calculateVolume` overloads. This is
the narrow boundary the feature uses (§7).

Vanilla clamps the input volume and the category gain to the 0–1 range.
That is why a factor above 1.0 cannot reliably amplify a sound (§2.2).

### 3.4 Stopping, reload and devices

- `SoundManager.stop()`, `stop(SoundInstance)` and
  `stop(Identifier, SoundSource)` stop playback.
  `pauseAllExcept(SoundSource...)` and `resume()` pause and resume it.
- **Resource reload.** `SoundManager` is a
  `SimplePreparableReloadListener`. `prepare` reads `sounds.json` on a
  worker thread. `apply` rebuilds the `Identifier → WeighedSoundEvents`
  registry, then calls `SoundEngine.reload()`, which stops every sound and
  reloads OpenAL.
- **Device change.** `SoundEngine.tick` calls `reload()` when
  `shouldChangeDevice()` is true.
- `SoundManager.getAvailableSounds()` returns the ids of the current
  registry. It includes events that resource packs define without a
  `SoundEvent` registry entry.

Rules key on ids, so reloads and device changes need no special handling.
Sounds that were stopped by a reload simply start again through `play`.

### 3.5 Threading

- `SoundEngine.play`, `tick`, `refreshCategoryVolume` and both
  `calculateVolume` overloads run on the caller's thread. In vanilla that
  is the client thread: packet handlers call
  `PacketUtils.ensureRunningOnSameThread`, and ticking comes from
  `Minecraft.tick`.
- OpenAL work runs on the `SoundEngineExecutor` thread (a
  `BlockableEventLoop`) through `ChannelHandle.execute(Consumer<Channel>)`.
- Listeners (`SoundEventListener`) are called synchronously inside `play`
  and inside the engine tick, so they run on the client thread too.
- Other mods may call `SoundManager.play` from another thread. Vanilla does
  not support that (its maps are not thread-safe), but the feature must not
  make it worse (§9).

### 3.6 Fabric API

Fabric API 0.166.2+26.3 ships `fabric-sound-api-v1` 2.0.7. Its only public
type is `FabricSoundInstance`, with a `getAudioStream(...)` default method
for custom audio streams; its Mixin wraps `SoundBufferLibrary.getStream`.
There is **no** Fabric play event and no volume hook. Fabric therefore
offers nothing for this feature except screen events (Q-SC9).

Vanilla's own public `SoundManager.addListener(SoundEventListener)` and
`removeListener` are an observation-only hook, which is enough for the
recent-sounds list without a Mixin.

MixinExtras is already bundled with Fabric Loader and is used in this
repository (`@WrapOperation`, `@Local`).

## 4. TNT and source attribution

### 4.1 Which event each explosion uses (verified)

`Level.explode` overloads without an explicit sound pass
`SoundEvents.GENERIC_EXPLODE`. `ServerLevel.explode` runs a
`ServerExplosion` and sends a `ClientboundExplodePacket` to nearby players.

| Explosion | Server call (26.3 bytecode) | Explosion sound event | Category and volume on the client |
| --- | --- | --- | --- |
| TNT (`PrimedTnt`) | `Level.explode(…, ExplosionInteraction.TNT)`, power 4.0 by default (`explosionPower` field, settable by entity data) | `minecraft:entity.generic.explode` | `BLOCKS`, 4.0 |
| TNT minecart | `ServerLevel.explode(…, TNT)`, power depends on speed | `minecraft:entity.generic.explode` | `BLOCKS`, 4.0 |
| Creeper | `ServerLevel.explode(…, MOB)`, radius from the `explosionRadius` field (3 by default, doubled when charged) | `minecraft:entity.generic.explode` | `BLOCKS`, 4.0 |
| End crystal | `ServerLevel.explode(…, BLOCK)`, power 6.0 | `minecraft:entity.generic.explode` | `BLOCKS`, 4.0 |
| Bed (Nether/End) | `Level.explode(…, BLOCK)`, power 5.0 | `minecraft:entity.generic.explode` | `BLOCKS`, 4.0 |
| Respawn anchor (outside the Nether) | `ServerLevel.explode(…, BLOCK)`, power 5.0 | `minecraft:entity.generic.explode` | `BLOCKS`, 4.0 |
| Ghast fireball (`LargeFireball`), Wither (`WitherBoss`) | `explode(…, MOB)` | `minecraft:entity.generic.explode` | `BLOCKS`, 4.0 |
| Wind charge | 13-argument `Level.explode` with `WIND_CHARGE_BURST` | `minecraft:entity.wind_charge.wind_burst` (own event) | as sent |
| Modded explosion | Depends on the mod | `entity.generic.explode` if it uses the default overloads; its own event if it passes one | as sent |

On the client, `ClientPacketListener.handleExplosion` reads the packet
(`center`, `radius`, `blockCount`, `playerKnockback`, `explosionParticle`,
`explosionSound`, `blockParticles`, `playSound`). If `playSound` is true
(false when the exploding entity is silent), it calls
`ClientLevel.playLocalSound(center, explosionSound, BLOCKS, 4.0, pitch,
false)`. **The packet carries no entity id, entity type or cause.**

Related sounds that *are* separate events:

| Sound | Event | Independent? |
| --- | --- | --- |
| TNT fuse (ignition) | `minecraft:entity.tnt.primed` | Yes |
| Creeper hiss | `minecraft:entity.creeper.primed` | Yes, although it uses the same asset as the TNT fuse |
| Creeper hurt / death | `entity.creeper.hurt` / `entity.creeper.death` | Yes |

### 4.2 Can the client tell the producer?

| Approach | Classification | Reason |
| --- | --- | --- |
| Rule per sound-event id | **Reliably feasible** | The id is on every `SoundInstance` |
| Rule per vanilla category | Already in vanilla | The category sliders |
| Infer TNT from the explosion radius in the packet | **Unreliable** | TNT power and Creeper radius come from entity data; a charged Creeper (6) equals an end crystal (6); TNT minecart power varies; plugins and mods change values |
| Correlate with a `PrimedTnt` entity disappearing near the center | **Unreliable**, **not recommended** | No ordering guarantee between entity removal and the explosion packet; chain reactions; entities may be outside tracking range while the sound is in range; needs a Mixin in packet handling and a heuristic window |
| Particle type or block count in the packet | **Unreliable** | Shared by all default explosions; depends on terrain |
| Server tells the client the cause | **Requires server support** | Needs a server mod or packets (excluded) |
| Rule per entity type for entity-bound sounds | **Feasible with limitations** | Only `EntityBoundSoundInstance` keeps the entity (private field, accessor Mixin needed); positional sounds, explosions included, have none |
| Rule per individual entity | **Not recommended** | Same limits, plus entity identity in config is fragile and personal |
| Radius or region rule around the player | **Feasible** | `SoundInstance` has coordinates; it is a different feature (§14) |

### 4.3 Conclusion [Proposed]

- A vanilla client **cannot** reliably mute TNT explosions without also
  muting Creeper, end crystal, bed, respawn anchor, fireball and Wither
  explosions. They all reach the client as the same event,
  `minecraft:entity.generic.explode`, in the same category, with no
  producer.
- V1 offers **event-level** control only and must say so plainly in the UI
  (§6.5) and docs. It never promises TNT-only muting.
- What a player *can* do for TNT in V1: mute or lower
  `entity.generic.explode` (all default explosions), and separately mute
  the TNT fuse `entity.tnt.primed` while keeping the Creeper hiss.
- Source-specific rules are **[Deferred]** until a reliable signal exists;
  heuristics are excluded.

## 5. V1 sound control

### 5.1 Per-event volume [Proposed]

- **Range.** An integer percentage from 0 to 100 per sound-event id, in 1%
  steps. 0% is "muted". 100% is "no rule" and is never stored.
- **Effective volume.** `vanilla result of calculateVolume × percent / 100`.
  Master and category sliders, instance volume, the variant volume, pitch,
  attenuation, position and relative mode all stay exactly vanilla, so
  the result can never exceed vanilla.
- **Mute.** At 0%, vanilla's own zero-volume branch applies: the sound is
  not started (`NOT_STARTED`), unless it is a sound vanilla starts silently
  (`canStartSilent()` or category `MUSIC`), which keeps playing inaudibly.
  No channel is used for a muted non-tickable sound. Vanilla debug logging
  ("volume was zero") is unchanged.
- **Mute toggle.** Mute sets 0%. Unmute restores the value the slider held
  before muting in this screen session, otherwise 100% (Q-SC5).
- **Match.** Exact match on the requested event id
  (`SoundInstance.getIdentifier()`). No wildcards in V1.

### 5.2 Live behavior for each kind of sound [Proposed]

Changes take effect when the player confirms the settings (§6.4). Then the
feature publishes the new rule table and calls
`SoundManager.refreshCategoryVolume(SoundSource.MASTER)` on the client
thread. This vanilla method recomputes the gain of every playing instance
through the hooked `calculateVolume(SoundInstance)`.

| Sound kind | Already playing when the rule changes | Next playback |
| --- | --- | --- |
| Short one-shot (static buffer) | Gain updated at once; it finishes at the new volume | New volume |
| Ticking (minecart, bees, elytra, guardian beam, some mod sounds) | Updated at once, and again every tick | New volume |
| Looping sounds (vanilla loop or manual loop with delay) | Updated at once; manual loops use the new volume on every restart | New volume |
| Streaming (music, discs, long ambience) | Gain updated at once on the streaming channel | New volume |
| Sound muted before it started, not silent-capable | Was never started; unmuting cannot bring it back | Plays at the new volume |
| Sound muted before it started, silent-capable (music, ticking with `canStartSilent`) | Was started silently; becomes audible on unmute | New volume |
| Delayed sound still in the queue | The rule is checked when it starts | New volume |

Disabling the feature works the same way: an empty table, then a refresh,
so playing sounds return to vanilla volume.

### 5.3 Recently heard sounds [Proposed]

- **Capture.** A vanilla `SoundEventListener` registered with
  `SoundManager.addListener` while the feature is enabled, and removed on
  disable. No Mixin. It receives every sound that resolves, **including
  muted ones**, because listeners are notified before the zero-volume
  check (§3.2).
- **Bounded.** At most 128 distinct event ids (Q-SC6), least recently
  heard evicted first. One entry per id, not per play.
- **Per entry:** event id, category of the last play, last-heard time
  (client tick count, shown as "5 s ago" / "2 min ago"), and occurrence
  count this session, capped for display at "999+". Display name, mod
  name and current volume are looked up when the screen draws.
- **Muted sounds stay** in the list with a muted mark, so they can be
  restored where they were found.
- **Not recorded:** coordinates, producer entity, player names, server
  address, world name, sound variant. Nothing is logged per sound.
- **Session-only.** The list lives in memory, is cleared when the game
  closes or the feature is disabled, and is never written to disk (Q-SC7).
- **Limits.** If no audio device is loaded, vanilla does not run `play`
  past its first check, so nothing is captured; the screen says so (§6.5).
  Sounds a mod plays outside `SoundManager` never appear.

### 5.4 Search and discovery [Proposed]

- **Catalog.** Built when the screen opens from
  `SoundManager.getAvailableSounds()`. That covers vanilla, mods using
  standard registration and `sounds.json`, and resource-pack events. It is
  rebuilt after a resource reload. There is no hardcoded list.
- **Matches** (case-insensitive substring, all words must match):
  - friendly name in the current language;
  - full id (`minecraft:entity.villager.ambient`);
  - namespace or mod display name;
  - category name, localized or raw (`hostile`).
- **Friendly name.** The event's subtitle (`WeighedSoundEvents.getSubtitle()`)
  if it resolves in the current language, otherwise the id path
  (`entity.villager.ambient`). Mod name from Fabric Loader metadata,
  falling back to the namespace (same rule as Target Info Q-TI8; see
  Q-SC10 on sharing it).
- **Category for catalog rows** that were never heard: unknown until
  played (events don't declare a category; the instance does). The row
  shows no category chip. Heard events show the last-heard category.
- **Configured but not installed.** A rule for an id that is not in the
  catalog (mod removed) is still listed in the Rules tab, marked "Not
  installed", and can be reset (§8.3).

## 6. UI and interaction [Proposed]

### 6.1 Entry points

- The QoL: Unified settings screen → Sound Control → button
  **"Open sound list"**, using the existing `FeatureActions` mechanism, as
  Keyboard Map does. It is shown only while the feature is active.
- No key binding (none needed; the brief asks for none).
- Optional: a **"Sound Control…"** button in vanilla's Music & Sound
  options screen, through Fabric `ScreenEvents.AFTER_INIT` (Q-SC9).

### 6.2 Layout

A native `Screen` built from vanilla widgets, styled like the existing
QoL: Unified screens: title at the top, scrolling `RowList`-style list, and
Done/Cancel at the bottom.

```
┌──────────────────────── Sound Control ─────────────────────────┐
│ [ Recent ] [ Rules (3) ] [ All sounds ]      Search: [_______] │
│ Category: [ Any ▾ ]  Mod: [ Any ▾ ]                            │
├────────────────────────────────────────────────────────────────┤
│ Explosion                          [■■■■■■□□□□  60%] [🔇] [↺]  │
│ minecraft:entity.generic.explode · Blocks · Minecraft · 4 s    │
│ Villager mumbles           MUTED   [□□□□□□□□□□   0%] [🔈] [↺]  │
│ minecraft:entity.villager.ambient · Neutral · Minecraft · 1 min│
│ Fuse                               [■■■■■■■■■■ 100%] [🔇] [↺]  │
│ minecraft:entity.tnt.primed · Blocks · Minecraft · 12 s        │
├────────────────────────────────────────────────────────────────┤
│ Event-level only: sounds that share an event change together.  │
│                     [ Done ]   [ Cancel ]                      │
└────────────────────────────────────────────────────────────────┘
```

- **Rows** are two lines, 24 GUI pixels high. Line 1: friendly name,
  muted badge, slider, mute/unmute button, reset button. Line 2, in grey
  text: id · category · mod · last heard. Long text is cut with an
  ellipsis, and the full text shows in a tooltip.
- **Tabs.**
  - Recent: most recent first.
  - Rules: every stored rule, sorted by name, with the count in the tab
    label.
  - All sounds: the catalog. It shows "Type to search 1,991 sounds" until
    the player types at least 2 characters or picks a filter, so thousands
    of rows never appear at once.
- **Filters:** category and mod cycle buttons apply to the current tab.
- **Reset** returns one event to 100% (removes the rule). A **"Reset
  all"** button in the Rules tab asks for confirmation first.
- **Slider** uses vanilla `AbstractSliderButton`, with a label like `60%`
  or `Muted`. Arrow keys change it by 1%.

### 6.3 Sizes, input and language

- **Smallest layout.** Vanilla limits the GUI scale to what fits, so the
  smallest is about 320×240 GUI pixels (854×480 at scale 2). At widths
  under 400 GUI pixels the filters move under the tabs and line 2
  shortens to id · category. The slider keeps at least 80 pixels;
  buttons are 20×20.
- **Mouse:** scroll, click, drag sliders. **Keyboard:** Tab/Shift+Tab
  focus, arrows for sliders, Enter on buttons, typing goes to the search
  box, Escape = Cancel.
- **Languages.** All labels use translation keys in `en_us` and `pt_br`
  (for example `qol_unified.feature.sound_control.*`). Friendly names come
  from the game's own subtitle translations. Ids are never translated.

### 6.4 Applying changes

- The screen edits the shared settings draft, like Keyboard Map. Its Done
  keeps the edits in the draft; Cancel or Escape throws away what was
  changed in this screen.
- Rules are saved and applied when the player presses Done on the main
  QoL: Unified settings screen. This matches the approved M0 transaction
  rule (save before runtime apply) [Approved 2026-11-21, M0].
- Whether the sound screen should preview edits live before confirmation
  is Q-SC4.
- The generic feature settings screen shows a one-line summary
  ("3 sound rules") instead of a control for the rule table.

### 6.5 Empty and limit states

| State | Message |
| --- | --- |
| Recent, nothing heard yet | "No sounds heard yet. Play for a moment and come back, or search all sounds." plus a button that opens the All sounds tab |
| Audio device not available | "Minecraft has no audio device right now, so no sounds are being heard." |
| Rules tab empty | "No sounds changed. Use the slider or the mute button on any sound." |
| Search, no result | "No sound matches \"…\"." |
| Footer (always) | "Sounds that share an event change together. For example, TNT and Creeper explosions are one event." |

## 7. Audio interception strategy [Proposed]

### 7.1 Options evaluated

| Option | Verdict | Reason |
| --- | --- | --- |
| Fabric event | Not available | `fabric-sound-api-v1` has no play or volume hook (§3.6) |
| `SoundEventListener` | **Use for capture only** | Observe-only; cannot change volume |
| Cancel at `SoundManager.play` HEAD | Rejected | Handles only one-shots, can't do partial volume or update playing sounds, and changes the `PlayResult` that callers see |
| Wrap or replace the `SoundInstance` | Rejected | Allocates per play, breaks identity checks such as `isActive` and `stop(instance)`, and conflicts with mods that use custom instance classes |
| Modify `SoundInstance.getVolume()` (as Decibel does) | Rejected | That value also sets the attenuation range (`Sound.getAttenuationDistance(volume)`) and the subtitle range, so it would change spatial behavior |
| **Multiply the result of `SoundEngine.calculateVolume`** | **Chosen** | After the category math and clamping; the one place that sets the gain for start, tick and refresh; leaves pitch, range, position and scheduling alone |

### 7.2 Chosen design

One client Mixin, `SoundEngineVolumeMixin`, on `SoundEngine`, with two
MixinExtras handlers that call the same static function
`SoundControlHooks.factor(SoundInstance)`:

1. `@ModifyReturnValue` on
   `calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F`.
   This covers ticking sounds and category refreshes.
2. `@ModifyExpressionValue` on the
   `calculateVolume(FLnet/minecraft/sounds/SoundSource;)F` call inside
   `play(SoundInstance)`, with the `SoundInstance` argument captured. This
   covers the starting gain and vanilla's zero-volume decision.

`factor` returns 1.0 when the feature is inactive or the table is empty,
otherwise `percent / 100f` for the id (1.0 if absent). The result is
multiplied, never replaced. Both handlers chain with other mods' handlers
of the same kind.

What stays unchanged: pitch, attenuation range, position, relative mode,
looping, delay and queue, `PlayResult` for unmuted sounds, the listener
calls, the stream/static pool choice, and every sound without a rule.

### 7.3 Positional and non-positional sounds

Both go through the same two calls. Relative (UI, music) and world sounds
are scaled the same way; spatial mixing is untouched because only the gain
changes.

### 7.4 Lifecycle

- The Mixin is always applied, as the existing HUD Mixins are, and is
  inert while the feature is disabled: one volatile read, then return.
- **Enable:** publish the rule table, add the listener, refresh `MASTER`.
- **Disable:** publish an empty table, remove the listener, clear the
  recent list, refresh `MASTER`. It never throws into the audio path; on
  any exception the hook returns 1.0 and logs once with the feature id.

### 7.5 Failure safety

- An unexpected exception in `factor` is caught; the sound plays at
  vanilla volume. It is logged once per session with `sound_control`.
- If the Mixin target is missing (a future Minecraft change), the client
  fails at startup (`defaultRequire: 1`), as the existing Mixins do. This
  is caught in development, not in silent production.

## 8. Persistence [Proposed]

### 8.1 Representation

Stored in the existing `config.json` under the feature's `settings`, with
no separate file:

```json
{
  "schemaVersion": 1,
  "features": {
    "sound_control": {
      "settings": {
        "event_volumes": {
          "minecraft:entity.generic.explode": 0,
          "minecraft:entity.villager.ambient": 25,
          "examplemod:machine.hum": 50
        }
      }
    }
  }
}
```

- Key: a namespaced sound-event id in Minecraft identifier syntax:
  namespace `[a-z0-9_.-]+`, path `[a-z0-9_.\-/]+`, joined by `:`, at most
  256 characters. An id without a namespace is invalid; it is not read as
  `minecraft:`.
- Value: an integer percentage, 0–100. 100 is valid, but the UI never
  writes it: reset removes the entry.
- Versioning: the global `schemaVersion` and its migrations (CFG-P5). A
  later shape change, such as remembering a pre-mute volume, would be a
  migration or a new setting key.
- Default: an empty object, so no rules.
- Bound: at most 4,096 entries (Q-SC6). Extra entries are ignored at
  runtime, kept in the file and reported once in the log.

### 8.2 Fit with the Configuration Service

Today's `SettingDescriptor` is a sealed interface with boolean, integer
and enum settings. Values are scalar JSON primitives that are validated as
a whole, and an invalid value is replaced by its default in memory. A
dynamic map **does not fit** as it stands.

Smallest compatible extension (a Core change that the PO or tech lead
must approve, Q-SC3). It is not a new configuration framework:

| Part | Change |
| --- | --- |
| `SettingDescriptor` | New variant `IntegerMapSetting(key, keyPattern, min, max, maxEntries, applyMode)`; default = empty map; value = immutable sorted `Map<String,Integer>` |
| `ConfigurationService` load | Reads a JSON object; validates **each entry**: valid entries go into the value, invalid ones (bad id, non-integer, out of range) are skipped and logged once as a count |
| `ConfigurationService` save | When the setting changed, writes the new valid entries **and keeps the original invalid entries unchanged** unless the player set the same key. This follows CFG-P6 and the "keep the original value until the player changes that setting" rule at entry level |
| `SettingsDraft` | A typed setter for map values |
| Generic settings screen | Shows a summary row for map settings (no editor) |
| Tests | Unit tests for parsing, preservation, bounds and round trip |

The Core stays free of Minecraft classes: the id pattern is a plain regex,
not `Identifier`.

### 8.3 Safe handling

| Situation | Behavior |
| --- | --- |
| Id from a removed mod | Kept and listed as "Not installed"; it applies again if the mod comes back |
| Id valid but never played | Kept; it has no effect until played |
| Invalid id or value | Ignored at runtime, kept in the file, logged once (count only) |
| `event_volumes` not an object | Default (empty) at runtime; the original is kept until the player changes rules (existing policy) |
| Newer `schemaVersion` | Existing read-only behavior |
| `options.txt` | Never read or written by the feature; vanilla category sliders are untouched |

Saving happens only on settings confirmation (existing policy), never on
the audio thread and never per sound.

## 9. Performance and threading [Proposed]

| Concern | Design |
| --- | --- |
| Lookup | Immutable `HashMap<Identifier, Float>` built from the snapshot when settings apply, published through one `volatile` field. Constant time; no allocation; an empty table is skipped with a single check |
| Thread safety of rules | Immutable table and volatile publication, safe on any thread, including a mod calling `play` off-thread |
| Recent list | One small `synchronized` access-ordered map, capped at 128 entries. An already-known id only updates a counter and a tick value, so no allocation. The UI copies the list once when the screen opens and then once per second while it is open |
| Off-thread capture | Allowed (the lock makes it safe); the work stays tiny |
| Disk I/O | None in the audio path; saves happen only on settings confirmation |
| Logging | Nothing per sound. One warning per session for hook errors or ignored config entries |
| Catalog | Built only when the screen opens (about 2,000 ids). Search filters an in-memory list on each keystroke; nothing is built at startup |
| Startup | No work at client start beyond registering the feature; the listener is added on enable; no eager loading |
| Reload and device change | No feature state depends on the audio device or `sounds.json`; the catalog is rebuilt the next time the screen opens |
| Memory | At most 128 recent entries and 4,096 rules |

No numeric FPS or latency claims are made. A profiling pass during
implementation (the debug profiler or spark, in a busy mob farm) is part
of QA (§12).

## 10. Compatibility [Proposed]

| Mod / situation | Verified fact | Interaction | Proposal |
| --- | --- | --- | --- |
| Extreme Sound Muffler 4.06 (`extremesoundmuffler`) | Mixins on both `SoundEngine.calculateVolume` overloads | Both mods scale the same gain. Result: volumes multiply, or one mod's handler bypasses the other, depending on injection type (not verified) | Declare as a soft conflict: by Core policy, disabled by default unless the player chooses; an explicit choice wins with a warning |
| Sound Controller 1.4.1 (`soundcontroller`) | Mixins on `SoundEngine.play`, `calculateVolume(SoundInstance)`, the `canStartSilent` call, `SubtitleOverlay` | Same as above | Soft conflict |
| Decibel 1.0.2 (`decibel`) | Mixins on `AbstractSoundInstance.getVolume` and `SoundEngine.calculateVolume(SoundInstance)` (RETURN) | Same as above | Soft conflict |
| Sound Control (pohci), Enhanced Sound Control | No 26.3 build | — | Not declared until a 26.3 build exists and its Fabric id is checked from the JAR |
| Mods with their own sounds (standard registration) | `getIdentifier()` is namespaced | Fully supported, including rules saved while the mod is absent | — |
| Mods with custom `SoundInstance` / `FabricSoundInstance` streams | Still go through `SoundEngine.play` | Supported | — |
| Mods that use OpenAL directly (for example voice chat) | They bypass `SoundManager` | Not controlled, not affected | Documented limitation, no conflict |
| Mods that add audio effects (reverb, occlusion) without changing the gain math | Not verified per mod | Expected to coexist | No conflict declared without evidence |
| Resource packs replacing sound files | Assets change, event ids don't | Rules keep working | — |
| Resource packs adding events | Present in `getAvailableSounds()` | Searchable and controllable | — |
| Vanilla volume sliders | Multiplied before the feature's factor | Unchanged behavior; never louder | — |
| Vanilla subtitles | Listeners are notified before the volume check | A muted sound still shows its subtitle, as vanilla does with a category at 0% | Keep vanilla behavior (Q-SC8) |
| Existing QoL features | No shared code paths | None | — |

No `breaks` entry in `fabric.mod.json`, so the player keeps the choice.

## 11. Acceptance criteria [Proposed]

| Id | Criterion |
| --- | --- |
| SC-AC-1 | With `minecraft:entity.villager.ambient` muted, villager idle sounds are inaudible and `SoundManager.play` returns `NOT_STARTED` for them; villager hurt, trade and death sounds still play |
| SC-AC-2 | At 25%, 50% and 100%, the gain the hook computes equals the vanilla `calculateVolume` result × 0.25, 0.5 and 1.0 (within float tolerance); pitch and attenuation range are identical to vanilla |
| SC-AC-3 | Sounds without a rule have exactly vanilla gain, pitch, position and `PlayResult`, with the feature enabled or disabled |
| SC-AC-4 | Master and category sliders still change every sound, ruled or not; a ruled sound is never louder than with the feature disabled; `options.txt` is unchanged by the feature |
| SC-AC-5 | Rules survive a restart and appear in `config.json` under `sound_control.settings.event_volumes`; resetting a sound removes its entry |
| SC-AC-6 | A muted event still appears in the Recent tab, marked muted, and can be unmuted from there |
| SC-AC-7 | A dev-fixture mod sound (namespace `qol_unified_dev`) is captured in Recent, found by its mod name in All sounds, and can be muted |
| SC-AC-8 | A rule for an id that is not installed stays in the file, shows as "Not installed" in Rules, can be reset, and causes no error; invalid ids and out-of-range values are ignored at runtime and kept in the file |
| SC-AC-9 | Changing a rule while a looping, ticking or streaming sound plays (minecart, beacon or portal ambience, music disc) changes its volume at once after confirmation; unmuting a silently started sound makes it audible; a muted one-shot that was not started is not replayed |
| SC-AC-10 | After F3+T (resource reload) and after an audio device change, rules still apply and the catalog lists the new resource-pack events |
| SC-AC-11 | Rules apply after settings confirmation without restart; disabling the feature restores vanilla volume on sounds that are playing |
| SC-AC-12 | No crash, deadlock or lost unruled sound in a 10-minute session near a mob farm and a TNT test. Profiling shows no measurable hook cost when no rules exist, and no allocation per played sound for known ids. No per-sound log lines at default log level |
| SC-AC-13 | Every screen string exists in `en_us` and `pt_br` (`LocalizationTest`); the screen is fully usable at GUI scales 1–4 and 854×480, with mouse only and with keyboard only |
| SC-AC-14 | Food HUD, Chat Heads, Keyboard Map and Target Info game tests still pass |
| SC-AC-15 | Works on a vanilla-protocol server without any server mod; the feature sends no packets |
| SC-AC-16 | With Extreme Sound Muffler, Sound Controller or Decibel installed, the conflict is reported and the feature is off by default |
| SC-AC-17 | The recent list never holds more than 128 ids and contains no coordinates, entity or player data |

### 11.1 TNT scenario (SC-AC-18)

Setup: a flat test world with a TNT block and a Creeper, the player 10
blocks away.

| Action | Expected with rule `entity.tnt.primed` = 0% | Expected with rule `entity.generic.explode` = 0% |
| --- | --- | --- |
| Ignite TNT | No fuse hiss | Fuse hiss plays |
| TNT explodes | Explosion plays | **No explosion sound** |
| Creeper starts to explode | Creeper hiss plays | Creeper hiss plays |
| Creeper explodes | Explosion plays | **No explosion sound (the same event as TNT)** |
| End crystal, bed in the Nether, respawn anchor in the Overworld | Explosion plays | **No explosion sound** |
| Wind charge burst | Plays | Plays (its own event) |
| Explosion particles, knockback, block damage | Unchanged | Unchanged |

Pass: these results hold exactly. The UI footer and docs state that TNT
and Creeper explosions cannot be separated.

## 12. QA plan [Proposed]

### 12.1 Automated

- **Unit tests (JUnit 5, no game):**
  - percent-to-factor math;
  - the id pattern;
  - the rule table (empty fast path, lookup, bounds);
  - `IntegerMapSetting` parsing, per-entry preservation, round trip and
    the entry limit;
  - recent-list capacity and eviction order with counts;
  - search matching (name, id, namespace, category; all words; case);
  - mute/unmute restore logic.
- **Client game test** `runGameTestSoundcontrol` in
  `build/run-gametest-soundcontrol`:
  - registers a dev-fixture sound event;
  - plays vanilla and fixture sounds through `SoundManager.play` with and
    without rules;
  - asserts the `PlayResult` and the recent-list contents;
  - checks the hook's computed gain against vanilla through a test-only
    path;
  - changes rules while a looping sound plays;
  - restarts and reloads resources;
  - opens the screen at GUI scales 1–4 for screenshots.

  If the test environment has no audio device (`SoundEngine` not loaded),
  the playback assertions are reported as skipped, not passed.
- **Regression:** the existing game tests and `LocalizationTest`.

### 12.2 Manual (Product Owner)

Use Prism and the existing server; change no server configuration, plugin,
Prism instance or world.

1. Mute villager ambience in a village; confirm other villager sounds.
2. Set chickens to 25%, cows to 50%; compare by ear with the feature off.
3. Run the TNT scenario (§11.1) in a disposable creative world.
4. Play a music disc and stand in a minecart; change the rule while they
   play.
5. Restart the game; check the rules and that Recent starts empty.
6. Switch the audio output device during play; press F3+T.
7. Use a resource pack that replaces a sound; check that the rule still
   applies.
8. Check GUI scales 1–4, an 854×480 window, en_us and pt_br, keyboard only.
9. In a disposable profile, install Extreme Sound Muffler 4.06 and check
   the conflict notice.

## 13. Open Product Owner decisions

- **Q-SC1 [Open]** Approve the identity in §1.1: id `sound_control`, M1.5,
  enabled by default, LIVE.
- **Q-SC2 [Open]** Add a new category **Audio** (`audio`) to the approved
  category list, or place the feature in an existing one (closest:
  Performance & Network or Visuals; neither fits well). Proposal: add
  `audio`.
- **Q-SC3 [Open]** Approve the Core extension `IntegerMapSetting` with
  per-entry validation and preservation (§8.2). This is an M0 Core change,
  so the M0 specification and the configuration policy doc would be
  updated with it.
- **Q-SC4 [Open]** Should slider changes preview live while the sound
  screen is open (and revert on Cancel), or apply only on settings
  confirmation as M0 requires today? Proposal: confirmation only in V1.
- **Q-SC5 [Open]** Mute memory. Proposal: one stored integer per event,
  with an unmute that restores the in-screen value (else 100%).
  Alternative: store volume and muted flag separately so the pre-mute
  volume survives restarts.
- **Q-SC6 [Open]** Limits: 128 recent entries; 4,096 stored rules.
- **Q-SC7 [Open]** Recent sounds session-only (proposal) or persisted.
- **Q-SC8 [Open]** Subtitles of muted sounds: keep vanilla behavior
  (shown; proposal) or hide them.
- **Q-SC9 [Open]** Add a "Sound Control…" button to vanilla's Music &
  Sound options screen (proposal: yes; that is where players look).
- **Q-SC10 [Open]** The friendly mod name lookup already exists inside
  Target Info. Feature isolation forbids reusing its private code: move a
  small helper to the Core, or duplicate the one-line lookup.
- **Q-SC11 [Open]** Approve the soft conflicts with Extreme Sound Muffler,
  Sound Controller and Decibel (ids verified from their 26.3 JARs, §2.1).
- **Q-SC12 [Open]** Approve the V1 scope in §14 and the event-level-only
  TNT conclusion in §4.3.

## 14. V1 scope and deferred features [Proposed]

| Capability | Classification | Reason |
| --- | --- | --- |
| Per-event volume 0–100% and mute | **V1** | Core need |
| Bounded recent-sounds list | **V1** | Finding the sound without knowing its id |
| Search and filters (name, id, mod, category) | **V1** | Discovery among about 2,000 events |
| Rule persistence in `config.json` | **V1** | Required |
| Native settings UI | **V1** | Project convention |
| LIVE application, including playing sounds | **V1** | Possible through `refreshCategoryVolume` (§5.2) |
| Vanilla and modded events | **V1** | Same path |
| Streaming and looping sound updates | **V1** | Same path, no extra cost |
| TNT-only explosion suppression | **Exclude** | Not possible from the client without unreliable heuristics (§4) |
| Entity-type rules | **[Deferred]** | Only for entity-bound sounds; needs an accessor Mixin; partial coverage would confuse |
| Rules for one specific entity | **Exclude** | Fragile identity, personal data in config |
| Sound-radius filters | **[Deferred]** | Feasible (coordinates exist); separate design |
| Region-specific muting | **[Deferred]** | Needs per-world/per-server config (CFG-P2 deferred) |
| Volume above 100% | **Exclude** from V1 | Vanilla clamps the gain; uneven results; the brief says never by default |
| Sound preview / playback | **[Deferred]** | Useful, but adds audio from the UI and pitch/variant questions |
| Saved sound profiles / presets | **[Deferred]** | More UI and storage |
| Per-world / per-server rules | **[Deferred]** | Blocked on Q-C2/Q-C3 |
| Import / export | **[Deferred]** | Rules are already plain JSON in `config.json` |
| Keyboard shortcuts (mute last sound and similar) | **[Deferred]** | No demonstrated need; the brief asks for none |
| Wildcard or pattern rules (`entity.villager.*`) | **[Deferred]** | Ambiguity with ids; revisit with feedback |
| Hiding subtitles of muted sounds | **[Deferred]** | Q-SC8 |
