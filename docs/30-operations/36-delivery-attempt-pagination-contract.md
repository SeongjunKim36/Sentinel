# 36. Delivery-Attempt Pagination Contract

This document defines the query and cursor pagination contract for delivery-attempt audit APIs.

## Endpoint

- `GET /api/v1/delivery-attempts`

Supported filters:

- `eventId`
- `tenantId`
- `channel`
- `success`
- `limit`
- `cursor`

## Response Contract

The API returns a page envelope:

```json
{
  "items": [],
  "page": {
    "limit": 50,
    "hasMore": false,
    "nextCursor": null
  }
}
```

Contract guarantees:

- `limit` is clamped to `1..200`.
- `hasMore=true` means additional records exist.
- `nextCursor` is only present when `hasMore=true`.
- Invalid cursor format returns `400 Bad Request`.

## Cursor Semantics

Pagination ordering is stable and deterministic:

- `order by attempted_at desc, id desc`
- cursor boundary:
  - `attempted_at < cursor.attempted_at`
  - or same timestamp and `id < cursor.id`

## Storage Index Hardening

`V7__delivery_attempt_pagination_indexes.sql` adds composite indexes for cursor-driven queries:

- `attempted_at + id`
- `tenant_id + attempted_at + id`
- `event_id + attempted_at + id`
- `channel + attempted_at + id`
- `success + attempted_at + id`
