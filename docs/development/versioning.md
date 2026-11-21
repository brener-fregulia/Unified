# Versioning and Release Policy

Status labels follow [AGENTS.md](../../AGENTS.md#decision-status-labels).
All rules in this document are **[Approved]** (M0.1.1) unless marked otherwise.

## Version structure [Approved]

```text
MC_MAJOR.MC_MINOR.MOD_REVISION[-PRERELEASE]
```

The scheme is SemVer-compatible and aligned with Minecraft release lines.

| Component | Meaning |
| --- | --- |
| `MC_MAJOR.MC_MINOR` | Target Minecraft release line (e.g. `26.3`). |
| `MOD_REVISION` | Mod revision within that line; starts at `0` for each new line. |
| `PRERELEASE` | Optional `alpha.N`, `beta.N` or `rc.N`. |

Examples, in release order:

| Version | Meaning |
| --- | --- |
| `26.3.0-alpha.1` | First alpha for the 26.3 line (first intended distributable version) |
| `26.3.0-alpha.2` | Second alpha |
| `26.3.0-beta.1` | First beta |
| `26.3.0-rc.1` | First release candidate |
| `26.3.0` | First stable release for the 26.3 line |
| `26.3.1` | Next mod revision for the 26.3 line |
| `26.4.0` | First stable release for the 26.4 line; revision reset to `0` |

## Release stages [Approved]

Stages progress `alpha` → `beta` → `rc` → stable. The number increments
within each stage (`alpha.1`, `alpha.2`, …) and restarts at `1` in the next
stage.

| Stage | Intent |
| --- | --- |
| `alpha` | Early testing; features and configuration may change. |
| `beta` | Feature-complete for the version; stabilization. |
| `rc` | Candidate for stable; only release-blocking fixes. |
| stable | No prerelease suffix. |

The intent column is **[Proposed]** guidance; the ordering is approved.

## Development identifier [Approved]

Before `26.3.0-alpha.1` is released, `26.3.0-alpha.1-SNAPSHOT` may be used as
a local build version when one becomes necessary. It is never published.

## Immutability [Approved]

- A published version is never changed, re-uploaded or reused.
- A fix to a published version is a new version.

## Git tags [Approved]

- Release tags use the `v` prefix: `v26.3.0-alpha.1`, `v26.3.0`.
- Commits never create releases or tags automatically.
- Tags and GitHub Releases are created only as part of a prepared release.

## Minecraft compatibility [Approved]

- The version string identifies a release line, not exact compatibility.
- Exact supported Minecraft versions are declared in Fabric metadata
  (`fabric.mod.json`) and in the release information.
- No automatic compatibility claims are made for future Minecraft releases.

## Changelog maintenance [Approved]

[CHANGELOG.md](../../CHANGELOG.md) follows
[Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/).

- Record meaningful changes under `[Unreleased]`, not every commit.
- Use only the categories needed: Added, Changed, Deprecated, Removed, Fixed,
  Security. No empty categories.
- Always document breaking user-facing changes, configuration migrations and
  removed functionality.
- Do not invent versions, dates or functionality.
- A version section is created only when that release is prepared; its
  entries move from `[Unreleased]`.
- Comparison links are added only for tags that actually exist.

## Preparing a release manually [Approved process]

1. Confirm the acceptance criteria for the included work are validated.
2. Choose the next version according to this policy; confirm it was never
   published.
3. Set the build version and declared Minecraft compatibility.
4. Move relevant `[Unreleased]` entries to `## [X.Y.Z[-pre]] - YYYY-MM-DD`
   and update comparison links.
5. Commit (`chore: release X.Y.Z[-pre]`), build and test the artifact.
6. Create the annotated tag `vX.Y.Z[-pre]` and push it.
7. Publish the release with notes from the changelog section.

The commit message format in step 5 is **[Proposed]**.

## Future automation [Proposed]

Automation (CI builds, tag-triggered publishing, changelog checks) may be
added later with approval. It must keep releases explicit: no release or
tag from an ordinary commit. Nothing is automated yet.
