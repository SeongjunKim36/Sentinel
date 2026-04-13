# 43. Replay API Error Response Contract Baseline

This document defines the baseline error response contract for dead-letter replay execution APIs.

## Endpoint

- `POST /api/v1/dead-letters/{id}/replay`

## Error Types

Replay API errors are returned as `application/problem+json` with stable fields and URI-style error types.

- `401 Unauthorized` (`urn:sentinel:error:dead-letter-replay-unauthorized`)
- `404 Not Found` (`urn:sentinel:error:dead-letter-replay-not-found`)
- `409 Conflict` (`urn:sentinel:error:dead-letter-replay-blocked`)
- `400 Bad Request` (validation/input hardening errors)

## Contract Fields

All replay API error responses include:

- `scope`: `dead-letter-replay`
- `errorCode`: stable machine-readable error code
- `type`: stable URI-style error type
- `Cache-Control: no-store`

Additional fields:

- `404`: `deadLetterId`
- `409`: `deadLetterId`, `replayStatus`, `replayOutcome`

## Error Code Registry

- `DEAD_LETTER_REPLAY_UNAUTHORIZED` -> `401 Unauthorized`
- `DEAD_LETTER_REPLAY_NOT_FOUND` -> `404 Not Found`
- `DEAD_LETTER_REPLAY_BLOCKED` -> `409 Conflict`
- `DEAD_LETTER_REPLAY_OPERATOR_NOTE_TOO_LONG` -> `400 Bad Request`
- `DEAD_LETTER_API_CURSOR_INVALID` -> `400 Bad Request`
- `DEAD_LETTER_API_TENANT_SCOPE_REQUIRED` -> `400 Bad Request`
- `DEAD_LETTER_API_TENANT_SCOPE_MISMATCH` -> `400 Bad Request`
- `DEAD_LETTER_API_BAD_REQUEST` -> `400 Bad Request` fallback

## Baseline Intent

This baseline gives replay clients deterministic error semantics for authorization, not-found scope checks, and guardrail blocks, while keeping validation failures machine-readable for operational tooling and automation.
