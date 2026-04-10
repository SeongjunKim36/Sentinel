# Sentinel

Sentinel is an event-driven AI analysis platform that ingests events from external systems, analyzes them with LLMs, and delivers actionable insights to the right channels.

This repository is intended to be the public, open-source home of the project. It should contain only material that is appropriate to publish externally: source code, architecture and design documents, sanitized examples, and reproducible local development assets.

## Current Goal

The current goal is to turn the original Phase 1 MVP into a working codebase.

- Source: Sentry webhook
- Pipeline: event ingestion -> Kafka -> classification -> LLM root cause analysis -> evaluation and routing
- Output: Slack notification, with Telegram available for fast local delivery testing and analysis failure alerts
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
- retry + exponential backoff policy for LLM analysis calls
- fallback `analysis-failure` result publication when retries are exhausted
- publication of bootstrap `AnalysisResult` records to `sentinel.analysis-results`
- an `evaluation` consumer that reads `sentinel.analysis-results`
- preservation of explicit Telegram routing for `analysis-failure` alerts
- publication of routed results to `sentinel.routed-results`
- a `delivery` consumer that dispatches routed results to output plugins
- end-to-end trace propagation across HTTP and Kafka with OpenTelemetry
- persistence of every delivery attempt (success/failure) to PostgreSQL for auditability
- dead-letter persistence and Kafka publication for failed delivery attempts
- replay API for dead-letter events back into the routed-results topic
- switchable LLM provider mode (`bootstrap` or `openai`)
- a real `SlackOutputPlugin` backed by Slack `chat.postMessage`
- a real `TelegramOutputPlugin` for end-to-end delivery testing
- a replaceable `LlmClient` boundary for future provider integrations
- integration tests that verify webhook-to-Kafka, raw-to-classified, classified-to-analysis, and analysis-to-routing delivery

## Current Stack

- Spring Boot `4.0.5`
- Kotlin `2.2.21`
- Gradle `9.4.1` wrapper
- Spring Modulith `2.0.5`
- OpenTelemetry (Micrometer tracing bridge + OTLP exporter)
- Prometheus + Grafana provisioning for local operations dashboards and replay recovery alerts
- PostgreSQL, Redis, Kafka, and Jaeger for local infrastructure

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

## Sentry Webhook Signature Validation

Sentinel can validate Sentry webhook signatures before accepting payloads.

```bash
export SENTINEL_SENTRY_WEBHOOK_SIGNATURE_VALIDATION_ENABLED=true
export SENTINEL_SENTRY_WEBHOOK_SECRET=your-sentry-client-secret
export SENTINEL_SENTRY_WEBHOOK_MAX_TIMESTAMP_SKEW=PT5M
```

When enabled, requests missing valid `Sentry-Hook-Signature` or acceptable `Sentry-Hook-Timestamp` are rejected with HTTP `401`.

For local bootstrap flows, validation is disabled by default.

## LLM Provider Setup

The analysis module supports `bootstrap` and `openai` provider modes.

```bash
export SENTINEL_ANALYSIS_LLM_PROVIDER=bootstrap
```

To use OpenAI for real analysis calls:

```bash
export SENTINEL_ANALYSIS_LLM_PROVIDER=openai
export SENTINEL_OPENAI_API_KEY=your-api-key
export SENTINEL_OPENAI_MODEL=gpt-4.1-mini
export SENTINEL_ANALYSIS_LLM_PROMPT_VERSION=openai-v1
```

Optional tuning:

```bash
export SENTINEL_OPENAI_API_BASE_URL=https://api.openai.com
export SENTINEL_OPENAI_TEMPERATURE=0.2
```

Prompt templates are externalized under:

- `src/main/resources/prompts/openai-v1/`
- `src/main/resources/prompts/openai-v2/`

Prompt rollout controls (default + canary) are configurable:

```bash
export SENTINEL_ANALYSIS_LLM_PROMPT_VERSION=openai-v1
export SENTINEL_ANALYSIS_LLM_PROMPT_CANARY_VERSION=openai-v2
export SENTINEL_ANALYSIS_LLM_PROMPT_CANARY_PERCENTAGE=10
```

