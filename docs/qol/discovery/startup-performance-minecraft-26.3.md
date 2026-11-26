# Discovery: Startup Performance on Minecraft 26.3

Status labels follow [AGENTS.md](../../../AGENTS.md#decision-status-labels).
This is a discovery report (M0.3). It was closed on 2026-11-21 with the
technical lead's [decisions](#decisions-2026-11-21). Recommendations not
covered by those decisions remain **[Proposed]**, and no startup
optimization is scheduled.

Date of investigation: 2026-11-21. Reserved feature id: `fast_startup`
(Q-SP5).

## 1. Executive summary

- On the test machine, vanilla 26.3 reaches the end of its initial resource
  load in a median **7.5 s**. Fabric Loader + Fabric API adds a median
  **≈3.2 s** (10.7 s). Adding QoL: Unified adds a further **≈0.3 s**
  (11.0 s), measured only in the development environment.
- Almost all of the Fabric overhead (**≈2.5 s**) happens **before the render
  thread starts** (loader start-up, Mixin setup and class transformation,
  game bootstrap). The initial resource reload itself takes only **≈1.0–1.2 s**
  in all three configurations.
- The best-known startup mods do not target Fabric 26.3. LazyDFU is
  obsolete: vanilla 26.3 already does selective, background DFU optimization.
  ModernFix supports 26.1.x on **NeoForge only**. Fastload targets world
  loading and stops at 1.20.1. No mod named "FasLoad" could be found.
- Conclusion: with vanilla assets there is **little startup time available
  to a client mod to win back**. The largest controllable factor is our own
  mod's cost. The top opportunities are:
  1. a startup budget for QoL: Unified itself, plus measurement;
  2. opt-in startup timing diagnostics;
  3. caching resource-pack file lookups. This one is only worth it if
     profiling with many packs/mods shows that filesystem access dominates.
- Startup optimizations that use Mixins need the configuration **before**
  normal client initialization. The proposed Core contracts support this with
  one small adjustment: a read-only early configuration read
  ([§6](#6-core-architecture-implications)).

## 2. Research findings

Confirmed = checked in source code, official metadata or the 26.3 game jar.
Assumption = not verified.

### 2.1 LazyDFU

| Aspect | Finding |
| --- | --- |
| Source | [astei/lazydfu](https://github.com/astei/lazydfu) (MIT), [Modrinth](https://modrinth.com/mod/lazydfu) |
| Versions | Last listed game version 1.20.6; Fabric only; repository last pushed 2024-01 (confirmed) |
| Technique | Defers DataFixerUpper rule optimization until data actually needs upgrading (README, confirmed) |
| 26.3 status | **Already handled by Minecraft.** `DataFixers.optimize(Set<TypeReference>)` exists in the 26.3 jar, and every benchmark run logs `307 Datafixer optimizations took ~280 milliseconds` on a separate `Datafixer Bootstrap` thread (confirmed) |
| Needs | Mixin into DFU bootstrap |
| Risk | Lag spike on the first data upgrade; not relevant now |

### 2.2 ModernFix

| Aspect | Finding |
| --- | --- |
| Source | [embeddedt/ModernFix](https://github.com/embeddedt/ModernFix) (LGPL-3.0), [Modrinth](https://modrinth.com/mod/modernfix), patch docs in the [project wiki](https://github.com/embeddedt/ModernFix/wiki/Summary-of-Patches) |
| Versions | Branch `26.1` targets 26.1.2; Modrinth builds for 26.1.2 are **NeoForge only**. The newest Fabric builds are for 1.20.1/1.21.1. No 26.3 build (confirmed via Modrinth API) |
| Startup-relevant patches (26.1 branch) | `perf.resourcepacks` (caches resource-pack listings and existence checks; documented as "a key optimization" because launches are bottlenecked by filesystem access), `perf.dynamic_resources` (loads/bakes models on demand), `perf.dynamic_dfu` (avoids loading DFU until needed), `perf.dynamic_languages`, `perf.dedicated_reload_executor`, `perf.thread_priorities`, `perf.lazy_search_tree_registry`, `feature.measure_time` (bootstrap/reload timing) |
| 26.3 status | DFU: handled by vanilla (see 2.1). The others are **unverified for 26.3**; whether vanilla resource packs are still filesystem-bound is an assumption to be profiled |
| Needs | Many Mixins, applied at class load; options gated by a Mixin config plugin that reads a properties file early |
| Risk | `dynamic_resources` changes model loading and carries per-mod compatibility patches (AE2, CTM, Refined Storage…), which shows it is high risk |
| License note | LGPL-3.0: code must not be copied into this project; reimplementing ideas from documented behavior is acceptable |

### 2.3 Fastload ("FasLoad")

| Aspect | Finding |
| --- | --- |
| Source | [BumbleSoftware/Fastload](https://github.com/BumbleSoftware/Fastload) (LGPL-2.1), [Modrinth](https://modrinth.com/mod/fastload) |
| Versions | Up to 1.20.1; last updated 2023-08 (confirmed) |
| Technique | Changes **world** loading (spawn chunk preparation), not game startup (Modrinth description) |
| 26.3 status | Not applicable to this investigation (world loading is secondary) |

No project called "FasLoad" was found on GitHub or Modrinth. Fastload is
assumed to be the intended mod (see Q-SP1).

### 2.4 Other relevant work

- **Smooth Boot**-style thread-priority tuning: ModernFix includes an
  equivalent (`perf.thread_priorities`). It improves responsiveness on
  low-core machines rather than total time. Not verified for 26.3.
- **JVM-level start-up**: Java 25 offers ahead-of-time class loading and
  caching (JEPs 483/514/515). These are JVM launch flags, controlled by the
  launcher, not by a mod. Benefit under Fabric's custom class loader (Knot)
  is an **unverified assumption**.

## 3. Baseline methodology and results

### 3.1 Setup

- Hardware/OS: Windows 11 Pro 10.0.26200, AMD Radeon RX 9070 (OpenGL
  backend), same machine for all runs.
- Java: Temurin 25.0.5.1, identical JVM arguments for all configurations:
  `-Xmx4G -XX:StackShadowPages=32 --sun-misc-unsafe-memory-access=allow
  --enable-native-access=ALL-UNNAMED`, plus the locale and encoding flags
  that Loom uses.
- Game files: the 26.3 client jar and libraries from the Loom cache, the
  same asset index (`26.3-34`), a separate throw-away game directory per
  configuration with a copy of the same `options.txt` (pt_br, windowed,
  render distance 16). No player data was touched.
- Configurations:
  - **A** vanilla: `net.minecraft.client.main.Main` run directly. The Fabric
    jars stay on the classpath but are inert.
  - **B** Fabric Loader 0.20.1 + Fabric API 0.166.2+26.3, launched through
    Loom's dev launcher, with QoL: Unified's output removed from the
    classpath.
  - **C** as B, plus QoL: Unified (from `build/classes`).
- Order: interleaved A,B,C × 6 rounds to spread drift. The first round is
  reported separately; rounds 2–6 (n = 5) are "warm".
- Timing: a harness started the JVM and timestamped each stdout line with a
  monotonic clock (ms resolution); time 0 = process spawn. Memory was read
  20 s after the resource-load marker: process working set, and Java heap
  used via `jcmd GC.heap_info`. The process was then killed. The harness was
  kept out of the repository (see Q-SP4).

### 3.2 Metrics and their reliability

| Metric | Marker | Reliability |
| --- | --- | --- |
| Process start → game window | `Created window using SDL video driver` | Good (log emitted right after window creation) |
| Process start → initial resource load done | Last `Created: … atlas` line of the first reload | **Proxy.** Atlas stitching happens near the end of the reload; work after it is not captured |
| Resource reload duration | `Reloading ResourceManager` → last atlas | Proxy, as above |
| Process start → *interactive* title screen | none logged | **Unavailable** automatically; see manual procedure §3.5 |
| Idle memory at title screen | working set, heap used | Indicative only (heap depends on GC timing; high variance) |

### 3.3 Results (warm, n = 5; seconds unless stated)

| Metric | A vanilla | B Fabric + API | C B + QoL: Unified |
| --- | --- | --- | --- |
| Window, median (min–max, sd) | 5.91 (5.81–5.97, 0.06) | 8.72 (8.70–8.98, 0.12) | 9.07 (8.94–9.30, 0.14) |
| Resource load done, median (min–max, sd) | 7.49 (7.43–7.60, 0.07) | 10.70 (10.65–10.92, 0.11) | 11.04 (10.95–11.38, 0.18) |
| Working set MB, median (min–max) | 1060 (949–1116) | 1150 (1129–1168) | 1112 (1096–1157) |
| Heap used MB, median (min–max) | 234 (220–351) | 400 (345–406) | 287 (245–402) |
| Interactive title screen | unavailable | unavailable | unavailable |

First round of the session (single run each): A 5.98 / 7.69 s, B 8.98 /
10.94 s, C 9.10 / 11.08 s (window / resource load). These are **not cold
starts**: the files had already been read earlier that day, so they are in
the Windows file cache.

Phase breakdown from one warm run per configuration (log line arrival times):

| Phase | A | B | C |
| --- | --- | --- | --- |
| Spawn → render thread starts (`Environment:`) | 5.19 | 7.68 | 7.82 |
| Render thread → window | 0.72 | 1.30 | 1.21 |
| Resource reload (start → last atlas) | ≈1.0 | ≈1.2 | ≈1.2 |

### 3.4 Interpretation and limitations

- The Fabric overhead (≈3.2 s) is mostly pre-render (≈2.5 s). That time goes
  to Fabric Loader, Mixin and Fabric API, which are outside this project's
  control.
- The C − B difference (≈0.3 s) exceeds run-to-run spread, but it was
  measured only in the dev layout. There, our mod is loaded from class
  directories rather than a JAR, and the initializer only logs one line. It
  is **not confirmed** to apply to a production install (Q-SP3).
- **Cold start is unavailable.** Windows keeps recently read files in its
  standby cache. Clearing it requires administrator tools or a reboot, and
  this task does not allow either. A clean Gradle build does not clear it,
  so it does not create a cold start either. A true cold measurement needs a
  reboot followed by a single first launch, repeated across several reboots.
- The dev environment differs from a launcher install: Loom's dev launcher,
  unobfuscated merged jar, offline account (Realms/auth calls fail fast).
  Absolute numbers will differ in production; relative comparisons are the
  useful result.
- Only one machine was tested; no conclusions for low-end hardware.

### 3.5 Manual QA procedure (interactive title screen, cold start)

1. Reboot; log in and wait 2 minutes for background activity to settle.
2. Launch the configuration from the official launcher (production install,
   identical JVM arguments and `options.txt`), with a stopwatch or screen
   recording at ≥ 60 fps starting at the launch click.
3. Stop when the title screen buttons respond to the mouse (hover
   highlight). Record the time and the `latest.log` timestamps.
4. Read the working set in Task Manager after 20 s idle.
5. Repeat steps 1–4 at least five times per configuration (one cold run per
   reboot). Warm runs: repeat steps 2–4 without rebooting.
6. Report median, minimum, maximum and standard deviation.

## 4. Ranked optimization candidates

| Rank | Candidate | Class | Expected benefit | Complexity | Compat. risk | Phase | Restart | Testability | Maintenance |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | **Startup budget for QoL: Unified** (no eager Mixins or class loading in features; minimal initializer work; CI/QA check of C − B) | Recommended | Keeps our ≈0.3 s or less from growing as features are added | Low | None | All | n/a | High (harness) | Low |
| 2 | **Startup timing diagnostics** (opt-in log of bootstrap, window and reload durations) | Recommended | No direct speedup; makes every later claim measurable | Low–medium (a few injections, or only existing events if available) | Low | Client init + reload | Live or restart | High | Low–medium |
| 3 | **Resource-pack lookup caching** (cache directory listings and existence checks for pack resources) | Experimental | Unknown on vanilla (reload ≈1.2 s total); possibly larger with many packs/mods | Medium | Medium (packs changing on disk, other pack mods) | Resource reload | Restart | Medium | Medium–high |
| 4 | Dedicated reload executor / thread priorities | Experimental | Responsiveness, not total time; unverified | Low–medium | Low–medium | Early | Restart | Low (hardware dependent) | Medium |
| 5 | JVM AOT cache / launch-flag guidance | Experimental (docs only) | Unverified under Knot | None in mod | None | Before JVM start | n/a | Manual | Low |
| 6 | Lazy model loading/baking (dynamic resources) | Not recommended | Possibly large with heavy modpacks; small on vanilla | High | **High** (rendering mods, model APIs) | Resource reload | Restart | Low | **High** |
| 7 | Lazy/deferred language loading | Not recommended (for now) | Small; we ship only two languages | Medium | Medium | Resource reload | Restart | Medium | Medium |
| 8 | Lazy DFU initialization | Already handled by Minecraft | — | — | — | — | — | — | — |
| 9 | World-loading tweaks (Fastload) | Out of scope | — | — | Data-safety sensitive | World load | — | — | — |

No candidate has a measured gain yet; none should be promised.

## 5. Risk and compatibility analysis

- **Data safety.** None of the recommended candidates touches world,
  inventory or navigation data. Lazy DFU-style changes would affect data
  upgrades and are excluded.
- **Compatibility.** Mixins into resource loading conflict with ModernFix,
  with resource-pack mods and with Fabric API's resource loader. If ModernFix
  ever ships for Fabric 26.x, candidate 3 must declare it as a conflict.
- **Vanilla behavior.** Caching pack lookups must keep vanilla semantics:
  reloading with F3+T must pick up changed files, so the cache is
  invalidated on every reload.
- **Maintenance.** Every Mixin into loading code must be re-verified for
  each Minecraft release line (each `26.x`). Prefer injections at stable,
  public entry points.
- **Licensing.** ModernFix (LGPL-3.0) and Fastload (LGPL-2.1) must not be
  copied; LazyDFU is MIT but obsolete. Implementations must come from our
  own analysis of vanilla 26.3.

## 6. Core architecture implications

Findings:

1. **Mixins apply at class load, before `ClientModInitializer` runs.** A
   startup optimization implemented as a Mixin cannot be switched on or off
   live. It must be `RESTART_REQUIRED`, which the proposed descriptor
   already supports ([M0 §3.1](../specifications/m0-platform-foundations.md#31-descriptor)).
2. **Gating needs the configuration before client initialization.** The
   standard way is a Mixin config plugin (`IMixinConfigPlugin.shouldApplyMixin`)
   that decides per Mixin whether to apply it. That plugin runs very early,
   before the Core is initialized.
3. **The proposed Configuration Service is already planned to be
   Minecraft-free** ([overview](../architecture/overview.md#dependency-direction-proposed)),
   so it can be read that early.

Smallest proposed adjustment (**[Deferred]** by Q-SP7):

- Add a static, **read-only early read** of the configuration: it parses the
  file, applies the safe-handling rules in memory, never writes, and returns
  only the effective enabled state of `RESTART_REQUIRED` features. The Mixin
  config plugin uses it. The normal Configuration Service loads the file
  again later, as specified.
- Do not add a `preLaunch` entrypoint, a new bootstrap framework or
  early-loading abstractions. None of the recommended candidates needs one.

The proposed lifecycle (desired vs effective state, *pending restart*)
already covers how the UI presents these features. No other contract
changes are needed.

## 7. Proposed implementation scope

For a future milestone, if approved; nothing is scheduled:

1. Commit a benchmark harness as developer tooling outside the shipped mod
   (Q-SP4), and record a production-install baseline using the manual
   procedure.
2. Candidate 1: a startup budget rule in `AGENTS.md` and a benchmark check
   per release.
3. Candidate 2: opt-in startup timing diagnostics.
4. Profile resource reload with a realistic mod and resource-pack set. Only
   then decide on candidate 3.

## 8. Acceptance criteria for a future optimization milestone [Proposed]

1. **SP-AC1** A committed, documented harness reproduces the A/B/C benchmark
   with n ≥ 5 per configuration and reports median, min, max and sd.
2. **SP-AC2** QoL: Unified adds at most an approved budget (Q-SP2) to warm
   median time-to-resource-load over B, measured from a production-style JAR
   install.
3. **SP-AC3** Each optimization shows a warm median improvement larger than
   2 × the larger sd of the two configurations, on at least two machines.
4. **SP-AC4** With the feature disabled, startup is unchanged within noise,
   and the optimization's Mixins are not applied (verified in the log).
5. **SP-AC5** Resource reload (F3+T) after changing a pack file shows the
   change (no stale cache).
6. **SP-AC6** No change to world, inventory or navigation data;
   singleplayer and vanilla-server joins work.
7. **SP-AC7** Conflicting mods, if declared, are detected per M0 §6.

## Decisions (2026-11-21)

| Id | Decision | Status |
| --- | --- | --- |
| Q-SP1 | The intended "FasLoad" reference remains unconfirmed; no further investigation now. | [Approved 2026-11-21] |
| Q-SP2 | Qualitative startup-performance policy (see [AGENTS.md](../../../AGENTS.md#engineering-rules)). A numerical budget is deferred until production-install measurements exist. | [Approved 2026-11-21] |
| Q-SP3 | Production-install benchmarking is required before any startup optimization work. | [Approved 2026-11-21] |
| Q-SP4 | A development-only benchmark harness may be committed; implementation is deferred. | [Approved 2026-11-21] |
| Q-SP5 | Plain feature ids matching the descriptor pattern are kept. `fast_startup` is reserved for a possible future feature. | [Approved 2026-11-21] |
| Q-SP6 | No startup optimization feature is scheduled. Startup-performance engineering guidelines are kept. | [Approved 2026-11-21] |
| Q-SP7 | Early read-only configuration loading (§6) is deferred until a confirmed requirement exists. | [Deferred] |

Because of these decisions, §6–§8 are reference material only.
SP-AC2's budget waits on Q-SP2's deferred numerical budget.

## 9. Original questions (resolved above)

- **Q-SP1** Was "FasLoad" meant to be Fastload (world loading), or a
  different mod? If Fastload: world loading is out of scope for this
  investigation.
- **Q-SP2** Acceptable startup budget for QoL: Unified over Fabric +
  Fabric API (e.g. ≤ 0.1 s warm median)?
- **Q-SP3** Re-measure C − B with a production install to confirm or
  reject the ≈0.3 s dev-environment difference.
- **Q-SP4** May benchmark tooling be committed (e.g. a `tools/`
  directory, not shipped in the JAR)?
- **Q-SP5** The id `performance.fast_startup` contains a dot and a
  category prefix. The proposed id rule (`^[a-z][a-z0-9_]{1,63}$`, see the
  [feature catalog](../product/feature-catalog.md#identifier-rules-approved-2026-11-21))
  does not allow that; the existing catalog uses plain ids such as
  `server_ping`. Which form is intended?
- **Q-SP6** Should startup optimization become a catalog feature at
  all, given the small measured headroom, or be limited to candidates 1–2?
- **Q-SP7** Is the early read-only configuration read (§6) approved
  as a Core contract addition?
- Unresolved technical: whether vanilla 26.3 resource loading is
  filesystem-bound with many packs; whether Java 25 AOT caching helps under
  Knot; true cold-start numbers.
