# 22. Routing Policy and Channel Mapping

Sentinel externalizes routing decisions so teams can tune delivery behavior without changing Kotlin code.

## Routing Inputs

Each `AnalysisResult` enters evaluation with:

- `severity`
- `category`
- optional pre-filled `routing.channels`
- optional pre-filled `routing.priority`

The evaluation module then applies policy-based overrides.

## Configuration Surface

Routing policy is configured under `sentinel.evaluation`:

- `minimum-confidence`
- `routing.default-channels`
- `routing.severity-policies`
- `routing.category-policies`

Example (`application.yml`):

```yaml
sentinel:
  evaluation:
    minimum-confidence: 0.5
    routing:
      default-channels: [slack]
      severity-policies:
        CRITICAL: { priority: IMMEDIATE }
        HIGH: { priority: IMMEDIATE }
        MEDIUM: { priority: BATCHED }
        LOW: { priority: DIGEST }
        INFO: { priority: DIGEST }
      category-policies:
        analysis-failure:
          priority: IMMEDIATE
          prefer-result-channels: true
          skip-minimum-confidence: true
```

## Resolution Order

For every result:

1. Category policy (if present) has highest priority.
2. Severity policy applies when category policy does not override that field.
3. Fallback values use existing result routing and configured defaults.

Channel resolution order:

1. Category policy channels
2. Severity policy channels
3. `sentinel.evaluation.routing.default-channels`
4. `sentinel.delivery.default-channels` (legacy fallback)
5. Existing `result.routing.channels`

If `prefer-result-channels=true` in a category policy and result channels are non-empty, those channels are kept first.

## Current Default Semantics

- `analysis-failure` keeps explicit failure channels and always routes as `IMMEDIATE`.
- Non-failure categories are mapped by severity to priority.
- Confidence is clamped to at least `minimum-confidence` unless the category policy sets `skip-minimum-confidence=true`.
