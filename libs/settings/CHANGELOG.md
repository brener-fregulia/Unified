# Changelog: Unified Settings (Library)

Changes to `unified_settings`, the library nested into every Unified mod. The
format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Tags
use `settings/v<version>` ([versioning](../../docs/development/versioning.md)).
Nothing has been released yet.

## [Unreleased]

### Added

- 1.0.0: provider contract `unified.settings.api` (contract 1.0), discovery
  through the `unified-settings` entrypoint with deterministic order and
  failure isolation, the **Open Unified Settings** key (default `U`, category
  "Unified"), one **Unified Settings…** button in the Options screen, and a
  temporary entry screen listing the installed modules.
- 1.1.0: contract 1.1 adds `UnifiedSettingsProvider.workspace()` and
  `SettingsWorkspace` (defaults keep 1.0 providers working). The shared
  workspace (search, module/category/feature sidebar, drawer, footer with
  Retry/Undo) replaces the temporary entry screen; `U` also works on the
  title screen.
- 1.2.0: a shared `FeatureSwitch` (ON/OFF) widget for module panels, drawn
  like the approved QoL switch. Contract unchanged (1.1).
