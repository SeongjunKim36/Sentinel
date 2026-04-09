# 01. Sentinel - Intelligent Event Analysis Platform

## 1. Project Definition

Sentinel is an event-driven AI analysis platform that collects events from multiple external sources, analyzes and classifies them with LLMs, and delivers the resulting insights to the appropriate channels.

Its core value is simple: transform raw operational data into insights that a human can act on immediately.

### What This Project Is Not

- It is not an ML project for training or fine-tuning foundation models.
- It is not a single-purpose chatbot.
- It is not tied to a single domain. The platform should support arbitrary event sources through a plugin architecture.

---

## 2. Why Build This Project

This project exists to demonstrate multiple engineering capabilities within one coherent system.

| Capability | How It Shows Up |
|------|-----------------|
| Event-Driven Architecture | Kafka-based asynchronous pipeline, exactly-once processing, dead letter queue |
| LLM Engineering | Prompt management, streaming, fallback handling, cost control, caching, evaluation pipeline |
| Plugin Architecture | Source and output integrations abstracted behind interfaces so new integrations do not require core changes |
| System Design | CQRS, pipeline patterns, multi-tenancy, scalability |
| Operations and Reliability | OpenTelemetry tracing, metrics, cost dashboards, incident response records |

The goal is not just to produce "working code." It should function as an engineering portfolio that captures design reasoning, implementation trade-offs, and operational maturity.

---

## 3. System Architecture Overview

```text
┌─────────────────────────────────────────────────────────┐
│                    Source Plugins                      │
│                                                         │
│  Sentry Webhook  │  Slack Event  │  RSS Crawler        │
│  Log Stream      │  Review API   │  Custom Webhook     │
└────────┬────────────────┬───────────────┬───────────────┘
         │                │               │
         ▼                ▼               ▼
┌─────────────────────────────────────────────────────────┐
│                  Kafka Event Bus                       │
│                                                         │
│  topic: sentinel.raw-events                            │
│  topic: sentinel.analysis-results                      │
│  topic: sentinel.dead-letter                           │
└────────┬────────────────────────────────┬───────────────┘
         │                                │
         ▼                                ▼
┌──────────────────────────┐   ┌──────────────────────────┐
│    Analysis Pipeline     │   │      Result Router       │
│                          │   │                          │
│  1. Classifier           │   │  Route insights to the   │
│     - event tagging      │   │  right output channel    │
│     - dedupe and noise   │   │  based on urgency and    │
│       filtering          │   │  result type             │
│                          │   └─────────┬────────────────┘
│  2. Analyzer             │             │
│     - LLM invocation     │             ▼
│     - root cause         │   ┌──────────────────────────┐
│       analysis           │   │      Output Plugins      │
│     - sentiment analysis │   │                          │
│     - summary extraction │   │  Slack messages/threads  │
│                          │   │  Email briefings         │
│  3. Evaluator            │   │  Dashboard (SSE)         │
│     - urgency scoring    │   │  Jira ticket creation    │
│     - confidence scoring │   │  Kakao Alimtalk          │
│     - routing decisions  │   │                          │
└──────────────────────────┘   └──────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│                Cross-Cutting Concerns                  │
│                                                         │
│  - OpenTelemetry distributed tracing                    │
│  - LLM cost tracking and usage metrics                  │
│  - Prompt version management                            │
│  - Tenant-specific configuration                        │
│  - APIs for settings, dashboards, and pipeline health   │
└─────────────────────────────────────────────────────────┘
```

---

## 4. Core Domain Model

### 4.1 Event

`Event` is the shared representation for all ingested data. Each source plugin converts external payloads into this structure.

```text
Event {
  id: UUID
  sourceType: string
  sourceId: string
  tenantId: string
  payload: JSON
  metadata: {
    receivedAt: timestamp
    sourceVersion: string
    headers: map
  }
}
```

### 4.2 AnalysisResult

`AnalysisResult` is the output produced by the analysis pipeline after processing an event.

```text
AnalysisResult {
  id: UUID
  eventId: UUID
  tenantId: string
  category: string
  severity: enum
  confidence: float
  summary: string
  detail: {
    analysis: string
    actionItems: string[]
    relatedEvents: UUID[]
  }
  llmMetadata: {
    model: string
    promptVersion: string
    tokenUsage: {
      input: int
      output: int
    }
    costUsd: float
    latencyMs: int
  }
  routing: {
    channels: string[]
    priority: enum
  }
  createdAt: timestamp
}
```

### 4.3 PipelineConfig

`PipelineConfig` defines, for each tenant, which sources are active, what kind of analysis is performed, and where the results should be sent.

```text
PipelineConfig {
  tenantId: string
  sources: [{
    type: string
    config: JSON
    filters: {
      include: string[]
      exclude: string[]
    }
  }]
  analysis: {
    promptTemplate: string
    model: string
    maxTokens: int
    temperature: float
    fallbackModel: string
  }
  routing: [{
    channel: string
    config: JSON
    conditions: {
      minSeverity: enum
      categories: string[]
    }
  }]
  costLimit: {
    dailyMaxUsd: float
    alertThresholdPercent: int
  }
}
```

