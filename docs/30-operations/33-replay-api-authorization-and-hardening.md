# 33. Replay API Authorization and Hardening

This document defines the baseline hardening controls for dead-letter replay APIs.

## Scope

Protected and hardened endpoints:

- `GET /api/v1/dead-letters`
- `GET /api/v1/dead-letters/{id}/replay-audits`
- `POST /api/v1/dead-letters/{id}/replay`

## Authorization

Replay execution authorization is optional and disabled by default for local bootstrap compatibility.

When enabled:

- `POST /api/v1/dead-letters/{id}/replay` requires a configured token header.
- Missing or invalid token returns `401 Unauthorized`.
- Token check uses constant-time comparison.

Configuration:

- `sentinel.dead-letter.api.replay-authorization.enabled`
- `sentinel.dead-letter.api.replay-authorization.header-name`
- `sentinel.dead-letter.api.replay-authorization.token`

Environment variables:

- `SENTINEL_DEAD_LETTER_REPLAY_AUTH_ENABLED`
- `SENTINEL_DEAD_LETTER_REPLAY_AUTH_HEADER_NAME`
- `SENTINEL_DEAD_LETTER_REPLAY_AUTH_TOKEN`

## Input Hardening

The API enforces:

- Query limit clamping with `sentinel.dead-letter.api.max-query-limit`
- Operator note max length with `sentinel.dead-letter.api.max-operator-note-length`
- Trim-and-normalize behavior for tenant/channel filters

Invalid operator note length returns `400 Bad Request`.

## Recommended Production Baseline

1. Enable replay authorization.
2. Use a strong random token sourced from secret manager or environment.
3. Keep max query limit bounded (for example 100-200).
4. Keep operator note limit bounded (for example 300-1000).
