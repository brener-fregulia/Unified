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
