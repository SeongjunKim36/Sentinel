# 38. Delivery-Attempt Query Authorization Baseline

This document defines token-based baseline authorization for delivery-attempt query APIs.

## Endpoint

- `GET /api/v1/delivery-attempts`

## Authorization Behavior

Delivery-attempt query authorization is optional and controlled by configuration.

When enabled:

- Request must include the configured authorization header.
- Missing or blank header returns `401 Unauthorized`.
- Invalid token returns `401 Unauthorized`.

When disabled:

- No additional query token is required.

## Configuration

Properties:

- `sentinel.delivery.api.query-authorization.enabled`
- `sentinel.delivery.api.query-authorization.header-name`
- `sentinel.delivery.api.query-authorization.token`

Environment variables:

- `SENTINEL_DELIVERY_QUERY_AUTH_ENABLED`
- `SENTINEL_DELIVERY_QUERY_AUTH_HEADER_NAME`
- `SENTINEL_DELIVERY_QUERY_AUTH_TOKEN`

## Baseline Intent

This baseline adds a lightweight control for operational query surfaces and aligns with the replay API authorization hardening pattern.
