# Linux Development Setup

How to build, run and test QoL: Unified on Linux. The same Gradle build is used
on Windows and Linux; there is no separate Linux build. Commands below use
`./gradlew` (on Windows, `.\gradlew.bat` with the same task names).

## Requirements

| Item | Version | Where it is pinned |
| --- | --- | --- |
| JDK | 25 (Temurin recommended) | `build.gradle.kts` (toolchain 25) |
| Gradle | from the wrapper (do not install Gradle) | `gradle/wrapper/gradle-wrapper.properties` |
| Minecraft, Fabric Loader, Fabric API | `minecraft_version`, `loader_version`, `fabric_api_version` | [`gradle.properties`](../../gradle.properties) |
| Graphics, for clients and game tests | OpenGL 4.5 driver (Mesa or vendor) and a desktop session (X11 or Wayland) | — |

Minecraft brings its own LWJGL and SDL natives; no extra libraries are
needed to build. The optional narrator needs the system `flite` library;
without it the client logs `Failed to load library flite` and continues.

## 1. Java 25

Any of these works; Gradle needs a JDK 25 as its runtime.

**Arch / CachyOS packages.** List the available OpenJDK packages, install the
JDK 25 one, and select it:

```sh
pacman -Ss openjdk | grep -i jdk
sudo pacman -S <the JDK 25 package listed above>
archlinux-java status
sudo archlinux-java set <the java-25 environment listed by status>
```

**User-local JDK (no root).** Download the Temurin 25 JDK for Linux x64 from
[adoptium.net](https://adoptium.net/temurin/releases/?version=25), extract it
under `~/.jdks/`, and point the shell at it:

```sh
export JAVA_HOME="$HOME/.jdks/<extracted jdk-25 folder>"
export PATH="$JAVA_HOME/bin:$PATH"
```

Check what Gradle will use (look for `Launcher JVM: 25…`):

```sh
java -version
./gradlew --version
```

## 2. Clone and build

Keep the checkout on a Linux filesystem (under `$HOME`). On WSL2, do not build
inside `/mnt/c` or `/mnt/d`: it is much slower and file permissions differ.

```sh
git clone https://github.com/brener-fregulia/qol-unified.git
cd qol-unified
./gradlew clean build
```

`gradlew` is committed as executable with LF line endings
([`.gitattributes`](../../.gitattributes)). If a copy lost the bit, run
`chmod +x gradlew`.

The build runs the unit tests and writes the mod to
`build/libs/qol-unified-<version>.jar`. That JAR is client-only and never
contains the development fixtures.

## 3. Development client and game tests

```sh
./gradlew runClient                # Minecraft with the mod, files in run/
./gradlew runGameTestTargetinfo    # one automated client game test
```

Game tests open a real window, so they need a desktop session. They use only
disposable folders under `build/run-gametest*` and save screenshots there.
The task list and optional comparison runs are described in the
[README](../../README.md#development-fixtures).

## 4. Testing a build in Prism Launcher

1. Create an instance for the Minecraft version in `gradle.properties`, with
   the Fabric Loader version from `loader_version`.
2. **Edit instance → Settings → Java:** pick a Java 25 installation
   (Prism lists detected ones; **Auto-detect** or **Browse** if needed).
3. **Edit instance → Mods → Download mods:** add Fabric API, matching
   `fabric_api_version` or newer for the same Minecraft version.
4. **Mods → Add file:** choose `build/libs/qol-unified-<version>.jar`.
   Alternatively use **Folder** on the instance to open its `mods`
   folder; Prism opens the right path, so it never needs to be typed or
   hardcoded.

Use a dedicated test instance, not one holding worlds you care about.

## 5. WSL2 notes

- WSLg provides the display (`DISPLAY` and `WAYLAND_DISPLAY` are set).
  Minecraft's SDL falls back to XWayland on it.
- Rendering may use Mesa's software renderer (`llvmpipe`): the client and game
  tests work but slowly, and frame rates say nothing about real hardware.
- A passing WSL2 run is not a native Linux validation: drivers, compositors
  and input differ on a real desktop.
