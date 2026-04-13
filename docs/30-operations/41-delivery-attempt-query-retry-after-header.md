# 41. Delivery-Attempt Query Retry-After Header Baseline

This document defines the baseline `Retry-After` response contract for delivery-attempt query rate limiting.

## Endpoint

- `GET /api/v1/delivery-attempts`

## Header Contract

When a request is rejected by the delivery-attempt query rate limiter:

- Response status is `429 Too Many Requests`.
- Response includes `Retry-After` header in seconds.
- Response body includes `retryAfterSeconds` for client-side handling.

Example:

- `HTTP 429`
- `Retry-After: 42`

## Scope

This baseline applies to both limiter modes:

- local in-memory limiter
- Redis distributed limiter

## Baseline Intent

This baseline gives clients a deterministic retry signal and aligns rate-limit behavior with standard HTTP backoff semantics.
