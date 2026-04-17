# 02. MVP Implementation Plan

## Goal

The first concrete objective is to turn the documented Phase 1 MVP into working software.

> When a Sentry error event arrives, Sentinel should process it through a Kafka-based pipeline, run LLM-powered root cause analysis, and deliver actionable output to Slack.

This document focuses less on what Sentinel is and more on the order and structure in which the MVP should be built.

## Implementation Strategy

The MVP keeps the documented feature scope intact, but simplifies the deployment model.

- Deployment model: single Spring Boot application
- Internal architecture: modular monolith verified with Spring Modulith
- Asynchronous processing: Kafka topics
- Persistent storage: PostgreSQL and Redis
- First integrations: Sentry and Slack
- First analysis path: incident and error analysis

Why this is the right starting point:

- It preserves the event-driven architecture that defines the project.
- It prioritizes end-to-end validation over premature service decomposition.
- It still leaves a clean path to split consumers or plugins into separate services later.

## The Flow That Must Exist In The MVP

If the following flow works, the core MVP is alive.

1. Sentry sends a webhook.
2. Sentinel normalizes the payload into a shared `Event`.
3. Sentinel publishes the event to `sentinel.raw-events`.
4. The Classifier consumes the event and removes duplicates or noise.
5. Only analyzable events move to `sentinel.classified-events`.
6. The Analyzer calls an LLM and creates an `AnalysisResult`.
7. The Evaluator determines severity, confidence, and routing priority.
8. Slack output sends the final notification.
9. A trace ID and key processing metrics exist across the full path.

## Build Priorities

### 1. Application Skeleton

The first milestone is a runnable local environment with an extensible project shape.

- Create the Spring Boot project
- Use Kotlin and Gradle Kotlin DSL
- Establish the base package layout around application modules
- Create Docker Compose for local dependencies
- Define local runtime profiles

### 2. Domain Model And Contracts

Lock the central concepts into code before implementing detailed behavior.

- `Event`
- `AnalysisResult`
- `PipelineConfig`
- `SourcePlugin`
- `OutputPlugin`
- Kafka topic naming and key strategy

At this stage, clear types and boundaries matter more than feature completeness.

### 3. Ingestion Path

Sentry webhook ingestion must be able to get a real event into the system.

- `POST /api/v1/webhooks/sentry`
- Sentry payload validation
- Event normalization
- Kafka publishing
- Logging and trace propagation

### 4. Three Pipeline Stages

Implement the documented `Classifier`, `Analyzer`, and `Evaluator` as distinct responsibilities.

- Classifier: deduplication, exclusion filtering, classification tags
- Analyzer: prompt loading, LLM invocation, result parsing, cost recording
- Evaluator: confidence scoring, severity classification, routing priority

The consumers should be separate at the responsibility level, even if they run inside the same application process.

### 5. Output Delivery

Slack output is the first visible user-facing value in the MVP.

- Severity-aware message formatting
- Channel or thread delivery strategy
- Retry and delivery logging for failures

### 6. Operational Baseline

Even the MVP should expose minimum operational visibility.

- OpenTelemetry trace propagation
- Micrometer metrics
- LLM latency, token usage, and cost recording
- Dead letter queue publishing

## Suggested First Sprints

The first implementation cycle should focus on vertical slices instead of horizontal infrastructure buildup.

### Sprint 1

- Create the Spring Boot project
- Add Docker Compose
- Add the shared domain models
- Implement `POST /api/v1/webhooks/sentry`
- Confirm publishing to `sentinel.raw-events`

Done when:

- A local webhook request results in a Kafka message on the raw events topic

### Sprint 2

- Implement the Classifier consumer
- Add Redis-based deduplication
- Publish to `sentinel.classified-events`

Done when:

- Repeated ingestion of the same event is suppressed correctly

### Sprint 3

- Implement the Analyzer
- Add an LLM provider abstraction
- Load prompt templates
- Create `AnalysisResult`

Done when:

- A classified event becomes a structured analysis result

### Sprint 4

- Implement the Evaluator
- Implement Slack output
- Add `sentinel.routed-results` or route internally if needed

Done when:

- A Sentry incident produces a Slack notification

### Sprint 5

- Add OpenTelemetry
- Expose Prometheus metrics
- Implement the dead letter queue path
- Clean up baseline operational logging

Done when:

- A single event can be followed end-to-end by trace ID

## Recommended Implementation Choices

### Service Boundaries

Do not split the system into many deployable services too early.

- The current goal is design validation, not distributed deployment
- Keep boundaries explicit in code so separation remains possible later
- Use Spring Modulith verification to keep module boundaries honest from the start

### Kafka Topics

For the MVP, these topics are enough to implement first.

- `sentinel.raw-events`
- `sentinel.classified-events`
- `sentinel.analysis-results`
- `sentinel.dead-letter`

`sentinel.routed-results` can be formalized once the routing layer becomes more independent.

### Prompt Storage

Prompts should not begin life as hard-coded constants if that can be avoided.

- Early implementation: store prompts in a database or resource files with version identifiers
- Minimum requirement: every result must record `promptVersion`

### LLM Integration

Even in the MVP, it is worth defining a multi-provider interface first.

- Primary model: Claude
- Fallback model: OpenAI
- Structure: `LlmClient` interface plus provider-specific implementations

## MVP Completion Criteria

The Phase 1 implementation is in good shape when all of the following are true.

- The Sentry webhook to Slack path works end-to-end
- Events, analysis results, cost data, and trace data are stored or emitted
- Kafka, PostgreSQL, and Redis can be reproduced locally
- Pipeline responsibilities are clearly separated in code
- The codebase already points toward the Phase 2 plugin architecture

The current repository review for these criteria is recorded in [05. Phase 1 Completion Review](/Users/skl-wade/Wade/Sentinel/docs/01-foundation/05-phase-1-completion-review.md).