---

## 5. Plugin Interfaces

### 5.1 Source Plugin

Source plugins receive data from external systems and convert it into `Event`.

```text
interface SourcePlugin {
  getType(): string
  getConfigSchema(): JSONSchema

  handleWebhook(request: WebhookRequest): Event[]
  poll(config: JSON, since: timestamp): Event[]
  normalize(rawPayload: JSON): Event
}
```

Target source plugins:

| Plugin | Intake Method | Purpose |
|--------|---------------|---------|
| `SentrySource` | Webhook | Application errors and incident events |
| `SlackSource` | Event API | Customer inquiries and internal messages |
| `RssSource` | Polling | News and blog feeds |
| `LogStreamSource` | Kafka/Fluentd | Application log streams |
| `ReviewApiSource` | Polling | Marketplace reviews |
| `GenericWebhookSource` | Webhook | User-defined webhook sources |

### 5.2 Output Plugin

Output plugins deliver analysis results to external destinations.

```text
interface OutputPlugin {
  getType(): string
  getConfigSchema(): JSONSchema

  send(result: AnalysisResult, config: JSON): DeliveryResult
  sendBatch(results: AnalysisResult[], config: JSON): DeliveryResult
}
```

Target output plugins:

| Plugin | Delivery Method | Purpose |
|--------|-----------------|---------|
| `SlackOutput` | Slack API | Channel or thread messages and urgent alerts |
| `EmailOutput` | SMTP | Daily or weekly digest briefings |
| `DashboardOutput` | SSE/WebSocket | Real-time dashboard pushes |
| `JiraOutput` | Jira API | Automatic issue creation |
| `KakaoAlimtalkOutput` | Alimtalk API | Mobile notifications |

---

## 6. Analysis Pipeline Details

The pipeline consists of three stages. Each stage runs as an independent Kafka consumer and publishes events between stages to keep responsibilities loosely coupled.

### 6.1 Stage 1: Classifier

This is a lightweight stage that should not require an LLM call.

- Detect duplicate events within a time window using `sourceId`
- Filter noise using configured exclusion rules
- Tag events based on `sourceType` and payload patterns
- Forward only events that require LLM analysis

Why separate it:
LLM calls are expensive. Filtering out most noise before analysis can reduce cost dramatically.

### 6.2 Stage 2: Analyzer

This is the core stage where LLM-based analysis happens.

- Load prompt templates, potentially per event category
- Invoke the LLM in streaming mode
- Apply a timeout of 30 seconds
- Retry up to 3 times with exponential backoff
- Retry with a fallback model if the primary model fails
- Record token usage, cost, and latency for every invocation
- Parse responses into `AnalysisResult`
- Send failed events to the dead letter queue

Prompt management principles:

- Treat prompts as data rather than code
- Version prompts in a database or equivalent persistent store
- Store the prompt version used for every analysis result
- Support A/B testing by applying different prompts to the same class of events

### 6.3 Stage 3: Evaluator

This stage scores the quality and urgency of the analysis result and decides how it should be routed.

- Compute confidence based on structural validity and presence of supporting rationale
- Determine urgency using category, keywords, and configured rules
- Route based on severity:
  - `IMMEDIATE`: `CRITICAL` or `HIGH`
  - `BATCHED`: `MEDIUM`, grouped in 10-minute windows
  - `DIGEST`: `LOW` or `INFO`, included in daily digest output
- Suppress duplicate insights when multiple events map to the same root cause

---

## 7. Non-Functional Requirements

### 7.1 LLM Cost Control

Cost control failures can become service failures. The platform must implement the following.

- Daily spend limits per tenant
- Analysis suspension plus admin alerting when the limit is reached
- Response caching for identical or highly similar events
- Real-time token usage tracking by model, tenant, and time window
- Model tiering so low-cost models handle simple tasks and stronger models handle deeper analysis

### 7.2 Reliability

- Exactly-once processing through Kafka offset discipline and event-level idempotency
- Dead letter queue after three failed attempts
- Circuit breaker behavior when an LLM provider is degraded
- Graceful degradation so event ingestion and classification still operate during LLM outages

### 7.3 Observability

- Distributed tracing with a single trace ID across the full path from ingestion to delivery
- Metrics for throughput, latency, cost, error rate, and DLQ growth
- Structured JSON logging with trace IDs and masked sensitive fields
- Grafana dashboards that expose the health of the full pipeline

### 7.4 Multi-Tenancy

- Full tenant isolation for source configuration, prompts, routing, and cost limits
- No data leakage across tenants
- Shared Kafka topics with logical isolation through `tenantId` partitioning

---

## 8. Technology Stack