Tenant-specific overrides can be defined in `application.yml` with `sentinel.analysis.llm.prompt-rollout.tenant-overrides`.

## Routing Policy Setup

Evaluation routing is externally configurable by severity and category.

```bash
export SENTINEL_EVALUATION_MINIMUM_CONFIDENCE=0.5
```

Configure channel/priority mapping in `application.yml` under `sentinel.evaluation.routing`:

- `default-channels`
- `severity-policies`
- `category-policies`

Example category override:

```yaml
sentinel:
  evaluation:
    routing:
      category-policies:
        replay-failure-alert:
          channels: [telegram, slack]
          priority: IMMEDIATE
```

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
Local observability endpoints from Docker Compose:

- Jaeger: `http://localhost:16686`
- Prometheus: `http://localhost:9090`
- Prometheus Alerts: `http://localhost:9090/alerts`
- Grafana: `http://localhost:3000` (`admin` / `sentinel`)

## OpenTelemetry Tracing

Sentinel exports traces via OTLP and propagates trace context through Kafka topics.

```bash
export SENTINEL_TRACING_SAMPLING_PROBABILITY=1.0
export SENTINEL_OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318/v1/traces
```

With `docker compose -f docker/compose.yml up -d`, Jaeger UI is available at `http://localhost:16686`.

The webhook response now includes a `traceId`, and the same trace ID is carried into normalized event metadata for end-to-end correlation.

## Pipeline Metrics

Sentinel now emits stage-level business metrics for ingestion, classification filtering, and delivery outcomes.

- `sentinel.pipeline.ingestion.events` with tag `source_type`
- `sentinel.pipeline.classification.events` with tags `category`, `outcome`, `filter_reason`
- `sentinel.pipeline.delivery.attempts` with tags `tenant_id`, `channel`, `category`, `outcome`, `failure_type`
- `sentinel.pipeline.delivery.fanout` summary for number of target channels per routed result
- `sentinel.deadletter.replay.events` counter tagged by `tenant_id`, `channel`, and replay `outcome`
- `sentinel.deadletter.replay.mttr.seconds` summary for replay recovery MTTR per tenant/channel

Example PromQL panels:

```promql
sum by (source_type) (rate(sentinel_pipeline_ingestion_events_total[5m]))
sum by (outcome, filter_reason) (rate(sentinel_pipeline_classification_events_total[5m]))
sum by (channel, outcome, failure_type) (rate(sentinel_pipeline_delivery_attempts_total[5m]))
sum(rate(sentinel_pipeline_delivery_fanout_sum[5m])) / sum(rate(sentinel_pipeline_delivery_fanout_count[5m]))
```

## Grafana Dashboard

When Docker Compose is running, Grafana auto-loads `Sentinel Operations Overview`.

- Dashboard file: `docker/grafana/dashboards/01-sentinel-operations-overview.json`
- Provisioning files:
  - `docker/grafana/provisioning/datasources/datasource.yml`
  - `docker/grafana/provisioning/dashboards/provider.yml`
- Prometheus scrape config: `docker/prometheus/prometheus.yml`

The dashboard includes:

- stage throughput and classification outcome trends
- delivery attempts by tenant/channel/category/outcome
- replay outcomes and replay failure alert delivery frequency
- replay recovery MTTR by tenant/channel

## Replay Recovery SLO Alerts

Prometheus now loads replay recovery SLO recording and alerting rules from:

- `docker/prometheus/rules/sentinel-replay-slo-alerts.yml`

Configured SLO thresholds:

- replay recovery ratio: warning below `95%`, critical below `90%`
- replay MTTR average: warning above `300s`, critical above `900s`
- replay blocked outcomes: warning when blocked outcomes are detected

The rule loader is configured in:

- `docker/prometheus/prometheus.yml` (`rule_files`)

## Delivery Attempt Audit

Sentinel persists each channel delivery attempt (including failures and plugin-missing cases) in PostgreSQL table `delivery_attempt`.

- Flyway migration: `V2__delivery_attempts.sql`
- Query endpoint: `GET /api/v1/delivery-attempts`
- Supported filters: `eventId`, `tenantId`, `channel`, `success`, `limit`

