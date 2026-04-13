# 39. Delivery-Attempt Query Rate-Limit Baseline

This document defines baseline rate limiting for the delivery-attempt query API.

## Endpoint

- `GET /api/v1/delivery-attempts`

## Rate-Limit Behavior

Delivery-attempt query rate limiting is optional and controlled by configuration.

When enabled:

- Rate limiting is applied per scoped tenant (`X-Sentinel-Tenant-Id`).
- A fixed window counter is used.
- Requests over the configured threshold return `429 Too Many Requests`.

When disabled:

- No rate-limit guardrail is applied.

## Configuration

Properties:

- `sentinel.delivery.api.query-rate-limit.enabled`
- `sentinel.delivery.api.query-rate-limit.max-requests`
- `sentinel.delivery.api.query-rate-limit.window`

Environment variables:

- `SENTINEL_DELIVERY_QUERY_RATE_LIMIT_ENABLED`
- `SENTINEL_DELIVERY_QUERY_RATE_LIMIT_MAX_REQUESTS`
- `SENTINEL_DELIVERY_QUERY_RATE_LIMIT_WINDOW`

## Observability

Rate-limit outcomes are tracked with:

- `sentinel.delivery.api.query.requests` (`tenant_id`, `outcome`)

Outcome values:

- `allowed`
- `rate_limited`

## Baseline Intent

This baseline prevents operational query surfaces from being overused and creates measurable API guardrail signals before moving to distributed limiter strategies.
