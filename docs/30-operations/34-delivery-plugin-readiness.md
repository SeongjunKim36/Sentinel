# 34. Delivery Plugin Readiness

This document defines the operational readiness checks for outbound delivery plugins.

## Endpoints

Delivery readiness can be checked through:

- `GET /api/v1/delivery/health`
- `GET /actuator/health/readiness`

The readiness group includes `deliveryPlugins`.

## Readiness Criteria

Sentinel evaluates readiness based on required default channels:

- Required channels are `sentinel.delivery.default-channels`.
- A required channel is ready only when:
  - an output plugin is registered for that channel
  - required channel configuration is present

Built-in configuration checks:

- `slack`: `bot-token` and `default-channel` must be non-empty
- `telegram`: `bot-token` and `chat-id` must be non-empty

## Behavior

- If all required channels are ready, status is `UP`.
- If any required channel is not ready, status is `DOWN`.
- If no required default channels are configured, readiness returns `UP`.

The response includes per-channel check details (`required`, `registered`, `configured`, `ready`, `reason`).
