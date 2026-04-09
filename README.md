# Sentinel

Sentinel is an event-driven AI analysis platform that ingests events from external systems, analyzes them with LLMs, and delivers actionable insights to the right channels.

This repository is intended to be the public, open-source home of the project. It should contain only material that is appropriate to publish externally: source code, architecture and design documents, sanitized examples, and reproducible local development assets.

## Current Goal

The current goal is to turn the original Phase 1 MVP into a working codebase.

- Source: Sentry webhook
- Pipeline: event ingestion -> Kafka -> classification -> LLM root cause analysis -> evaluation and routing
- Output: Slack notification
- Infrastructure: local development with Docker Compose
- Observability: end-to-end traceability by trace ID

The current bootstrap implementation already includes:

- a real `POST /api/v1/webhooks/sentry` endpoint
- Sentry payload normalization into the shared `Event` contract
- publishing normalized raw events to Kafka topic `sentinel.raw-events`
- a `classification` consumer that reads `sentinel.raw-events`
- publication of analyzable classified events to `sentinel.classified-events`
- integration tests that verify both webhook-to-Kafka and raw-to-classified Kafka delivery

## Current Stack

- Spring Boot `4.0.5`
- Kotlin `2.2.21`
- Gradle `9.4.1` wrapper
- Spring Modulith `2.0.5`
- PostgreSQL, Redis, and Kafka for local infrastructure

## Documentation

- [00. Documentation Index](/Users/skl-wade/Wade/Sentinel/docs/00-docs-index.md)
- [01. Sentinel Platform Plan](/Users/skl-wade/Wade/Sentinel/docs/01-foundation/01-sentinel-platform-plan.md)
- [02. MVP Implementation Plan](/Users/skl-wade/Wade/Sentinel/docs/01-foundation/02-mvp-implementation-plan.md)
- [03. Project Structure Proposal](/Users/skl-wade/Wade/Sentinel/docs/01-foundation/03-project-structure.md)
- [ADR 0001 - MVP Bootstrap Architecture](/Users/skl-wade/Wade/Sentinel/docs/99-adr/0001-mvp-bootstrap-architecture.md)

## Working Principles

- Do not reduce the scope of the documented MVP.
- Keep the implementation strategy simple enough to reach end-to-end execution quickly.
- Start with a modular monolith to validate the full pipeline.
- Strengthen plugin abstraction and operational maturity incrementally.
- Keep the repository public-facing and English-first.

## What Belongs In This Repository

- Source code for the public implementation
- English documentation and ADRs
- Docker and local development assets
- Sanitized sample payloads and demo scenarios
- Architecture diagrams and operational notes that are safe to publish

## What Should Stay Out Of This Repository

- Private notes in Korean or any other internal-only planning artifacts
- Real API keys, webhook secrets, or production credentials
- Company-specific payloads, customer data, or sensitive logs
- Internal experiments that cannot be safely published

## Local-Only Working Notes

For internal planning and day-to-day development notes, use a local folder such as `docs-private/` or `notes-private/`.

- These folders are intentionally ignored by Git.
- They can be written in Korean.
- They are meant for temporary decisions, implementation notes, and working drafts that do not belong in the public repository.

## Suggested Next Steps

1. Introduce Redis-backed deduplication to the `classification` stage.
2. Implement the first Analyzer consumer for `sentinel.classified-events`.
3. Add an LLM client abstraction and the first provider integration.
4. Replace the Slack placeholder with a real delivery client.
5. Add OpenTelemetry and end-to-end trace propagation.
