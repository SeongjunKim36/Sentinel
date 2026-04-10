package io.github.seongjunkim36.sentinel.deadletter

import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.Tag
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

object DeadLetterReplayMetrics {
    private const val REPLAY_EVENTS = "sentinel.deadletter.replay.events"
    private const val REPLAY_MTTR_SECONDS = "sentinel.deadletter.replay.mttr.seconds"
    private val failureStartedAtByTarget = ConcurrentHashMap<String, Instant>()

    fun recordReplayOutcome(
        record: DeadLetterRecord,
        outcome: DeadLetterReplayOutcome,
        occurredAt: Instant = Instant.now(),
    ) {
        val channel = record.channel ?: "unknown"
        val tags =
            listOf(
                Tag.of("tenant_id", record.tenantId),
                Tag.of("channel", channel),
                Tag.of("outcome", outcome.name.lowercase()),
            )

        Metrics.counter(REPLAY_EVENTS, tags).increment()

        val targetKey = targetKey(record.tenantId, channel)
        when (outcome) {
            DeadLetterReplayOutcome.REPLAY_FAILED -> failureStartedAtByTarget.putIfAbsent(targetKey, occurredAt)
            DeadLetterReplayOutcome.REPLAYED -> {
                val firstFailureAt = failureStartedAtByTarget.remove(targetKey) ?: return
                if (occurredAt.isBefore(firstFailureAt)) {
                    return
                }

                val mttrSeconds = Duration.between(firstFailureAt, occurredAt).toMillis().toDouble() / 1_000.0
                Metrics
                    .summary(
                        REPLAY_MTTR_SECONDS,
                        listOf(
                            Tag.of("tenant_id", record.tenantId),
                            Tag.of("channel", channel),
                        ),
                    ).record(mttrSeconds)
            }

            DeadLetterReplayOutcome.REPLAY_BLOCKED -> {
            }
        }
    }

    internal fun resetStateForTests() {
        failureStartedAtByTarget.clear()
    }

    private fun targetKey(
        tenantId: String,
        channel: String,
    ): String = "$tenantId|$channel"
}
