#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
API_BASE_URL="${SENTINEL_BASE_URL:-http://localhost:8080}"
TENANT_ID="${SENTINEL_TENANT_ID:-dev-sentinel}"
SOURCE_TYPE="${SENTINEL_SOURCE_TYPE:-sentry}"
PAYLOAD_PATH="${1:-$ROOT_DIR/samples/sentry/checkout-timeout.json}"

curl --fail --show-error --silent \
  -X POST "$API_BASE_URL/api/v1/webhooks/$SOURCE_TYPE" \
  -H "Content-Type: application/json" \
  -H "X-Sentinel-Tenant-Id: $TENANT_ID" \
  -H "sentry-hook-version: v1" \
  --data @"$PAYLOAD_PATH"

echo
