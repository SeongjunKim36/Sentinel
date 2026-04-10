# 00. Documentation Index

Sentinel documents are organized by number and category so the repository stays easy to navigate as it grows.

## 01. Foundation

Foundation documents define the direction, scope, and initial implementation strategy of the project.

- [01. Sentinel Platform Plan](/Users/skl-wade/Wade/Sentinel/docs/01-foundation/01-sentinel-platform-plan.md)
- [02. MVP Implementation Plan](/Users/skl-wade/Wade/Sentinel/docs/01-foundation/02-mvp-implementation-plan.md)
- [03. Project Structure Proposal](/Users/skl-wade/Wade/Sentinel/docs/01-foundation/03-project-structure.md)

## 99. ADR

Architectural decisions that should remain traceable over time live here.

- [ADR 0001 - MVP Bootstrap Architecture](/Users/skl-wade/Wade/Sentinel/docs/99-adr/0001-mvp-bootstrap-architecture.md)

## Numbering Scheme

- `00`: documentation index and repository-level documentation guidance
- `01`: foundational project documents
- `10-19`: architecture details
- `20-29`: domain models and contracts
- `30-39`: operations, observability, and security
- `90-99`: ADRs, retrospectives, and change history

At the current stage only `01 Foundation` and `99 ADR` are in use.

## Local-Only Notes

Working notes that should not be published can be kept in `docs-private/` or `notes-private/`.

- Those folders are intentionally ignored by Git.
- Korean notes are welcome there.
- They should not be referenced from public documentation.

## Current Execution Status

The public MVP implementation is being delivered in numbered, reviewable units.

- Done: end-to-end pipeline (webhook -> Kafka -> classification -> analysis -> evaluation -> delivery)
- Done: delivery attempt audit + dead-letter persistence + replay workflow
- Done: replay guardrails, replay audit trail endpoint, and replay failure threshold alerts
- Done: switchable LLM provider mode (`bootstrap` and `openai`)
- Done: Prometheus + Grafana local dashboard provisioning for stage metrics and replay-failure-alert MTTR
- Next: Prometheus alerting rules and SLO thresholds for replay recovery
- Next: prompt/template externalization with versioned rollout controls
