# Changelog

All notable changes to QoL: Unified are documented in this file.

The format is based on [Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/).
Versions follow the Minecraft-aligned, SemVer-compatible scheme described in
the [versioning policy](docs/development/versioning.md). No version has been
released yet.

## [Unreleased]

### Added

- Spec-Driven Development documentation: product vision, feature catalog,
  architecture overview, configuration policy and the M0 Platform
  Foundations specification.
- Agent guide (`AGENTS.md`) with decision status labels and engineering rules.
- Versioning, release and changelog policy.
- Minimal Fabric client mod for Minecraft 26.3 (Java 25, Fabric Loader,
  Fabric API) that logs a startup message; English and Brazilian Portuguese
  resources.
- GitHub Actions build verification on Java 25 (no publishing).
- Core feature registry and lifecycle foundation: validated feature and
  setting descriptors, a frozen explicit registry, safe enable/disable with
  rollback and failure records, and desired vs effective settings. No
  features are registered yet.
- Global configuration file `config/qol_unified/config.json` (schema version
  1): validation with safe defaults, preservation of unknown and invalid
  values, backups of corrupt or migrated files, read-only mode for newer
  schema versions and atomic saves.
- Detection of mods declared as conflicting with a feature: such features are
  disabled by default unless the player explicitly chose otherwise, and each
  conflict is logged at startup.
- Settings screen built from native Minecraft widgets, opened with a
  configurable key binding (no default key): categories, search, per-feature
  settings, status and conflict indicators, and Done/Cancel. English and
  Brazilian Portuguese.
- Food HUD (`food_hud`), the first feature: a saturation overlay on the
  hunger bar; blinking previews of the hunger, saturation and estimated
  health that held food would restore (main or off-hand, following vanilla's
  item-use order); food values as icons in tooltips (off, while holding
  Shift, or always); and food and saturation in an optional F3 line. It
  follows the vanilla icon animations, applies settings without a restart,
  and is disabled by default when AppleSkin is installed. It is client-only
  and never shows exhaustion, which the server does not send.
- Chat Heads (`chat_heads`): shows the sender's head, with its hat layer,
  in player chat, either directly before the name (`<[head]Name> text`) or at
  the start of the line. Heads come only from the sender profile sent with
  the message, never from reading names in the text, so system messages,
  command output and plugin broadcasts get no head. It uses vanilla's own
  player-head text glyph and skin cache, changes only the displayed copy of a
  message (signatures, trust indicators, reports and narration are
  untouched), updates messages already in chat when it or its position is
  changed, and is disabled by default when the original Chat Heads mod is
  installed. TAB is not changed.
- Keyboard Map (`keyboard_map`): a visual keyboard of all vanilla and modded
  key bindings, with US (ANSI) and Brazilian (ABNT2) layouts and key names
  from your system. Free keys are gray, keys with one action green, and keys
  shared by several actions red with a count. A searchable, filterable list
  sits beside the keyboard. Selecting a key highlights its actions and vice
  versa. Bindings can be changed, cleared and reset with Minecraft's own
  controls (saved to `options.txt` as usual), with a confirmation before a
  key is shared. Open it from Options → Controls, from the QoL: Unified
  settings, or with an optional key (unbound by default).