| Area | Technology | Why |
|------|------------|-----|
| Language | Java 17 or Kotlin | Mature JVM ecosystem and strong typing |
| Framework | Spring Boot 3.x | Fast integration across API, Kafka, data access, and observability |
| Message Broker | Apache Kafka | Event-driven architecture, partitioning, and delivery guarantees |
| Database | PostgreSQL | Durable storage for pipeline config, prompts, and results |
| Cache | Redis | Response caching, deduplication, and rate limiting |
| LLM | Claude API primary, OpenAI fallback | Provider diversity and better resilience |
| Tracing | OpenTelemetry + Jaeger | Standard distributed tracing |
| Metrics | Micrometer + Prometheus + Grafana | Operational metrics and dashboards |
| Containers | Docker + Docker Compose | Reproducible local development |
| CI/CD | GitHub Actions | Automated build and verification |
| Infrastructure | AWS ECS or EKS | Production deployment target |

---

## 9. API Endpoints

### 9.1 Webhook Intake

```text
POST /api/v1/webhooks/{sourceType}
```

- Receives webhooks from external systems such as Sentry or Slack
- Delegates to the corresponding source plugin
- Publishes normalized events to Kafka
- Returns `202 Accepted` because processing is asynchronous

### 9.2 Pipeline Configuration Management

```text
GET    /api/v1/tenants/{tenantId}/pipelines
POST   /api/v1/tenants/{tenantId}/pipelines
PUT    /api/v1/tenants/{tenantId}/pipelines/{id}
DELETE /api/v1/tenants/{tenantId}/pipelines/{id}
```

### 9.3 Analysis Result Queries

```text
GET /api/v1/tenants/{tenantId}/results
GET /api/v1/tenants/{tenantId}/results/{id}
GET /api/v1/tenants/{tenantId}/results/stream
```

- List results with filters such as time range, severity, and category
- Retrieve a single result in detail
- Stream real-time results over SSE

### 9.4 Prompt Management

```text
GET  /api/v1/prompts
POST /api/v1/prompts
GET  /api/v1/prompts/{id}/versions
POST /api/v1/prompts/{id}/activate
```

### 9.5 Cost and Health Metrics

```text
GET /api/v1/tenants/{tenantId}/metrics/cost
GET /api/v1/tenants/{tenantId}/metrics/throughput
GET /api/v1/tenants/{tenantId}/metrics/health
```

---

## 10. Kafka Topic Design

```text
sentinel.raw-events
  key: tenantId
  partitions: 12
  retention: 7 days

sentinel.classified-events
  key: tenantId
  partitions: 12
  retention: 3 days

sentinel.analysis-results
  key: tenantId
  partitions: 12
  retention: 30 days

sentinel.routed-results
  key: tenantId + channelType
  partitions: 12
  retention: 30 days

sentinel.dead-letter
  key: tenantId
  partitions: 3
  retention: 90 days
```

---

## 11. Incremental Delivery Roadmap

### Phase 1: Incident Analysis Assistant (MVP)

One source, one analysis path, one output channel.

- Source: Sentry webhook
- Pipeline: error event classification -> LLM root cause analysis
- Output: Slack message
- Infrastructure: local Docker Compose with Kafka, PostgreSQL, and Redis
- Goal: the entire pipeline works and can be traced end-to-end

### Phase 2: Plugin Architecture

Replace Phase 1 hardcoding with durable interfaces and extension points.

- Define `SourcePlugin` and `OutputPlugin`
- Refactor Phase 1 code behind those abstractions
- Add RSS source
- Add email output
- Add prompt version management
- Goal: new sources or outputs can be added without changing the core flow

### Phase 3: Dashboard and Customer Inquiry Flow

Add a human-facing interface and broader event coverage.

- Real-time dashboard with SSE and frontend UI
- Review analysis source
- Slack source for customer inquiry classification
- UI-based routing condition management
- Cost dashboard
- Goal: non-developers can observe pipeline state and review results

### Phase 4: Operational Maturity

Raise the project to a production-style operational standard.

- Load testing with k6
- Circuit breaker implementation
- Prompt A/B testing
- Finished Grafana dashboards
- CI/CD pipeline
- Failure scenario testing for Kafka, LLM timeouts, and Redis outages
- Goal: strong enough operational depth to support technical write-ups and demonstrations

---

## 12. Blog Series Plan

The project should be accompanied by technical articles following a "problem -> decision -> implementation -> result" structure.

1. Why Event-Driven Architecture Matters for LLM Systems
2. How the Classifier Stage Reduced LLM Cost by 5x
3. Designing the Plugin Architecture
4. Lessons Learned From Exactly-Once Processing in Kafka
5. Debugging an Asynchronous Pipeline With OpenTelemetry
6. Prompt Engineering as Data, Not Code

---

## 13. Success Criteria

- [ ] A Sentry incident produces a Slack root cause analysis within 60 seconds
- [ ] A new source plugin can be added without changing core code
- [ ] LLM cost never exceeds the configured daily limit
- [ ] Full distributed tracing works across the pipeline and is visible in Grafana
- [ ] The GitHub repository includes architecture diagrams, setup instructions, and a demo video
- [ ] At least three technical blog posts have been published
