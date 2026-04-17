# 06. Source Plugin Extension Workflow

This document records the baseline workflow for adding new source plugins to Sentinel without changing the core pipeline flow.

## Plugin Capability Model

Sentinel now separates source plugins by intake capability.

- `SourcePlugin`: shared identity boundary
- `WebhookSourcePlugin`: normalizes externally pushed payloads
- `PollingSourcePlugin`: fetches and normalizes externally pulled payloads

This split allows the project to add sources with different intake modes while reusing the same event model and downstream stages.

## Core Flow That Must Not Change

New sources should continue to feed the existing pipeline:

1. Normalize external input into shared `Event`
2. Publish to `sentinel.raw-events`
3. Reuse classification, analysis, evaluation, and delivery

If a new source requires an entirely separate downstream flow, it should justify that divergence explicitly.

## Extension Steps

### 1. Pick the intake mode

- Use `WebhookSourcePlugin` for pushed events such as Sentry webhooks
- Use `PollingSourcePlugin` for pulled sources such as RSS feeds

### 2. Implement the plugin

- Define a stable `type`
- Normalize external data into the shared `Event`
- Preserve tenant scope and trace propagation
- Produce a deterministic `sourceId`

### 3. Attach the intake adapter

- Webhook sources are invoked through `WebhookIntakeService`
- Polling sources are invoked through `SourcePollingService`

### 4. Reuse the existing pipeline

- Publish normalized events through `RawEventPublisher`
- Extend classification only where category mapping or analyzability rules require it
- Avoid introducing source-specific delivery plumbing unless there is a strong product reason

### 5. Document the contract

- Add a public document for the new source baseline
- Update the phase checklist and execution log
- Add repository-level usage guidance where needed

### 6. Prove it with tests

- Plugin normalization tests
- Intake adapter or controller tests
- Classification or downstream behavior tests when source semantics require it

## Baseline Proof

The `rss` source plugin is the first polling-based proof of this workflow.

It demonstrates that Sentinel can add a new source type through capability-specific plugin boundaries while preserving the same shared event contract and downstream pipeline.
