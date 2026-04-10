# 32. Webhook Signature Security Baseline

This document defines the baseline verification policy for inbound source webhooks.

## Current Scope

The baseline currently applies to Sentry webhooks received at:

- `POST /api/v1/webhooks/sentry`

## Verification Rules

When enabled, Sentinel validates:

- `Sentry-Hook-Signature`
- `Sentry-Hook-Timestamp`

Validation behavior:

- Signature uses HMAC-SHA256 with the configured Sentry webhook secret.
- Signature comparison is constant-time.
- Timestamp skew must be within configured tolerance.

If validation fails, Sentinel returns `401 Unauthorized` and does not publish to Kafka.

## Configuration

Properties (`application.yml`):

- `sentinel.ingestion.webhook.sentry.signature-validation-enabled`
- `sentinel.ingestion.webhook.sentry.secret`
- `sentinel.ingestion.webhook.sentry.max-timestamp-skew`

Environment variables:

- `SENTINEL_SENTRY_WEBHOOK_SIGNATURE_VALIDATION_ENABLED`
- `SENTINEL_SENTRY_WEBHOOK_SECRET`
- `SENTINEL_SENTRY_WEBHOOK_MAX_TIMESTAMP_SKEW`

Default posture:

- Validation is disabled by default to preserve local bootstrap compatibility.
- Production-like deployments should enable validation and configure a non-empty secret.

## Reference

Sentry webhook signature and header structure:

- https://docs.sentry.io/product/integrations/integration-platform/webhooks/
