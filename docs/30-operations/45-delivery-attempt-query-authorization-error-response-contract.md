# 45. Delivery-Attempt Query Authorization Error Response Contract

This document defines the stabilized authorization error response contract for the delivery-attempt query API.

## Endpoint

- `GET /api/v1/delivery-attempts`

## Error Type

Authorization failures return `401 Unauthorized` with a stable URI-style error type:

- `urn:sentinel:error:delivery-attempt-query-unauthorized`

## Contract Fields

All authorization failures include:

- `scope`: `delivery-attempt-query`
- `errorCode`: `DELIVERY_ATTEMPT_QUERY_UNAUTHORIZED`
- `type`: `urn:sentinel:error:delivery-attempt-query-unauthorized`
- `Cache-Control: no-store`

The response body detail remains specific to the failure mode:

- missing authorization header
- invalid authorization token

## Baseline Intent

This baseline gives operational clients a deterministic machine-readable contract for delivery-attempt query authorization failures while preserving detailed operator-facing failure messages.
