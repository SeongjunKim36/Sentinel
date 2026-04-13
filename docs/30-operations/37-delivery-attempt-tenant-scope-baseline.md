# 37. Delivery-Attempt Tenant Scope Baseline

This document defines tenant isolation rules for delivery-attempt query APIs.

## Endpoint

- `GET /api/v1/delivery-attempts`

## Tenant Scope Header

The endpoint requires tenant scope header:

- `X-Sentinel-Tenant-Id`

Rules:

- Missing or blank header returns `400 Bad Request`.
- Query is always executed under the scoped tenant from the header.
- If `tenantId` query is provided, it must match the header tenant scope.
- Mismatched `tenantId` query returns `400 Bad Request`.

## Interaction with Pagination

Tenant scope is applied before cursor pagination boundaries.

- Records from other tenants are excluded before page slicing.
- `hasMore` and `nextCursor` are computed only within the scoped tenant result set.

## Baseline Intent

This baseline aligns delivery-attempt query isolation with dead-letter API isolation patterns and reduces accidental cross-tenant query exposure during operations.
