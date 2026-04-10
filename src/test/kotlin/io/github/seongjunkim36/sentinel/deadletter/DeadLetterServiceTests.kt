package io.github.seongjunkim36.sentinel.deadletter

import io.github.seongjunkim36.sentinel.shared.AnalysisDetail
import io.github.seongjunkim36.sentinel.shared.AnalysisResult
import io.github.seongjunkim36.sentinel.shared.DeadLetterPayloadType
import io.github.seongjunkim36.sentinel.shared.LlmMetadata
import io.github.seongjunkim36.sentinel.shared.RoutingDecision
import io.github.seongjunkim36.sentinel.shared.Severity
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class DeadLetterServiceTests {
    @Test
    fun `records and publishes dead-letter for failed delivery`() {
        val store = RecordingDeadLetterStore()
        val publisher = RecordingDeadLetterPublisher()
        val deadLetterService =
            DeadLetterService(
                deadLetterStore = store,
                deadLetterPublisher = publisher,
                jsonMapper = JsonMapper.builder().findAndAddModules().build(),
            )

        val result =
            AnalysisResult(
                eventId = UUID.randomUUID(),
                tenantId = "tenant-alpha",
                category = "error",
                severity = Severity.HIGH,
                confidence = 0.7,
                summary = "Checkout timeout",
                detail = AnalysisDetail(analysis = "Checkout degraded"),
                llmMetadata = LlmMetadata(model = "stub", promptVersion = "v1"),
                routing = RoutingDecision(channels = listOf("slack")),
            )

        deadLetterService.recordDeliveryFailure(
            result = result,
            channel = "slack",
            reason = "Slack delivery is not configured",
        )

        assertThat(store.savedWrites).hasSize(1)
        assertThat(store.savedWrites.single().payloadType).isEqualTo(DeadLetterPayloadType.ANALYSIS_RESULT)
        assertThat(store.savedWrites.single().channel).isEqualTo("slack")
        assertThat(store.savedWrites.single().reason).contains("not configured")

        assertThat(publisher.publishedEvents).hasSize(1)
        assertThat(publisher.publishedEvents.single().eventId).isEqualTo(result.eventId)
        assertThat(publisher.publishedEvents.single().tenantId).isEqualTo("tenant-alpha")
    }

    private class RecordingDeadLetterStore : DeadLetterStore {
        val savedWrites = mutableListOf<DeadLetterWrite>()

        override fun save(write: DeadLetterWrite): DeadLetterRecord {
            savedWrites += write
            return DeadLetterRecord(
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
            )
        }

        override fun findById(id: UUID): DeadLetterRecord? = null

        override fun findRecent(query: DeadLetterQuery): List<DeadLetterRecord> = emptyList()

        override fun markReplayed(
            id: UUID,
            replayedAt: java.time.Instant,
        ) {
        }

        override fun markReplayFailed(
            id: UUID,
            replayError: String,
            replayedAt: java.time.Instant,
        ) {
        }
    }

    private class RecordingDeadLetterPublisher : DeadLetterPublisher {
        val publishedEvents = mutableListOf<io.github.seongjunkim36.sentinel.shared.DeadLetterEvent>()

        override fun publish(event: io.github.seongjunkim36.sentinel.shared.DeadLetterEvent) {
            publishedEvents += event
        }
    }
}
