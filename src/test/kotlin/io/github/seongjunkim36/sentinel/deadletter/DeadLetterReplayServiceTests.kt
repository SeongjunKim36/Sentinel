package io.github.seongjunkim36.sentinel.deadletter

import io.github.seongjunkim36.sentinel.shared.AnalysisDetail
import io.github.seongjunkim36.sentinel.shared.AnalysisResult
import io.github.seongjunkim36.sentinel.shared.DeadLetterPayloadType
import io.github.seongjunkim36.sentinel.shared.LlmMetadata
import io.github.seongjunkim36.sentinel.shared.RoutingDecision
import io.github.seongjunkim36.sentinel.shared.Severity
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class DeadLetterReplayServiceTests {
    @Test
    fun `replays analysis result dead-letter and marks replayed`() {
        val recordStore = InMemoryDeadLetterStore()
        val publisher = RecordingRoutedResultPublisher()
        val replayService =
            replayService(
                recordStore = recordStore,
                publisher = publisher,
            )
        val jsonMapper = JsonMapper.builder().findAndAddModules().build()

        val analysisResult =
            AnalysisResult(
                eventId = UUID.randomUUID(),
                tenantId = "tenant-alpha",
                category = "error",
                severity = Severity.HIGH,
                confidence = 0.8,
                summary = "Checkout timeout",
                detail = AnalysisDetail(analysis = "Checkout degraded"),
                llmMetadata = LlmMetadata(model = "stub", promptVersion = "v1"),
                routing = RoutingDecision(channels = listOf("telegram")),
            )
        val deadLetterId = UUID.randomUUID()
        recordStore.records[deadLetterId] =
            DeadLetterRecord(
                id = deadLetterId,
                sourceStage = "delivery",
                sourceTopic = "sentinel.routed-results",
                tenantId = "tenant-alpha",
                eventId = analysisResult.eventId,
                channel = "telegram",
                reason = "Telegram delivery timeout",
                payloadType = DeadLetterPayloadType.ANALYSIS_RESULT,
                payload = jsonMapper.writeValueAsString(analysisResult),
                status = DeadLetterStatus.OPEN,
                replayCount = 0,
                createdAt = Instant.now(),
                lastReplayAt = null,
                lastReplayError = null,
                lastReplayOperatorNote = null,
            )

        val replayResult = replayService.replay(deadLetterId, operatorNote = "retry after telegram recovery")

        assertThat(replayResult).isNotNull
        assertThat(replayResult!!.replayed).isTrue()
        assertThat(replayResult.status).isEqualTo(DeadLetterStatus.REPLAYED)
        assertThat(replayResult.outcome).isEqualTo(DeadLetterReplayOutcome.REPLAYED)
        assertThat(publisher.publishedResults).hasSize(1)
        assertThat(publisher.publishedResults.single().eventId).isEqualTo(analysisResult.eventId)
        assertThat(recordStore.records[deadLetterId]!!.status).isEqualTo(DeadLetterStatus.REPLAYED)
        assertThat(recordStore.records[deadLetterId]!!.replayCount).isEqualTo(1)
        assertThat(recordStore.records[deadLetterId]!!.lastReplayOperatorNote)
            .isEqualTo("retry after telegram recovery")
    }

    @Test
    fun `marks replay failed when payload cannot be parsed`() {
        val recordStore = InMemoryDeadLetterStore()
        val publisher = RecordingRoutedResultPublisher()
        val replayService =
            replayService(
                recordStore = recordStore,
                publisher = publisher,
            )

        val deadLetterId = UUID.randomUUID()
        recordStore.records[deadLetterId] =
            DeadLetterRecord(
                id = deadLetterId,
                sourceStage = "delivery",
                sourceTopic = "sentinel.routed-results",
                tenantId = "tenant-alpha",
                eventId = UUID.randomUUID(),
                channel = "telegram",
                reason = "invalid payload",
                payloadType = DeadLetterPayloadType.ANALYSIS_RESULT,
                payload = "{not-valid-json}",
                status = DeadLetterStatus.OPEN,
                replayCount = 0,
                createdAt = Instant.now(),
                lastReplayAt = null,
                lastReplayError = null,
                lastReplayOperatorNote = null,
            )

        val replayResult = replayService.replay(deadLetterId, operatorNote = "retry to verify payload")

        assertThat(replayResult).isNotNull
        assertThat(replayResult!!.replayed).isFalse()
        assertThat(replayResult.status).isEqualTo(DeadLetterStatus.REPLAY_FAILED)
        assertThat(replayResult.outcome).isEqualTo(DeadLetterReplayOutcome.REPLAY_FAILED)
        assertThat(recordStore.records[deadLetterId]!!.status).isEqualTo(DeadLetterStatus.REPLAY_FAILED)
        assertThat(recordStore.records[deadLetterId]!!.replayCount).isEqualTo(1)
        assertThat(recordStore.records[deadLetterId]!!.lastReplayOperatorNote).isEqualTo("retry to verify payload")
    }

    @Test
    fun `blocks replay when operator note is missing`() {
        val recordStore = InMemoryDeadLetterStore()
        val publisher = RecordingRoutedResultPublisher()
        val replayService = replayService(recordStore, publisher)
        val deadLetterId = UUID.randomUUID()
        recordStore.records[deadLetterId] =
            recordStore.analysisResultRecord(deadLetterId = deadLetterId)

        val replayResult = replayService.replay(deadLetterId)

        assertThat(replayResult).isNotNull
        assertThat(replayResult!!.replayed).isFalse()
        assertThat(replayResult.outcome).isEqualTo(DeadLetterReplayOutcome.REPLAY_BLOCKED)
        assertThat(replayResult.message).contains("operator note")
        assertThat(recordStore.records[deadLetterId]!!.replayCount).isZero()
        assertThat(publisher.publishedResults).isEmpty()
    }

    @Test
    fun `blocks replay when max attempts is reached`() {
        val recordStore = InMemoryDeadLetterStore()
        val publisher = RecordingRoutedResultPublisher()
        val replayService = replayService(recordStore, publisher, maxReplayAttempts = 1)
        val deadLetterId = UUID.randomUUID()
        recordStore.records[deadLetterId] =
            recordStore.analysisResultRecord(
                deadLetterId = deadLetterId,
                replayCount = 1,
            )

        val replayResult = replayService.replay(deadLetterId, operatorNote = "second retry")

        assertThat(replayResult).isNotNull
        assertThat(replayResult!!.replayed).isFalse()
        assertThat(replayResult.outcome).isEqualTo(DeadLetterReplayOutcome.REPLAY_BLOCKED)
        assertThat(replayResult.message).contains("max replay attempts")
        assertThat(recordStore.records[deadLetterId]!!.replayCount).isEqualTo(1)
        assertThat(publisher.publishedResults).isEmpty()
    }

    @Test
    fun `blocks replay during cooldown`() {
        val recordStore = InMemoryDeadLetterStore()
        val publisher = RecordingRoutedResultPublisher()
        val replayService = replayService(recordStore, publisher, cooldown = Duration.ofMinutes(5))
        val deadLetterId = UUID.randomUUID()
        recordStore.records[deadLetterId] =
            recordStore.analysisResultRecord(
                deadLetterId = deadLetterId,
                lastReplayAt = Instant.now().minusSeconds(30),
            )

        val replayResult = replayService.replay(deadLetterId, operatorNote = "cooldown check")

        assertThat(replayResult).isNotNull
        assertThat(replayResult!!.replayed).isFalse()
        assertThat(replayResult.outcome).isEqualTo(DeadLetterReplayOutcome.REPLAY_BLOCKED)
        assertThat(replayResult.message).contains("cooldown")
        assertThat(recordStore.records[deadLetterId]!!.replayCount).isZero()
        assertThat(publisher.publishedResults).isEmpty()
    }

    @Test
    fun `returns null when dead-letter id is missing`() {
        val replayService = replayService()

        assertThat(replayService.replay(UUID.randomUUID())).isNull()
    }

    private fun replayService(
        recordStore: InMemoryDeadLetterStore = InMemoryDeadLetterStore(),
        publisher: RecordingRoutedResultPublisher = RecordingRoutedResultPublisher(),
        maxReplayAttempts: Int = 3,
        cooldown: Duration = Duration.ofMinutes(5),
        requireOperatorNote: Boolean = true,
    ): DeadLetterReplayService =
        DeadLetterReplayService(
            deadLetterStore = recordStore,
            deadLetterReplayPublisher = publisher,
            jsonMapper = JsonMapper.builder().findAndAddModules().build(),
            replayProperties =
                DeadLetterReplayProperties(
                    maxReplayAttempts = maxReplayAttempts,
                    cooldown = cooldown,
                    requireOperatorNote = requireOperatorNote,
                ),
        )

    private class RecordingRoutedResultPublisher : DeadLetterReplayPublisher {
        val publishedResults = mutableListOf<AnalysisResult>()

        override fun publishAnalysisResult(result: AnalysisResult) {
            publishedResults += result
        }
    }

    private class InMemoryDeadLetterStore : DeadLetterStore {
        val records = mutableMapOf<UUID, DeadLetterRecord>()

        fun analysisResultRecord(
            deadLetterId: UUID,
            replayCount: Int = 0,
            lastReplayAt: Instant? = null,
        ): DeadLetterRecord {
            val jsonMapper = JsonMapper.builder().findAndAddModules().build()
            val analysisResult =
                AnalysisResult(
                    eventId = UUID.randomUUID(),
                    tenantId = "tenant-alpha",
                    category = "error",
                    severity = Severity.HIGH,
                    confidence = 0.8,
                    summary = "Checkout timeout",
                    detail = AnalysisDetail(analysis = "Checkout degraded"),
                    llmMetadata = LlmMetadata(model = "stub", promptVersion = "v1"),
                    routing = RoutingDecision(channels = listOf("telegram")),
                )

            return DeadLetterRecord(
                id = deadLetterId,
                sourceStage = "delivery",
                sourceTopic = "sentinel.routed-results",
                tenantId = "tenant-alpha",
                eventId = analysisResult.eventId,
                channel = "telegram",
                reason = "Telegram delivery timeout",
                payloadType = DeadLetterPayloadType.ANALYSIS_RESULT,
                payload = jsonMapper.writeValueAsString(analysisResult),
                status = DeadLetterStatus.OPEN,
                replayCount = replayCount,
                createdAt = Instant.now(),
                lastReplayAt = lastReplayAt,
                lastReplayError = null,
                lastReplayOperatorNote = null,
            )
        }

        override fun save(write: DeadLetterWrite): DeadLetterRecord {
            val record =
                DeadLetterRecord(
                    id = write.id,
                    sourceStage = write.sourceStage,
                    sourceTopic = write.sourceTopic,
                    tenantId = write.tenantId,
                    eventId = write.eventId,
                    channel = write.channel,
                    reason = write.reason,
                    payloadType = write.payloadType,
                    payload = write.payload,
                    status = write.status,
                    replayCount = write.replayCount,
                    createdAt = write.createdAt,
                    lastReplayAt = null,
                    lastReplayError = null,
                    lastReplayOperatorNote = null,
                )
            records[write.id] = record
            return record
        }

        override fun findById(id: UUID): DeadLetterRecord? = records[id]

        override fun findRecent(query: DeadLetterQuery): List<DeadLetterRecord> = records.values.toList()

        override fun markReplayed(
            id: UUID,
            replayedAt: Instant,
            operatorNote: String?,
        ) {
            val current = records[id] ?: return
            records[id] =
                current.copy(
                    status = DeadLetterStatus.REPLAYED,
                    replayCount = current.replayCount + 1,
                    lastReplayAt = replayedAt,
                    lastReplayError = null,
                    lastReplayOperatorNote = operatorNote,
                )
        }

        override fun markReplayFailed(
            id: UUID,
            replayError: String,
            replayedAt: Instant,
            operatorNote: String?,
        ) {
            val current = records[id] ?: return
            records[id] =
                current.copy(
                    status = DeadLetterStatus.REPLAY_FAILED,
                    replayCount = current.replayCount + 1,
                    lastReplayAt = replayedAt,
                    lastReplayError = replayError,
                    lastReplayOperatorNote = operatorNote,
                )
        }
    }
}
