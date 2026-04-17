# 04. Phase Delivery Checklists

This document turns the roadmap into a trackable execution checklist.

Use it as the high-level phase board for the project.

- [00. Documentation Index](/Users/skl-wade/Wade/Sentinel/docs/00-docs-index.md) remains the unit-by-unit execution log.
- [01. Sentinel Platform Plan](/Users/skl-wade/Wade/Sentinel/docs/01-foundation/01-sentinel-platform-plan.md) remains the product and roadmap source of truth.
- [02. MVP Implementation Plan](/Users/skl-wade/Wade/Sentinel/docs/01-foundation/02-mvp-implementation-plan.md) remains the detailed Phase 1 implementation guide.

## How To Use This Checklist

- Check an item only when the repository contains the code, documentation, and verification needed to treat it as implemented.
- Keep this document phase-oriented and stable.
- Track small delivery slices and the immediate next item in [00. Documentation Index](/Users/skl-wade/Wade/Sentinel/docs/00-docs-index.md).

## Current Snapshot

- Phase 1: Complete
- Phase 2: In progress, source-side extension proven
- Phase 3: Not started as a dedicated implementation phase
- Phase 4: Partial groundwork completed during MVP hardening

## Phase 1. Incident Analysis Assistant

Goal: one source, one analysis path, one output channel, full end-to-end traceability.

Status: complete

- [x] Local Spring Boot application and Docker Compose environment exist
- [x] Shared event model and pipeline contracts are implemented
- [x] `POST /api/v1/webhooks/sentry` ingests real source events
- [x] Raw events are published to Kafka
- [x] Classification consumes raw events and suppresses duplicates
- [x] Classified events are forwarded for analysis
- [x] Analysis runs through a switchable LLM provider mode
- [x] Prompt templates and version rollout controls are externalized
- [x] Evaluation assigns severity, confidence, and routing intent
- [x] Slack delivery is implemented as the primary visible output
- [x] Telegram delivery exists for fast local testing and alert paths
- [x] Delivery attempts are persisted for auditability
- [x] Dead-letter persistence and replay workflow are implemented
- [x] Replay guardrails, replay audits, and replay failure alerting exist
- [x] OpenTelemetry trace propagation exists across HTTP and Kafka boundaries
- [x] Prometheus and Grafana local operational visibility are provisioned
- [x] Webhook signature validation and replay API hardening baselines are implemented
- [x] Delivery-attempt query pagination, tenant scope, authorization, and rate limiting are hardened
- [x] Delivery-attempt query rate-limit and authorization error contracts are stabilized
- [x] Delivery-attempt query validation error response contract baseline is implemented
- [x] Phase 1 completion criteria are reviewed and explicitly marked complete

## Phase 2. Plugin Architecture

Goal: new sources or outputs can be added without changing the core flow.

Status: in progress

- [x] `SourcePlugin` contract exists
- [x] `OutputPlugin` contract exists
- [x] Sentry intake is wired through a source plugin boundary
- [x] Slack delivery is wired through an output plugin boundary
- [x] Telegram delivery is wired through an output plugin boundary
- [x] Output plugin registry and readiness checks exist
- [x] Prompt version management is externalized as part of extensibility groundwork
- [x] RSS source plugin is implemented
- [ ] Email output plugin is implemented
- [x] A documented extension workflow proves that new plugins can be added without core flow changes

## Phase 3. Dashboard And Customer Inquiry Flow

Goal: non-developers can observe pipeline state and review broader analysis results.

Status: not started

- [ ] Real-time dashboard with SSE and frontend UI is implemented
- [ ] Review analysis source is implemented
- [ ] Slack source for customer inquiry classification is implemented
- [ ] UI-based routing condition management is implemented
- [ ] Cost dashboard is implemented

## Phase 4. Operational Maturity

Goal: the system reaches production-style operational depth suitable for technical demonstrations and write-ups.

Status: partial groundwork completed

- [x] Replay recovery SLO thresholds and alerting rules exist
- [x] Local Grafana dashboards are provisioned
- [x] Delivery plugin readiness checks exist
- [x] Replay failure threshold alerting exists
- [ ] Load testing with k6 is implemented
- [ ] Circuit breaker behavior is implemented for external dependency failures
- [ ] Prompt A/B testing is implemented
- [ ] Grafana dashboards are expanded to the intended finished operational set
- [ ] CI/CD pipeline is implemented
- [ ] Failure scenario testing exists for Kafka outages, LLM timeouts, and Redis outages

## Immediate Planning Note

If we continue from the current execution log, the next public delivery slice is:

- `email output plugin baseline`
