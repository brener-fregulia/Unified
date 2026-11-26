# FastQuit V1 (M2.3)

Status labels follow [AGENTS.md](../../../AGENTS.md#decision-status-labels).
The PO approved the feature identity and authorized implementation **if** a
safe, testable lifecycle design exists (task of 2026-11-26). Sections marked
**[Implemented]** were built under that authorization; the decisions still
open for the PO are listed in §11.

Return to the menu sooner when leaving a singleplayer world, while the
integrated server finishes its normal shutdown and save in the background.
Inspired by the behavior of FastQuit (contaria, MIT). No third-party code or
assets are used; only its public description was read.

## 1. Identity

| Item | Value |
| --- | --- |
| Feature id | `fast_quit` |
| Category | Performance (Desempenho) |
| Default | **Off** (conservative; Q-FQ1) |
| Apply mode | Live: decided at the moment a world is left |
| Side | Client only; singleplayer (integrated server) only |

## 2. Research: other implementations

| Mod | Version / MC | Fabric id (from its `fabric.mod.json`) | License | Behavior (from README) |
| --- | --- | --- | --- | --- |
| [FastQuit](https://modrinth.com/mod/fastquit) ([source](https://github.com/contariaa/FastQuit)) | 3.1.5, Minecraft 26.2–26.3 (2026-07-30) | `fastquit` | MIT | Skips the client's wait for the server; saving continues in the background. Rejoining the world or backing it up waits; editing, deleting or recreating goes through the still-running session; closing the game waits. Options: toasts, background thread priority, render the saving screen, show saving time. |

FastQuit supports 26.3 and changes the same wait, so it is a declared
conflict: with `fastquit` installed, `fast_quit` stays inactive (§8).

## 3. Minecraft 26.3 lifecycle (verified with `javap`)

**Leaving a world** (`Save and Quit`, `disconnectWithSavingScreen`,
`exitWorldAndClose` all reach `Minecraft.disconnect(Screen, boolean, boolean)`):

1. The client closes its connection and drops pending client tasks.
2. `singleplayerServer` is read into a local and cleared on the client.
3. `IntegratedServer.halt(false)`: runs a task on the server thread
   (`executeBlocking`), sets `running = false`, stops the LAN pinger. It does
   **not** wait for the save.
4. Vanilla then shows "Saving world" and loops
   `while (!server.isShutdown()) { pumpEvents(); renderFrame(false); }`.
5. Then the next screen is shown and the client level is torn down.

`MinecraftServer.isShutdown()` is `!serverThread.isAlive()`: vanilla waits
for the **end of the server thread**.

**On the server thread**, after its loop ends, `runServer` calls
`stopServer()` then `onServerExit()`. `stopServer()`:

- closes the packet processor and network listener;
- `isSaving = true`; `playerList.saveAll()`; `removeAll()`;
- marks every level as not-saving-on-tick, ticks chunk caches until pending
  chunk work drains (`deactivateTicketsOnClosing`, `tick`, `waitUntilNextTick`);
- `saveAllChunks(suppressLog, flush = true, force)`; closes every
  `ServerLevel` (IO workers flush);
- closes saved-data storage and reloadable resources;
- `storageSource.close()`: **releases `session.lock`**; a failure is logged
  ("Failed to unlock level").
- Fabric's `SERVER_STOPPING`/`SERVER_STOPPED` events run on this thread, as
  part of this sequence.

`IntegratedServer.onServerCrash` relays the crash to the client
(`delayCrash`), so a crash during shutdown reaches the client crash screen
even after the client has left the world.

**World storage access.** Opening (`WorldOpenFlows.openWorld` →
`validateAndCreateAccess`), creating, editing, deleting, backing up and Realms
uploads all create a `LevelStorageAccess`, which takes `session.lock`
(`DirectoryLock.create`). A world whose lock is held cannot be opened: the
attempt fails with "Failed to read level … data" and a toast (verified by an
accidental test run, §9.3). Reading world summaries for the list does not
lock.

**Game exit.** `Main` calls `exitWorldAndClose()` (leave, then
`Minecraft.close()`), then `System.exit(0)`. A server thread still saving
when `System.exit` runs would be cut off. Other exit paths (crash reports,
window close via the same loop end) also end in `System.exit`.

**Multiplayer.** `singleplayerServer` is null; nothing above runs.

## 4. Problem

The client is blocked on "Saving world" for the whole server shutdown, which
grows with explored chunks and mods' shutdown work. The time is not measured
here (§10).

## 5. Alternatives

| Option | Verdict |
| --- | --- |
| A. Skip the client wait after `halt(false)`; track the server thread; make every storage access and game exit wait for it | **Chosen.** The server runs its unchanged vanilla shutdown; only who waits, and when, changes. |
| B. Run `stopServer` on a new background executor | Rejected: changes thread ownership of server state. |
| C. Defer part of saving (e.g. skip flush) | Rejected: weakens persistence. |
| D. Allow a second world to open while one saves | Rejected for V1: two integrated servers would share global state (IO worker pools, static registries) with no evidence that this is safe. |

## 6. Design [Implemented]

- **Mixin 1** (`MinecraftFastQuitMixin`): a MixinExtras `@WrapOperation` on
  the `IntegratedServer.isShutdown()` call inside
  `Minecraft.disconnect(Screen, boolean, boolean)`. While the feature is
  active, the shutdown is handed to `PendingSaves.track` and the loop ends at
  once. Everything before it (`halt(false)`) and after it (next screen, level
  teardown) is vanilla. Off, or with `fastquit` installed: vanilla.
- **Mixin 2** (same class): `Minecraft.close()` HEAD waits for a pending
  shutdown before the client releases resources and `Main` exits.
- **Mixin 3** (`LevelStorageSourceMixin`): `createAccess` and
  `validateAndCreateAccess` HEAD wait for a pending shutdown. This covers
  open, create, edit, delete, backup and Realms upload of **any** world, so
  two integrated servers never run at once and a world never opens before its
  previous shutdown finished. Free when nothing is pending.
- **`PendingSaves`** (Kotlin):
  - **Bounded:** at most one pending shutdown. Leaving another world while
    one is pending first waits for it.
  - **Completion** = the server thread has ended (`Thread.isAlive()`), the
    same condition as vanilla's `isShutdown()`.
  - **Waiting** on the render thread shows a "Finishing saving the previous
    world…" screen and keeps rendering frames (`pumpEvents` +
    `renderFrame(false)`), exactly like vanilla's own wait; on another thread
    it joins the server thread. Interrupts never end a wait early. The saving
    server thread never waits on itself.
  - **Backstop:** a JVM shutdown hook joins the server thread, so
    `System.exit` from any path waits for the save.
  - **Status:** a toast "Saving world in the background" on leave and "World
    saved" on completion; log lines with the feature id and duration.
  - No sleeps, timers or new executors decide anything.
- **Errors:** vanilla's own handling is unchanged: save and unlock failures
  are logged by the server; a crash is relayed to the client crash screen.

## 7. Safety guarantees and limits

Guaranteed by construction (and tested, §9):

- No save step is skipped or reordered: the server thread runs vanilla's
  `stopServer`/`onServerExit`, including mod hooks.
- No world is accessed (opened, edited, deleted, backed up) while a previous
  shutdown holds or may still write it; no two integrated servers coexist.
- The game does not exit before the save ends (close waits; shutdown hook as
  backstop).
- A world is reported saved only after the server thread ended.

Not guaranteed (same as vanilla, or outside the mod's control):

- `kill -9`, power loss or a JVM `halt` during the save. The window is the
  same length as vanilla's, but the player no longer sees it, so they might
  kill a seemingly idle game. Mitigation: the toast; QA item.
- Mods with **client-side** logic that assumes the integrated server has
  fully stopped by the time the title screen shows (for example a client mod
  reading the world folder on disconnect). Their server-side hooks still run
  in order. This is why the default is off (Q-FQ1).
- A deadlocked server shutdown: vanilla would hang on "Saving world"; with
  FastQuit the hang moves to the next world access or game exit.

## 8. Compatibility

| Mod | Id (verified) | Relation |
| --- | --- | --- |
| FastQuit | `fastquit` | Same change on the same wait: `fast_quit` stays inactive while it is installed |
| Unified: QoL | `qol_unified` | Independent |
| Multiplayer / Paper servers | — | Unaffected: no integrated server |

## 9. Validation

### 9.1 Unit tests (`PendingSavesTest`)

A stand-in "server thread" gated by latches:

- a wait returns only after the thread ended, and an interrupt does not end it;
- a second shutdown waits for the first (bound of one);
- the saving thread never waits on itself;
- an already finished thread is never pending.

### 9.2 Game test (`runGameTestFastquit`)

With FastQuit on:

1. Create a world, place a block, leave through the normal disconnect. The
   shutdown is tracked and the client returns to the title screen; the
   background save finishes ("World saved" toast); `session.lock` is
   released.
2. Reopen the world: the block is there. Place another, leave; the client is
   back while the server is still saving (observed: true, 499 ms). After the
   save, reopen: the second change is there.
3. With FastQuit off, leaving is vanilla: nothing is tracked.

### 9.3 Limits of the automated test

Fabric's client game-test harness runs the integrated server in lockstep with
the test thread and defers `Minecraft.disconnect` for that reason. Any
render-thread wait for the server (vanilla's, and FastQuit's wait before
reopening) therefore cannot complete inside a test task. Two consequences:

- The "reopen while still saving waits" path was observed to start waiting
  (log "waiting for New World to finish saving") but cannot finish under the
  harness; it is covered by the unit tests and manual QA.
- An accidental early run opened the world before the deferred disconnect:
  vanilla refused with "Failed to read level … data" (lock held). This shows
  vanilla's lock protects the world even without FastQuit's wait.

Exiting the game while saving cannot be tested automatically (the test JVM
would exit). It is a manual QA item.

## 10. Performance

No timing improvement is claimed. The automated run only shows that the
client returned while the save was still running.

## 11. Decisions for the PO

- **Q-FQ1 [Open]:** default. Implemented **off**, because client-side mod
  compatibility is conditional (§7). Turn it on by default after manual QA?
- **Q-FQ2 [Proposed]:** while a world is saving, **every** world access waits
  (not only the same world), so two integrated servers never coexist.
- **Q-FQ3 [Proposed]:** no options beyond the switch in V1 (FastQuit's thread
  priority and saving-time options are not reproduced).

## 12. Manual QA

On Windows 11 and CachyOS with the production JARs:

1. FastQuit on; a world with explored terrain; Save and Quit: the title
   screen appears early; "World saved" follows.
2. Leave and immediately click the same world: "Finishing saving the previous
   world…" then the world opens; recent changes are present.
3. Leave and immediately open, create, edit (Edit → Backup / Optimize) or
   delete **another** world: each waits first.
4. Leave and immediately press Quit Game, and also close the window: the game
   waits, then exits; reopen the world, changes present; no "Failed to unlock"
   in `latest.log`.
5. Multiplayer (a Paper server): disconnecting is unchanged.
6. Open to LAN, a friend joined, Save and Quit: the friend is disconnected as
   in vanilla; the world saves.
7. With the original FastQuit installed: the switch shows it as the reason
   `fast_quit` is inactive.
