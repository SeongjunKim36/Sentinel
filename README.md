# Sentinel

Sentinel is an event-driven AI analysis platform that ingests events from external systems, analyzes them with LLMs, and delivers actionable insights to the right channels.

This repository is intended to be the public, open-source home of the project. It should contain only material that is appropriate to publish externally: source code, architecture and design documents, sanitized examples, and reproducible local development assets.

## Current Goal

The current goal is to turn the original Phase 1 MVP into a working codebase.

- Source: Sentry webhook
- Pipeline: event ingestion -> Kafka -> classification -> LLM root cause analysis -> evaluation and routing
- Output: Slack notification, with Telegram available for fast local delivery testing
- Infrastructure: local development with Docker Compose
- Observability: end-to-end traceability by trace ID

The current bootstrap implementation already includes:

- a real `POST /api/v1/webhooks/sentry` endpoint
- Sentry payload normalization into the shared `Event` contract
- publishing normalized raw events to Kafka topic `sentinel.raw-events`
- a `classification` consumer that reads `sentinel.raw-events`
- Redis-backed duplicate suppression in `classification` by `tenantId + sourceType + sourceId`
- publication of analyzable classified events to `sentinel.classified-events`
- an `analysis` consumer that reads `sentinel.classified-events`
- publication of bootstrap `AnalysisResult` records to `sentinel.analysis-results`
- an `evaluation` consumer that reads `sentinel.analysis-results`
- publication of routed results to `sentinel.routed-results`
- a `delivery` consumer that dispatches routed results to output plugins
- a real `SlackOutputPlugin` backed by Slack `chat.postMessage`
- a real `TelegramOutputPlugin` for end-to-end delivery testing
- a replaceable `LlmClient` boundary for future provider integrations
- integration tests that verify webhook-to-Kafka, raw-to-classified, classified-to-analysis, and analysis-to-routing delivery

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

## Telegram Test Setup

Telegram is the quickest way to test real outbound delivery while the local demo flow is still being refined.

1. Create a Telegram bot and collect the bot token.
2. Get the target chat ID for the bot.
3. Set the delivery channel to Telegram in your local environment.

```bash
export SENTINEL_DELIVERY_DEFAULT_CHANNELS=telegram
export SENTINEL_TELEGRAM_BOT_TOKEN=your-bot-token
export SENTINEL_TELEGRAM_CHAT_ID=your-chat-id
```

With those values in place, routed results will be sent to Telegram from the `delivery` stage.

## Slack Setup

Slack delivery uses the Web API `chat.postMessage` method with a bot token and a default channel.

```bash
export SENTINEL_DELIVERY_DEFAULT_CHANNELS=slack
export SENTINEL_SLACK_BOT_TOKEN=xoxb-your-bot-token
export SENTINEL_SLACK_DEFAULT_CHANNEL=C01234567
```

With those values in place, routed results will be sent to the configured Slack channel.

## Local Demo

You can exercise the full webhook-to-delivery path locally with the included sample payload.

1. Start the local infrastructure.
2. Run the application.
3. Configure either Telegram or Slack delivery in your shell.
4. Send the sample Sentry payload.

```bash
docker compose -f docker/compose.yml up -d
./gradlew bootRun
./scripts/send-sample-sentry-event.sh
```

The sample request body lives at `samples/sentry/checkout-timeout.json`.

## Classification Deduplication

Classification deduplication is enabled by default and prevents duplicate analyzable events from being sent downstream repeatedly.

```bash
export SENTINEL_CLASSIFICATION_DEDUPLICATION_ENABLED=true
export SENTINEL_CLASSIFICATION_DEDUPLICATION_TTL=PT30M
export SENTINEL_CLASSIFICATION_DEDUPLICATION_KEY_PREFIX=sentinel:classification:dedup
```

If Redis is unavailable, Sentinel falls back to in-memory deduplication in the running process.

## Suggested Next Steps

1. Replace the bootstrap `LlmClient` with a real provider integration.
2. Add OpenTelemetry and end-to-end trace propagation.
3. Add a reproducible local demo scenario that exercises failure and retry flows.
4. Persist delivery attempts and failures for auditability.
5. Add a dead-letter handling and replay workflow.
