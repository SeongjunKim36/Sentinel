# 03. Project Structure Proposal

## Summary

Sentinel currently starts from planning documents only, which makes it especially important to define a repository structure before code starts to accumulate.

Recommended baseline:

- Language: Kotlin
- Framework: Spring Boot 3.x
- Build tool: Gradle Kotlin DSL
- Architecture style: modular monolith
- Package strategy: feature boundaries plus shared platform concerns

## Why This Structure Fits Sentinel

- The project has significant architectural complexity, but the first milestone must still be a fast end-to-end vertical slice.
- A single Spring Boot app can host webhook intake, Kafka consumers, SSE, metrics, and persistence without unnecessary operational overhead.
- Kotlin keeps domain models, config objects, and API contracts concise.
- The structure remains flexible enough to split plugins or pipeline stages later.

## Recommended Directory Layout

```text
sentinel/
├── README.md
├── docs/
├── docker/
│   ├── compose.yml
│   ├── grafana/
│   └── prometheus/
├── src/
│   ├── main/
│   │   ├── kotlin/com/sentinel/
│   │   │   ├── SentinelApplication.kt
│   │   │   ├── common/
│   │   │   │   ├── config/
│   │   │   │   ├── errors/
│   │   │   │   ├── tracing/
│   │   │   │   └── util/
│   │   │   ├── domain/
│   │   │   │   ├── event/
│   │   │   │   ├── analysis/
│   │   │   │   ├── pipeline/
│   │   │   │   └── prompt/
│   │   │   ├── ingestion/
│   │   │   │   ├── api/
│   │   │   │   ├── application/
│   │   │   │   └── plugin/
│   │   │   ├── classifier/
│   │   │   │   ├── application/
│   │   │   │   ├── consumer/
│   │   │   │   └── support/
│   │   │   ├── analyzer/
│   │   │   │   ├── application/
│   │   │   │   ├── llm/
│   │   │   │   ├── prompt/
│   │   │   │   └── consumer/
│   │   │   ├── evaluator/
│   │   │   │   ├── application/
│   │   │   │   ├── consumer/
│   │   │   │   └── routing/
│   │   │   ├── output/
│   │   │   │   ├── application/
│   │   │   │   ├── plugin/
│   │   │   │   └── slack/
│   │   │   ├── persistence/
│   │   │   │   ├── postgres/
│   │   │   │   └── redis/
│   │   │   ├── messaging/
│   │   │   │   ├── kafka/
│   │   │   │   └── dlq/
│   │   │   ├── observability/
│   │   │   │   ├── metrics/
│   │   │   │   └── telemetry/
│   │   │   └── api/
│   │   │       ├── results/
│   │   │       ├── pipelines/
│   │   │       └── prompts/
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── db/migration/
│   │       └── prompts/
│   └── test/
│       ├── kotlin/com/sentinel/
│       └── resources/
└── build.gradle.kts
```

## Package Responsibilities

### `domain`

Contains the core models and contracts of the platform.

- `Event`
- `AnalysisResult`
- `PipelineConfig`
- plugin interfaces
- repository contracts

### `ingestion`

Responsible for converting external payloads into standardized internal events.

- webhook controller
- source plugin registry
- normalization service
- raw event publisher

### `classifier`

Handles lightweight decisions before any LLM call.

- deduplication
- exclusion rules
- analyzability checks
- classification tags

### `analyzer`

Owns LLM invocation and analysis result generation.

- prompt selection
- provider calls
- response parsing
- cost, token, and latency recording

### `evaluator`

Turns analysis output into operationally useful signals.

- severity classification
- confidence scoring
- routing priority decisions
- duplicate insight suppression

### `output`

Delivers analysis results to external destinations.

- output plugin registry
- Slack implementation
- later expansion to email, dashboard, and Jira

### `messaging`

Encapsulates Kafka producer and consumer logic, plus DLQ behavior.

- topic definitions
- serializers and deserializers
- shared publish helpers
- retry and dead letter handling

### `observability`

Owns metrics, tracing, and structured operational visibility.

- OpenTelemetry
- Micrometer
- structured logging

## Boundary Rules

There is no need to start with a multi-module Gradle build. Strict package boundaries matter more.

- `domain` should not depend directly on infrastructure implementations
- plugin interfaces should live in domain or application-level contracts
- Kafka, Redis, and PostgreSQL details should be hidden behind `messaging` and `persistence`
- controllers should orchestrate requests, not contain business rules

## Minimum Packages For Phase 1

The initial implementation does not need every package filled out. These are enough to start.

```text
com/sentinel/
├── SentinelApplication.kt
├── domain/
├── ingestion/
├── classifier/
├── analyzer/
├── evaluator/
├── output/slack/
├── messaging/kafka/
├── persistence/redis/
└── observability/
```

## Environment Files Needed Early

- `docker/compose.yml`
- `.env.example`
- `src/main/resources/application.yml`
- `src/main/resources/application-local.yml`
- `src/main/resources/db/migration/V1__init.sql`

## Testing Strategy

The test suite should be layered from the beginning.

- Unit tests: classifier rules, evaluator scoring, payload normalization
- Integration tests: webhook to Kafka publishing, Kafka consumer to persistence
- Contract tests: Sentry normalization, Slack message rendering
- End-to-end tests: local scenarios backed by Docker Compose

## Immediate Implementation Units

The most natural first code tasks are:

1. Create the Spring Boot project
2. Add Docker Compose
3. Add `Event` and `AnalysisResult`
4. Add the Sentry webhook endpoint
5. Add the Kafka producer

Once those five pieces exist, Sentinel stops being only a planning repository and becomes an executable project skeleton.
