# ADR 0001: MVP Bootstrap Architecture

## Status

Accepted

## Context

Sentinel is intended to grow into an event analysis platform with multiple source plugins, multiple output plugins, multi-tenancy, cost control, and strong observability.

At the moment, however, the repository contains only planning documents, and the first objective is to make the Phase 1 MVP real.

That raises a few important questions.

- Should the system be split into separate services from day one
- Should WebFlux be introduced immediately
- How much of the plugin architecture should exist before the first working slice

## Decision

Phase 1 starts with the following strategy.

1. Build a single Spring Boot application first.
2. Structure it internally as a modular monolith.
3. Keep asynchronous stage boundaries on Kafka.
4. Use Kotlin as the implementation language.
5. Start with Spring MVC for the web layer.
6. Introduce `SourcePlugin` and `OutputPlugin` interfaces early, but implement only Sentry and Slack first.

## Rationale

### Single Application First

- It is the fastest way to validate the entire system end to end.
- It avoids introducing unnecessary deployment complexity too early.
- Consumers and output workers can still be separated into independent deployables later.

### Keep Kafka From The Beginning

- Event-driven flow is a defining property of the project.
- Kafka-based stage boundaries can be validated even within a single application.
- It creates the foundation for idempotency, DLQ handling, and replay strategies early.

### Use Kotlin

- Kotlin expresses domain models, configuration objects, and serialized contracts clearly and compactly.
- It works well with Spring Boot 3.
- It also fits future DSL-like configuration or prompt-related code well.

### Start With Spring MVC

- Webhook intake, management APIs, and SSE are all feasible without introducing WebFlux immediately.
- The implementation and debugging path is simpler at the beginning.
- Specific flows can still become reactive later if the need becomes clear.

### Introduce Plugin Interfaces Early

- One of the central success criteria is adding new integrations without changing the core.
- That means plugin contracts should exist from the beginning.
- The implementations themselves can remain minimal in the MVP.

## Consequences

The Phase 1 codebase should have these characteristics.

- One runnable unit that is easy to reproduce locally
- Kafka-based pipeline boundaries preserved from the beginning
- A clear path to future plugin expansion and service separation
- Design decisions that are easy to explain in technical writing and demos

## Open Items To Revisit Later

- when to move to multi-module Gradle builds
- whether WebFlux is needed at all
- whether `sentinel.routed-results` should become an independently operated topic
- when prompt storage should move fully into the database
- whether output workers should be deployed independently