## Dead-Letter Handling and Replay

When delivery fails, Sentinel records a dead-letter event in PostgreSQL and publishes it to Kafka topic `sentinel.dead-letter`.

- Flyway migrations: `V3__dead_letter_events.sql`, `V4__dead_letter_replay_operator_note.sql`, `V5__dead_letter_replay_audits.sql`
- Query endpoint: `GET /api/v1/dead-letters`
- Replay endpoint: `POST /api/v1/dead-letters/{id}/replay`
- Replay audit endpoint: `GET /api/v1/dead-letters/{id}/replay-audits`
- Replay currently supports payload type `ANALYSIS_RESULT` and republishes to `sentinel.routed-results`

Replay guardrails are enabled by default.

```bash
export SENTINEL_DEAD_LETTER_REPLAY_MAX_REPLAY_ATTEMPTS=3
export SENTINEL_DEAD_LETTER_REPLAY_COOLDOWN=PT5M
export SENTINEL_DEAD_LETTER_REPLAY_REQUIRE_OPERATOR_NOTE=true
export SENTINEL_DEAD_LETTER_REPLAY_FAILURE_ALERT_ENABLED=true
export SENTINEL_DEAD_LETTER_REPLAY_FAILURE_ALERT_THRESHOLD=3
export SENTINEL_DEAD_LETTER_REPLAY_FAILURE_ALERT_WINDOW=PT30M
export SENTINEL_DEAD_LETTER_REPLAY_FAILURE_ALERT_CHANNELS=telegram
```

Replay API hardening options:

```bash
export SENTINEL_DEAD_LETTER_API_MAX_QUERY_LIMIT=200
export SENTINEL_DEAD_LETTER_API_MAX_OPERATOR_NOTE_LENGTH=500
export SENTINEL_DEAD_LETTER_REPLAY_AUTH_ENABLED=true
export SENTINEL_DEAD_LETTER_REPLAY_AUTH_HEADER_NAME=X-Sentinel-Replay-Token
export SENTINEL_DEAD_LETTER_REPLAY_AUTH_TOKEN=replace-with-strong-token
```

When replay authorization is enabled, `POST /api/v1/dead-letters/{id}/replay` requires the configured header token and returns `401` if missing or invalid.

`POST /api/v1/dead-letters/{id}/replay` can include an operator note:

```json
{
  "operatorNote": "Telegram outage recovered, replaying after verification"
}
```

If max attempts are exhausted or cooldown is active, replay is blocked with HTTP `409 Conflict`.

When replay failures for the same tenant and channel reach the configured threshold within the configured window, Sentinel publishes a `replay-failure-alert` routed result to the configured alert channels.

## Classification Deduplication

Classification deduplication is enabled by default and prevents duplicate analyzable events from being sent downstream repeatedly.

```bash
export SENTINEL_CLASSIFICATION_DEDUPLICATION_ENABLED=true
export SENTINEL_CLASSIFICATION_DEDUPLICATION_TTL=PT30M
export SENTINEL_CLASSIFICATION_DEDUPLICATION_KEY_PREFIX=sentinel:classification:dedup
```

If Redis is unavailable, Sentinel falls back to in-memory deduplication in the running process.

## Analysis Retry and Failure Routing

Analysis calls are retried with configurable exponential backoff before emitting a fallback `analysis-failure` result.

```bash
export SENTINEL_ANALYSIS_RETRY_MAX_ATTEMPTS=3
export SENTINEL_ANALYSIS_RETRY_INITIAL_BACKOFF=PT0.2S
export SENTINEL_ANALYSIS_RETRY_MULTIPLIER=2.0
export SENTINEL_ANALYSIS_RETRY_MAX_BACKOFF=PT2S
export SENTINEL_ANALYSIS_FAILURE_ROUTING_CHANNELS=telegram
```

When retries are exhausted, Sentinel publishes a critical `analysis-failure` result and routes it to Telegram immediately by default.

## Suggested Next Steps

1. Add Prometheus alerting rules for sustained replay failures and MTTR SLO breach.
2. Externalize prompts/templates with versioned storage and rollout strategy.
3. Add OpenAI provider integration tests with mock HTTP server and failure scenarios.
