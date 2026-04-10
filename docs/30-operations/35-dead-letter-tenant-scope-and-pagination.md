# 35. Dead-Letter Tenant Scope and Pagination Contract

This document defines dead-letter API isolation and pagination behavior hardening.

## Tenant Scope

All dead-letter APIs require tenant scope header:

- `X-Sentinel-Tenant-Id`

Applied endpoints:

- `GET /api/v1/dead-letters`
- `POST /api/v1/dead-letters/{id}/replay`
- `GET /api/v1/dead-letters/{id}/replay-audits`

Rules:

- Missing or blank tenant header returns `400 Bad Request`.
- Dead-letter records outside scoped tenant are treated as `404 Not Found`.
- If `tenantId` query is provided, it must match the scoped tenant header.

## Pagination Contract

Dead-letter list and replay-audit APIs use cursor pagination.

Request parameters:

- `limit` (clamped by `sentinel.dead-letter.api.max-query-limit`)
- `cursor` (opaque token from previous response page)

Response contract:

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

Cursor behavior:

- Invalid cursor format returns `400 Bad Request`.
- Cursors are stable on `(created_at desc, id desc)` ordering.
- Next page starts strictly after the last item from previous page.

## Storage Query Guarantees

JDBC implementations apply:

- deterministic sort: `order by created_at desc, id desc`
- cursor boundary:
  - `created_at < cursor.created_at`
  - or same timestamp and `id < cursor.id`

Index hardening (`V6__dead_letter_pagination_indexes.sql`) adds composite indexes for:

- dead-letter event by tenant + cursor-sort fields
- replay audit by dead-letter id + cursor-sort fields
