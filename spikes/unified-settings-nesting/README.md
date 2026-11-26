# Unified Settings nesting spike (M2.1.0)

A disposable runtime experiment: several mods nest the same library mod,
`unified_settings`, at different versions. Results and conclusions are in
[unified-settings-integration.md §18](../../docs/architecture/unified-settings-integration.md#18-runtime-spike-results-m210-2026-11-26).

It is a separate Gradle build, not part of `settings.gradle.kts`, CI or any
production JAR.

```sh
./gradlew :mods:qol:jar :mods:optimizations:jar        # the real JARs are repackaged with the library
./gradlew -p spikes/unified-settings-nesting :runner:runSpikeParentsA100B110
```

| Project | Purpose |
| --- | --- |
| `lib-1.0.0`, `lib-1.1.0`, `lib-2.0.0` | The library: base, additive and breaking contract |
| `parent-a`, `parent-b` | Stand-in mods compiled against contract 1.0 and 1.1; `jarWithLib<version>[NoRange]` tasks nest a chosen library version |
| `runner` | One `runSpike<Scenario>` per combination (see `runner/build.gradle.kts`); writes `spike-result.txt` into `runner/build/run-<Scenario>/` and closes the game |

`runSpikeBreakingConflictingRanges` is expected to stop at Fabric's
"Incompatible mods found!" window. Close it by hand.
