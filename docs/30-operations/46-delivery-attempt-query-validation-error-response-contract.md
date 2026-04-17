# 46. Delivery-Attempt Query Validation Error Response Contract

This document defines the stabilized validation error response contract for the delivery-attempt query API.

## Endpoint

- `GET /api/v1/delivery-attempts`

## Error Types

Validation failures are returned as `400 Bad Request` with stable URI-style error types.

- tenant scope required
- tenant scope mismatch
- cursor invalid
- limit out of range
- parameter invalid
- generic bad request fallback

## Contract Fields

All validation errors include:

- `scope`: `delivery-attempt-query`
- `errorCode`: stable machine-readable validation code
- `type`: stable URI-style error type
- `Cache-Control: no-store`

Parameter-specific failures may additionally include:

- `parameter`

## Error Code Registry

- `DELIVERY_ATTEMPT_QUERY_TENANT_SCOPE_REQUIRED` -> `400 Bad Request`
- `DELIVERY_ATTEMPT_QUERY_TENANT_SCOPE_MISMATCH` -> `400 Bad Request`
- `DELIVERY_ATTEMPT_QUERY_CURSOR_INVALID` -> `400 Bad Request`
- `DELIVERY_ATTEMPT_QUERY_LIMIT_OUT_OF_RANGE` -> `400 Bad Request`
- `DELIVERY_ATTEMPT_QUERY_PARAMETER_INVALID` -> `400 Bad Request`
- `DELIVERY_ATTEMPT_QUERY_BAD_REQUEST` -> `400 Bad Request` fallback

## Stable Error Types

- `urn:sentinel:error:delivery-attempt-query-tenant-scope-required`
- `urn:sentinel:error:delivery-attempt-query-tenant-scope-mismatch`
- `urn:sentinel:error:delivery-attempt-query-cursor-invalid`
- `urn:sentinel:error:delivery-attempt-query-limit-out-of-range`
- `urn:sentinel:error:delivery-attempt-query-parameter-invalid`
- `urn:sentinel:error:delivery-attempt-query-bad-request`

## Baseline Intent

This baseline makes delivery-attempt query validation failures deterministic for operators and API clients by replacing ambiguous default `400` responses with explicit machine-readable contracts.
