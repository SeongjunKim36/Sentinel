# 31. Replay Recovery SLO and Alerting

This document defines the Prometheus-based SLO thresholds and alerting signals for dead-letter replay recovery in local and future production-like environments.

## Scope

The rules focus on replay reliability for failed delivery recovery:

- replay recovery ratio
- replay MTTR (mean time to recovery)
- replay blocked events caused by replay guardrails

## Rule Sources

- Prometheus scrape and rule loader: `docker/prometheus/prometheus.yml`
- Replay SLO rule group: `docker/prometheus/rules/sentinel-replay-slo-alerts.yml`

## Recording Rules

The following recording rules are generated every 30 seconds:

- `sentinel:deadletter:replay:attempt_rate5m`
- `sentinel:deadletter:replay:recovery_ratio5m`
- `sentinel:deadletter:replay:mttr_avg_seconds15m`

These expressions are based on:

- `sentinel_deadletter_replay_events_total`
- `sentinel_deadletter_replay_mttr_seconds_sum`
- `sentinel_deadletter_replay_mttr_seconds_count`

## SLO Thresholds

Current baseline SLO targets:

- Replay recovery ratio target: `>= 95%` (warning below threshold), `>= 90%` as critical lower bound
- Replay MTTR target: `<= 300s` (warning above threshold), `<= 900s` as critical upper bound
- Replay blocked outcome target: `0` blocked outcomes in a healthy run window

These thresholds are intentionally strict enough for MVP reliability checks while still realistic for local testing noise.

## Alert Rules

Configured alerts:

- `SentinelReplayRecoverySloWarning`
- `SentinelReplayRecoverySloCritical`
- `SentinelReplayMttrSloWarning`
- `SentinelReplayMttrSloCritical`
- `SentinelReplayBlockedDetected`

Rule labels include:

- `severity` (`warning` or `critical`)
- `service=sentinel`
- `slo` (`replay-recovery`, `replay-mttr`, `replay-guardrail`)

## Local Verification

1. Start dependencies with Docker Compose.
2. Run Sentinel application with Actuator Prometheus endpoint enabled.
3. Open Prometheus Alerts UI at `http://localhost:9090/alerts`.
4. Trigger replay failures and replays through the dead-letter replay API.

Useful pages:

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`
- Sentinel dashboard: `Sentinel Operations Overview`

