package io.github.seongjunkim36.sentinel.deadletter

import io.github.seongjunkim36.sentinel.shared.AnalysisResult
import io.github.seongjunkim36.sentinel.shared.DeadLetterPayloadType
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import tools.jackson.databind.json.JsonMapper

class DeadLetterControllerTests {
    @Test
    fun `returns not found when replay audits requested for missing dead letter`() {
        val deadLetterStore = RecordingDeadLetterStore()
        val auditStore = RecordingDeadLetterReplayAuditStore()
        val controller = deadLetterController(deadLetterStore, auditStore)

        val response = controller.findReplayAudits(UUID.randomUUID(), limit = 25)

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(response.body).isNull()
    }

    @Test
    fun `returns replay audits with requested limit`() {
        val deadLetterStore = RecordingDeadLetterStore()
        val auditStore = RecordingDeadLetterReplayAuditStore()
        val deadLetterId = UUID.randomUUID()
        deadLetterStore.records[deadLetterId] = deadLetterStore.sampleRecord(deadLetterId)
        auditStore.records +=
            DeadLetterReplayAuditRecord(
                id = 1L,
                deadLetterId = deadLetterId,
                outcome = DeadLetterReplayOutcome.REPLAYED,
                status = DeadLetterStatus.REPLAYED,
                message = "Replay published",
                operatorNote = "manual replay",
                createdAt = Instant.now(),
            )
        val controller = deadLetterController(deadLetterStore, auditStore)

        val response = controller.findReplayAudits(deadLetterId, limit = 10)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(auditStore.lastDeadLetterId).isEqualTo(deadLetterId)
        assertThat(auditStore.lastLimit).isEqualTo(10)
        assertThat(response.body).hasSize(1)
        assertThat(response.body!!.single().outcome).isEqualTo(DeadLetterReplayOutcome.REPLAYED)
    }

    private fun deadLetterController(
        deadLetterStore: RecordingDeadLetterStore,
        auditStore: RecordingDeadLetterReplayAuditStore,
    ): DeadLetterController =
        DeadLetterController(
            deadLetterStore = deadLetterStore,
            deadLetterReplayAuditStore = auditStore,
            deadLetterReplayService =
                DeadLetterReplayService(
                    deadLetterStore = deadLetterStore,
                    deadLetterReplayAuditStore = auditStore,
                    deadLetterReplayPublisher = NoOpReplayPublisher(),
                    jsonMapper = JsonMapper.builder().findAndAddModules().build(),
                    replayProperties =
                        DeadLetterReplayProperties(
                            maxReplayAttempts = 3,
                            cooldown = Duration.ofMinutes(5),
                            requireOperatorNote = true,
                        ),
                ),
        )

    private class NoOpReplayPublisher : DeadLetterReplayPublisher {
        override fun publishAnalysisResult(result: AnalysisResult) {
        }
    }

    private class RecordingDeadLetterReplayAuditStore : DeadLetterReplayAuditStore {
        val records = mutableListOf<DeadLetterReplayAuditRecord>()
        var lastDeadLetterId: UUID? = null
        var lastLimit: Int? = null

        override fun save(write: DeadLetterReplayAuditWrite) {
            records +=
                DeadLetterReplayAuditRecord(
                    id = (records.size + 1).toLong(),
                    deadLetterId = write.deadLetterId,
                    outcome = write.outcome,
                    status = write.status,
                    message = write.message,
                    operatorNote = write.operatorNote,
                    createdAt = write.createdAt,
                )
        }

        override fun findRecentByDeadLetterId(
            deadLetterId: UUID,
            limit: Int,
        ): List<DeadLetterReplayAuditRecord> {
            lastDeadLetterId = deadLetterId
            lastLimit = limit
            return records
                .asReversed()
                .filter { it.deadLetterId == deadLetterId }
                .take(limit.coerceAtLeast(1))
        }

        override fun countRecentReplayFailures(
            tenantId: String,
            channel: String?,
            since: Instant,
        ): Long = 0
    }

    private class RecordingDeadLetterStore : DeadLetterStore {
        val records = mutableMapOf<UUID, DeadLetterRecord>()

        fun sampleRecord(id: UUID): DeadLetterRecord =
            DeadLetterRecord(
                id = id,
                sourceStage = "delivery",
                sourceTopic = "sentinel.routed-results",
                tenantId = "tenant-alpha",
                eventId = UUID.randomUUID(),
                channel = "telegram",
                reason = "timeout",
                payloadType = DeadLetterPayloadType.ANALYSIS_RESULT,
                payload = "{}",
                status = DeadLetterStatus.OPEN,
                replayCount = 0,
                createdAt = Instant.now(),
                lastReplayAt = null,
                lastReplayError = null,
                lastReplayOperatorNote = null,
            )

        override fun save(write: DeadLetterWrite): DeadLetterRecord {
            val record = sampleRecord(write.id)
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
        }

        override fun markReplayFailed(
            id: UUID,
            replayError: String,
            replayedAt: Instant,
            operatorNote: String?,
        ) {
        }
    }
}
