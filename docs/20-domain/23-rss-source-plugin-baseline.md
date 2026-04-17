# 23. RSS Source Plugin Baseline

This document defines the baseline RSS source plugin and polling flow for Sentinel Phase 2.

## Goal

The RSS source plugin proves that a new source can be added without changing the downstream pipeline shape.

The plugin publishes normalized raw events into the same Kafka-backed flow already used by webhook sources.

## Polling Endpoint

- `POST /api/v1/sources/rss/poll`

Required header:

- `X-Sentinel-Tenant-Id`

Request body:

```json
{
  "feedUrl": "https://feeds.example.com/releases.xml",
  "maxItems": 10
}
```

## Request Rules

- `feedUrl` is required
- `feedUrl` must be an absolute `http` or `https` URL
- `maxItems` is optional
- `maxItems` must be between `1` and `50`

## Normalized Event Shape

Each RSS or Atom entry becomes a normalized `Event` with:

- `sourceType`: `rss`
- `tenantId`: taken from `X-Sentinel-Tenant-Id`
- `sourceId`: derived from `guid`, then `link`, then `title`, then a stable hash fallback
- `metadata.traceId`: propagated from the poll request trace

Normalized payload fields include:

- `message`
- `title`
- `summary`
- `link`
- `guid`
- `publishedAt`
- `feedTitle`
- `feedUrl`
- `feedFormat`

## Pipeline Behavior

RSS entries are classified as:

- `category=feed-update`

The baseline treats `feed-update` as analyzable so RSS items can flow through:

- classification
- analysis
- evaluation
- delivery

This keeps the source plugin extension focused on intake and normalization rather than introducing a separate downstream path.

## Response Contract

Successful polling returns `202 Accepted` with:

- `accepted`
- `sourceType`
- `tenantId`
- `publishedCount`
- `sourceIds`
- `traceId`

## Baseline Intent

This baseline demonstrates that Sentinel can extend source coverage through a polling plugin while keeping the existing event model, Kafka flow, observability model, and delivery path intact.
