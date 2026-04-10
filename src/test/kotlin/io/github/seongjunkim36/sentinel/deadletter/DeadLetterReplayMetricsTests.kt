package io.github.seongjunkim36.sentinel.deadletter

import io.github.seongjunkim36.sentinel.shared.DeadLetterPayloadType
import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.time.Instant
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DeadLetterReplayMetricsTests {
    private lateinit var meterRegistry: SimpleMeterRegistry

    @BeforeEach
    fun setUp() {
        meterRegistry = SimpleMeterRegistry()
        Metrics.addRegistry(meterRegistry)
        DeadLetterReplayMetrics.resetStateForTests()
    }

    @AfterEach
    fun tearDown() {
        DeadLetterReplayMetrics.resetStateForTests()
        Metrics.removeRegistry(meterRegistry)
        meterRegistry.close()
    }

    @Test
    fun `records replay outcome counters by tenant channel and outcome`() {
        val record = sampleRecord(tenantId = "tenant-alpha", channel = "telegram")

        DeadLetterReplayMetrics.recordReplayOutcome(record, DeadLetterReplayOutcome.REPLAY_FAILED)
        DeadLetterReplayMetrics.recordReplayOutcome(record, DeadLetterReplayOutcome.REPLAY_BLOCKED)

        val failedCount =
            meterRegistry
                .find("sentinel.deadletter.replay.events")
                .tags(
                    "tenant_id",
                    "tenant-alpha",
                    "channel",
                    "telegram",
                    "outcome",
                    "replay_failed",
                ).counter()
                ?.count()
        val blockedCount =
            meterRegistry
                .find("sentinel.deadletter.replay.events")
                .tags(
                    "tenant_id",
                    "tenant-alpha",
                    "channel",
                    "telegram",
                    "outcome",
                    "replay_blocked",
                ).counter()
                ?.count()

        assertThat(failedCount).isEqualTo(1.0)
        assertThat(blockedCount).isEqualTo(1.0)
    }

    @Test
    fun `records mttr when replay succeeds after failures`() {
        val failedAt = Instant.parse("2026-04-10T00:00:00Z")
        val recoveredAt = Instant.parse("2026-04-10T00:03:30Z")
        val record = sampleRecord(tenantId = "tenant-beta", channel = "slack")

        DeadLetterReplayMetrics.recordReplayOutcome(
            record = record,
            outcome = DeadLetterReplayOutcome.REPLAY_FAILED,
            occurredAt = failedAt,
        )
        DeadLetterReplayMetrics.recordReplayOutcome(
            record = record,
            outcome = DeadLetterReplayOutcome.REPLAYED,
            occurredAt = recoveredAt,
        )

        val mttrSummary =
            meterRegistry
                .find("sentinel.deadletter.replay.mttr.seconds")
                .tags("tenant_id", "tenant-beta", "channel", "slack")
                .summary()

        assertThat(mttrSummary).isNotNull
        assertThat(mttrSummary!!.count()).isEqualTo(1L)
        assertThat(mttrSummary.totalAmount()).isEqualTo(210.0)
    }

    private fun sampleRecord(
        tenantId: String,
        channel: String?,
    ): DeadLetterRecord =
        DeadLetterRecord(
            id = UUID.randomUUID(),
            sourceStage = "delivery",
            sourceTopic = "sentinel.routed-results",
            tenantId = tenantId,
            eventId = UUID.randomUUID(),
            channel = channel,
            reason = "test",
            payloadType = DeadLetterPayloadType.ANALYSIS_RESULT,
            payload = "{}",
            status = DeadLetterStatus.OPEN,
            replayCount = 0,
            createdAt = Instant.now(),
            lastReplayAt = null,
            lastReplayError = null,
            lastReplayOperatorNote = null,
        )
}
