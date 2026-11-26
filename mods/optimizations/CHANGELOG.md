# Changelog: Unified: Optimizations

All notable changes to Unified: Optimizations are documented here. The format
follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the
project's [versioning policy](../../docs/development/versioning.md). Tags use
`optimizations/v<version>`. Nothing has been released yet.

## [Unreleased]

### Changed

- Requires Fabric Language Kotlin (`>=1.14.2+kotlin.2.4.21`); the startup
  report is written in Kotlin. Install it next to the mod.

### Added

- **Fast IP Ping** (Network, on by default): server-list pings of private,
  LAN, Tailscale and loopback IP addresses skip the slow reverse DNS lookup.
  Public IPs, host names and joining servers are unchanged; Mojang's
  blocklist still checks the IP and the typed address.
- **FastQuit** (Performance, off by default): returns to the menu while a
  singleplayer world finishes saving in the background. Opening, creating,
  editing or deleting a world, and closing the game, wait for the save.
  Inactive while the original FastQuit mod is installed.
- Settings in the shared Unified Settings workspace, saved to
  `config/unified_optimizations/config.json`. Carries Unified Settings
  (Library) 1.2.0.

- Unified Settings: the shared **Open Unified Settings** key (default `U`)
  and Options button list this mod, which has no settings yet. The mod
  carries Unified Settings (Library) 1.0.0 and now also requires Fabric API.
- Independent client-only Fabric mod `unified_optimizations` ("Unified:
  Optimizations"), version `26.3.0-alpha.1-SNAPSHOT`: entrypoint, logging,
  its own configuration directory and en_us/pt_br texts. No optimizations yet.
