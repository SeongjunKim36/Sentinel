# 44. Dead-Letter List and Replay-Audits Error Response Contract Baseline

This document defines the baseline error response contract for dead-letter list and replay-audits read APIs.

## Endpoints

- `GET /api/v1/dead-letters`
- `GET /api/v1/dead-letters/{id}/replay-audits`

## Error Types

Read API errors are returned as `application/problem+json` with stable fields and URI-style error types.

- `400 Bad Request` for tenant-scope and cursor validation failures
- `404 Not Found` for replay-audits requests that target a missing or out-of-scope dead-letter record

## Contract Fields

All error responses include:

- `scope`: endpoint-specific machine-readable scope
- `errorCode`: stable machine-readable error code
- `type`: stable URI-style error type
- `Cache-Control: no-store`

Replay-audits `404` additionally includes:

- `deadLetterId`

## Scopes

- `dead-letter-list`
- `dead-letter-replay-audits`

## Error Code Registry

- `DEAD_LETTER_LIST_TENANT_SCOPE_REQUIRED` -> `400 Bad Request`
- `DEAD_LETTER_LIST_TENANT_SCOPE_MISMATCH` -> `400 Bad Request`
- `DEAD_LETTER_LIST_CURSOR_INVALID` -> `400 Bad Request`
- `DEAD_LETTER_LIST_BAD_REQUEST` -> `400 Bad Request` fallback
- `DEAD_LETTER_REPLAY_AUDITS_TENANT_SCOPE_REQUIRED` -> `400 Bad Request`
- `DEAD_LETTER_REPLAY_AUDITS_CURSOR_INVALID` -> `400 Bad Request`
- `DEAD_LETTER_REPLAY_AUDITS_BAD_REQUEST` -> `400 Bad Request` fallback
- `DEAD_LETTER_REPLAY_AUDITS_NOT_FOUND` -> `404 Not Found`

## Baseline Intent

This baseline makes dead-letter read APIs deterministic for operational clients by separating list and replay-audits failure scopes, exposing machine-readable validation failures, and removing empty-body not-found responses for replay-audits lookups.
