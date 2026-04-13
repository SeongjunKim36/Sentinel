# 42. Delivery-Attempt Query Rate-Limit Response Contract

This document defines the stabilized error response contract for delivery-attempt query rate-limit enforcement.

## Endpoint

- `GET /api/v1/delivery-attempts`

## Error Types

Rate-limit guardrails use two explicit error types:

- `429 Too Many Requests` (`urn:sentinel:error:delivery-attempt-query-rate-limited`)
- `503 Service Unavailable` (`urn:sentinel:error:delivery-attempt-query-rate-limit-unavailable`)

## Contract Fields

All delivery-attempt query rate-limit errors include:

- `scope`: `delivery-attempt-query`
- `errorCode`: stable machine-readable error code
- `type`: stable URI-style error type

`429` additionally includes:

- `retryAfterSeconds`: integer retry hint in body
- `Retry-After` header in seconds

Both `429` and `503` include:

- `Cache-Control: no-store`

## Error Code Registry

- `DELIVERY_ATTEMPT_QUERY_RATE_LIMITED` -> `429 Too Many Requests`
- `DELIVERY_ATTEMPT_QUERY_RATE_LIMIT_UNAVAILABLE` -> `503 Service Unavailable`

## Baseline Intent

This baseline provides a deterministic, machine-readable error contract so clients can implement retry/backoff behavior and operational tooling can classify rate-limit failures consistently across limiter modes.
