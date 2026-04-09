# 03. Project Structure Proposal

## Summary

Sentinel currently starts from planning documents only, which makes it especially important to define a repository structure before code starts to accumulate.

Recommended baseline:

- Language: Kotlin
- Framework: Spring Boot 4.x
- Build tool: Gradle Kotlin DSL
- Architecture style: modular monolith
- Module strategy: direct application modules under the root package, aligned with Spring Modulith

## Why This Structure Fits Sentinel

- The project has significant architectural complexity, but the first milestone must still be a fast end-to-end vertical slice.
- A single Spring Boot app can host webhook intake, Kafka consumers, SSE, metrics, and persistence without unnecessary operational overhead.
- Kotlin keeps domain models, config objects, and API contracts concise.
- Spring Modulith gives the project structural verification and module-level documentation from day one.
- The structure remains flexible enough to split plugins or pipeline stages later.

## Recommended Directory Layout

```text
sentinel/
├── README.md
├── docs/
├── docker/
│   ├── compose.yml
│   └── ...
├── gradle/
│   └── wrapper/
├── gradlew
├── gradlew.bat
├── src/
│   ├── main/
│   │   ├── kotlin/io/github/seongjunkim36/sentinel/
│   │   │   ├── SentinelApplication.kt
│   │   │   ├── SentinelTopics.kt
│   │   │   ├── shared/
│   │   │   ├── ingestion/
│   │   │   │   ├── api/
│   │   │   │   └── application/
│   │   │   ├── classification/
│   │   │   ├── analysis/
│   │   │   ├── evaluation/
│   │   │   └── delivery/
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/
│   └── test/
│       └── kotlin/io/github/seongjunkim36/sentinel/
└── build.gradle.kts
```

## Module Responsibilities

The most important adjustment is this: do not start with top-level technical layers such as `messaging`, `persistence`, and `observability` as peer packages. Spring Modulith works best when the root package contains application modules, not technical buckets.

### `shared`

Contains shared contracts that multiple modules can depend on safely.

- `Event`
- `ClassifiedEvent`
- `AnalysisResult`
- `PipelineConfig`
- plugin interfaces
- enums and delivery contracts

### `ingestion`

Owns webhook intake and source normalization.

- webhook controller
- source plugin implementations
- intake service
- raw event publishing boundary

### `classification`

Handles lightweight pre-LLM event decisions.

- deduplication
- exclusion rules
- category tags
- analyzability decisions

### `analysis`

Owns LLM-based enrichment and structured result creation.

- `LlmClient` abstraction and provider implementations
- prompt loading
- provider calls
- parsing
- cost and latency capture

### `evaluation`

Turns analysis output into operational routing decisions.

- confidence scoring
- severity decisions
- routing priority
- duplicate suppression

### `delivery`

Delivers results to external channels.

- output plugin registry
- Slack implementation
- later expansion to email, dashboard, and Jira

## Boundary Rules

There is no need to start with a multi-module Gradle build. Strict module boundaries matter more.

- application modules should be direct subpackages of the root package
- shared contracts should live in `shared`
- infrastructure details should sit inside the module that owns them
- controllers should orchestrate requests, not contain business rules
- module boundaries should be verified with Spring Modulith tests

## Minimum Packages For Phase 1

The initial implementation does not need every package filled out. These are enough to start.

```text
io/github/seongjunkim36/sentinel/
├── SentinelApplication.kt
├── shared/
├── ingestion/
├── classification/
├── analysis/
├── evaluation/
└── delivery/
```

## Environment Files Needed Early

- `docker/compose.yml`
- `.env.example`
- `src/main/resources/application.yml`
- `src/main/resources/db/migration/V1__bootstrap.sql`

## Testing Strategy

The test suite should be layered from the beginning.

- Unit tests: classifier rules, evaluator scoring, payload normalization
- Integration tests: webhook to Kafka publishing, Kafka consumer to persistence
- Contract tests: Sentry normalization, Slack message rendering
- Modulith verification tests: application module boundary checks
- End-to-end tests: local scenarios backed by Docker Compose

## Immediate Implementation Units

The most natural first code tasks are:

1. Create the Spring Boot project
2. Add Docker Compose
3. Add `Event` and `AnalysisResult`
4. Add the Sentry webhook endpoint
5. Add the Kafka producer

Once those five pieces exist, Sentinel stops being only a planning repository and becomes an executable project skeleton.
