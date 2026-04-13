# 00. Documentation Index

Sentinel documents are organized by number and category so the repository stays easy to navigate as it grows.

## 01. Foundation

Foundation documents define the direction, scope, and initial implementation strategy of the project.

- [01. Sentinel Platform Plan](/Users/skl-wade/Wade/Sentinel/docs/01-foundation/01-sentinel-platform-plan.md)
- [02. MVP Implementation Plan](/Users/skl-wade/Wade/Sentinel/docs/01-foundation/02-mvp-implementation-plan.md)
- [03. Project Structure Proposal](/Users/skl-wade/Wade/Sentinel/docs/01-foundation/03-project-structure.md)

## 20. Domain and Contracts

Domain-level behavior and contract semantics are documented here.

- [21. Analysis Prompt Versioning and Rollout](/Users/skl-wade/Wade/Sentinel/docs/20-domain/21-analysis-prompt-versioning.md)
- [22. Routing Policy and Channel Mapping](/Users/skl-wade/Wade/Sentinel/docs/20-domain/22-routing-policy-and-channel-mapping.md)

## 30. Operations

Operational runbooks, alerting standards, and observability definitions live here.

- [31. Replay Recovery SLO and Alerting](/Users/skl-wade/Wade/Sentinel/docs/30-operations/31-replay-recovery-slo-alerting.md)
- [32. Webhook Signature Security Baseline](/Users/skl-wade/Wade/Sentinel/docs/30-operations/32-webhook-signature-security-baseline.md)
- [33. Replay API Authorization and Hardening](/Users/skl-wade/Wade/Sentinel/docs/30-operations/33-replay-api-authorization-and-hardening.md)
- [34. Delivery Plugin Readiness](/Users/skl-wade/Wade/Sentinel/docs/30-operations/34-delivery-plugin-readiness.md)
- [35. Dead-Letter Tenant Scope and Pagination Contract](/Users/skl-wade/Wade/Sentinel/docs/30-operations/35-dead-letter-tenant-scope-and-pagination.md)
- [36. Delivery-Attempt Pagination Contract](/Users/skl-wade/Wade/Sentinel/docs/30-operations/36-delivery-attempt-pagination-contract.md)
- [37. Delivery-Attempt Tenant Scope Baseline](/Users/skl-wade/Wade/Sentinel/docs/30-operations/37-delivery-attempt-tenant-scope-baseline.md)

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

At the current stage, `01 Foundation`, `20 Domain and Contracts`, `30 Operations`, and `99 ADR` are in use.

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
- Done: Prometheus alerting rules and replay recovery SLO thresholds
- Done: prompt/template externalization with versioned rollout controls
- Done: source webhook signature validation and secret-hardening baseline
- Done: routing policy and channel-mapping externalization
- Done: replay operation authorization and API hardening
- Done: delivery plugin health endpoint and readiness checks
- Done: dead-letter tenant scoping and API pagination contract hardening
- Done: delivery-attempt query pagination contract alignment
- Done: delivery-attempt tenant scope enforcement baseline
- Next: delivery-attempt query authorization token baseline
