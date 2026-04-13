# 40. Delivery-Attempt Query Distributed Limiter Baseline

This document defines the Redis-based distributed limiter baseline for delivery-attempt query APIs.

## Endpoint

- `GET /api/v1/delivery-attempts`

## Distributed Limiter Behavior

The distributed limiter extends the existing rate-limit baseline.

When enabled:

- Redis fixed-window counters are used by tenant scope.
- Limit checks are shared across multiple Sentinel instances.
- Requests above the configured threshold return `429 Too Many Requests`.

When disabled:

- Sentinel uses the local in-memory limiter baseline.

## Failure Behavior

If Redis is temporarily unavailable:

- `fail-open = true`: request is allowed and metric outcome is recorded as `degraded_allow`.
- `fail-open = false`: request is rejected with `503 Service Unavailable`.

## Configuration

Properties:

- `sentinel.delivery.api.query-rate-limit.distributed.enabled`
- `sentinel.delivery.api.query-rate-limit.distributed.key-prefix`
- `sentinel.delivery.api.query-rate-limit.distributed.fail-open`

Environment variables:

- `SENTINEL_DELIVERY_QUERY_RATE_LIMIT_DISTRIBUTED_ENABLED`
- `SENTINEL_DELIVERY_QUERY_RATE_LIMIT_DISTRIBUTED_KEY_PREFIX`
- `SENTINEL_DELIVERY_QUERY_RATE_LIMIT_DISTRIBUTED_FAIL_OPEN`

## Observability

Distributed limiter outcomes continue to use:

- `sentinel.delivery.api.query.requests` (`tenant_id`, `outcome`)

Additional outcome value introduced by this baseline:

- `degraded_allow`

## Baseline Intent

This baseline removes single-instance limit drift and introduces explicit degraded-mode behavior for multi-instance operational query surfaces.
