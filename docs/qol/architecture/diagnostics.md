# Diagnostics Direction

Status labels follow [AGENTS.md](../../../AGENTS.md#decision-status-labels).

This document records an approved **future direction** (2026-11-21). It is
not an M0 telemetry implementation. M0 only includes the local logging
already required by the [M0 lifecycle](../specifications/m0-platform-foundations.md#44-logging-and-ui-reporting-approved-2026-11-21).

## Decisions [Approved 2026-11-21]

- **DG-1** Feature-specific logging and failure identification belong in the
  Core. Every Core log entry about a feature names its feature id.
- **DG-2** Remote telemetry is disabled by default and is not implemented in
  M0.
- **DG-3** Any future reporting requires explicit user consent and a review
  of the data before it is sent.
- **DG-4** No GitHub write token (or any other write credential) is ever
  embedded in the client.
- **DG-5** Raw client reports never create public GitHub issues directly.

## Deferred [Deferred]

Remote submission, deduplication, privacy controls and issue automation.
Each needs its own specification and approval. No dependencies or services
are introduced for diagnostics until then.
