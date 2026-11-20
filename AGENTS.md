# Agent Guide

Instructions for coding agents (and humans) contributing to QoL: Unified.
Read this file before making any change.

## Roles

- **Product Owner (PO)** and **technical lead** make product and architecture
  decisions.
- **Implementation agents** document those decisions faithfully, identify gaps,
  implement approved specifications and validate the results.

Agents do not make product decisions. When a decision is missing, record it as
an open question and ask; do not fill the gap silently.

## Methodology: Spec-Driven Development

SDD is a working practice here, not a tool. Do not install an SDD framework,
generator or template engine unless the PO explicitly approves it.

1. **Specify.** Behavior is written in `docs/specifications/` before code.
2. **Approve.** The PO or technical lead approves the spec (or parts of it).
3. **Implement.** Only approved requirements are implemented.
4. **Validate.** Every acceptance criterion is checked and reported.
5. **Update.** If implementation reveals a gap or a needed change, update the
   spec first and flag it for approval.

## Decision status labels

Every decision in the documentation carries one of these labels:

| Label | Meaning | Agent may implement? |
| --- | --- | --- |
| **[Approved]** | Decided by the PO or technical lead. | Yes |
| **[Proposed]** | Technical proposal awaiting approval. | No, unless the task explicitly approves it |
| **[Open]** | Question with no proposal or with alternatives. | No |
| **[Deferred]** | Intentionally postponed to a later milestone. | No |

Never relabel a proposal as approved yourself. Promotion to **[Approved]**
happens only when the PO or technical lead says so; note the date in the
commit message or the document.

## Documentation map

| Path | Content |
| --- | --- |
| `docs/product/vision.md` | Goals, principles, targets, milestones |
| `docs/product/feature-catalog.md` | Planned features and identifiers |
| `docs/architecture/overview.md` | Components and boundaries |
| `docs/architecture/configuration.md` | Configuration and data policy |
| `docs/specifications/` | One spec per milestone or feature |

## Engineering rules

- **Client-side only.** No server component, no custom packets that require a
  server-side mod. Respect vanilla synchronization and server authority.
- **Player data safety first.** Never risk inventory, world or navigation data.
  Prefer doing nothing over doing something unsafe.
- **Keep it simple.** No dependency injection framework, event bus,
  reflection-based discovery or plugin loader without demonstrated need and
  approval. No speculative abstractions.
- **Feature isolation.** A feature never reaches into another feature's
  private implementation. Shared needs go through the Core.
- **Dependencies.** Do not add a library, Gradle plugin or mod dependency
  without approval.
- **Versions.** Never assume a Minecraft, Fabric, Loom, mapping or library
  version. Verify it from an official source and cite it in the change.
- **No copied code or assets.** Features inspired by other mods are
  reimplemented from behavior, not from their code, textures or translations.
- **Localization.** All user-facing text uses translation keys, with entries in
  both `en_us` and `pt_br`.
- **No license decisions.** Do not add or change a license file.
- **Horizon.** Do not write speculative Horizon (Rust) code before M5.

## Git workflow

- Conventional Commits (`feat:`, `fix:`, `docs:`, `test:`, `refactor:`,
  `build:`, `chore:`).
- Small, focused commits. Run `git diff --check` before committing.
- Do not create GitHub Issues, milestones or releases unless the task asks.
- Push only when the task says so and validation passed.

## Task completion

Stop at the boundary of the assigned task; do not start the next milestone.
Finish with a report containing: starting and final commit hashes, files
changed, decisions documented or implemented, open questions, validation
results, and commit/push status.
