package io.github.seongjunkim36.sentinel.deadletter

import io.github.seongjunkim36.sentinel.shared.AnalysisResult
import io.github.seongjunkim36.sentinel.shared.DeadLetterPayloadType
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.json.JsonMapper

class DeadLetterControllerTests {
    @Test
    fun `returns not found when replay audits requested for missing dead letter`() {
        val deadLetterStore = RecordingDeadLetterStore()
        val auditStore = RecordingDeadLetterReplayAuditStore()
        val controller = deadLetterController(deadLetterStore, auditStore)

        val response =
            controller.findReplayAudits(
                id = UUID.randomUUID(),
                limit = 25,
                cursor = null,
                tenantScopeHeader = "tenant-alpha",
            )

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(response.body).isNull()
    }

    @Test
    fun `returns replay audits with cursor pagination`() {
        val deadLetterStore = RecordingDeadLetterStore()
        val auditStore = RecordingDeadLetterReplayAuditStore()
        val deadLetterId = UUID.randomUUID()
        deadLetterStore.records[deadLetterId] = deadLetterStore.sampleRecord(deadLetterId, createdAt = Instant.parse("2026-04-01T00:00:00Z"))
        auditStore.records +=
            DeadLetterReplayAuditRecord(
                id = 1L,
                deadLetterId = deadLetterId,
                outcome = DeadLetterReplayOutcome.REPLAY_FAILED,
                status = DeadLetterStatus.REPLAY_FAILED,
                message = "failed",
                operatorNote = "first",
                createdAt = Instant.parse("2026-04-01T00:05:00Z"),
            )
        auditStore.records +=
            DeadLetterReplayAuditRecord(
                id = 2L,
                deadLetterId = deadLetterId,
                outcome = DeadLetterReplayOutcome.REPLAYED,
                status = DeadLetterStatus.REPLAYED,
                message = "replayed",
                operatorNote = "second",
                createdAt = Instant.parse("2026-04-01T00:10:00Z"),
            )
        val controller = deadLetterController(deadLetterStore, auditStore)

        val first =
            controller.findReplayAudits(
                id = deadLetterId,
                limit = 1,
                cursor = null,
                tenantScopeHeader = "tenant-alpha",
            )

        assertThat(first.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(auditStore.lastDeadLetterId).isEqualTo(deadLetterId)
        assertThat(auditStore.lastQuery!!.limit).isEqualTo(2)
        assertThat(first.body!!.items).hasSize(1)
        assertThat(first.body!!.items.single().id).isEqualTo(2L)
        assertThat(first.body!!.page.hasMore).isTrue()
        assertThat(first.body!!.page.nextCursor).isNotBlank()

        val second =
            controller.findReplayAudits(
                id = deadLetterId,
                limit = 1,
                cursor = first.body!!.page.nextCursor,
                tenantScopeHeader = "tenant-alpha",
            )

        assertThat(second.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(second.body!!.items).hasSize(1)
        assertThat(second.body!!.items.single().id).isEqualTo(1L)
        assertThat(second.body!!.page.hasMore).isFalse()
        assertThat(second.body!!.page.nextCursor).isNull()
    }

    @Test
    fun `rejects replay when authorization is enabled and token is missing`() {
        val deadLetterStore = RecordingDeadLetterStore()
        val auditStore = RecordingDeadLetterReplayAuditStore()
        val deadLetterId = UUID.randomUUID()
        deadLetterStore.records[deadLetterId] = deadLetterStore.sampleRecord(deadLetterId)
        val controller =
            deadLetterController(
                deadLetterStore = deadLetterStore,
                auditStore = auditStore,
                apiProperties =
                    DeadLetterApiProperties(
                        replayAuthorization =
                            DeadLetterReplayAuthorizationProperties(
                                enabled = true,
                                token = "secret-token",
                            ),
                    ),
            )

        val request = MockHttpServletRequest()
        val response =
            kotlin.runCatching {
                controller.replay(
                    id = deadLetterId,
                    request = DeadLetterReplayRequest("manual retry"),
                    tenantScopeHeader = "tenant-alpha",
                    httpServletRequest = request,
                )
            }.exceptionOrNull()

        assertThat(response).isInstanceOf(DeadLetterReplayUnauthorizedException::class.java)
    }

    @Test
    fun `accepts replay when authorization token is valid`() {
        val deadLetterStore = RecordingDeadLetterStore()
        val auditStore = RecordingDeadLetterReplayAuditStore()
        val deadLetterId = UUID.randomUUID()
        deadLetterStore.records[deadLetterId] = deadLetterStore.sampleRecord(deadLetterId)
        val controller =
            deadLetterController(
                deadLetterStore = deadLetterStore,
                auditStore = auditStore,
                apiProperties =
                    DeadLetterApiProperties(
                        replayAuthorization =
                            DeadLetterReplayAuthorizationProperties(
                                enabled = true,
                                token = "secret-token",
                            ),
                    ),
            )

        val request =
            MockHttpServletRequest().apply {
                addHeader("X-Sentinel-Replay-Token", "secret-token")
            }

        val response =
            controller.replay(
                id = deadLetterId,
                request = DeadLetterReplayRequest("manual retry"),
                tenantScopeHeader = "tenant-alpha",
                httpServletRequest = request,
            )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
    }

    @Test
    fun `returns not found when replay request is outside tenant scope`() {
        val deadLetterStore = RecordingDeadLetterStore()
        val auditStore = RecordingDeadLetterReplayAuditStore()
        val deadLetterId = UUID.randomUUID()
        deadLetterStore.records[deadLetterId] =
            deadLetterStore.sampleRecord(
                id = deadLetterId,
                tenantId = "tenant-alpha",
            )
        val controller = deadLetterController(deadLetterStore, auditStore)

        val response =
            controller.replay(
                id = deadLetterId,
                request = DeadLetterReplayRequest("manual retry"),
                tenantScopeHeader = "tenant-beta",
                httpServletRequest = MockHttpServletRequest(),
            )

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `rejects replay when operator note exceeds max length`() {
        val deadLetterStore = RecordingDeadLetterStore()
        val auditStore = RecordingDeadLetterReplayAuditStore()
        val deadLetterId = UUID.randomUUID()
        deadLetterStore.records[deadLetterId] = deadLetterStore.sampleRecord(deadLetterId)
        val controller =
            deadLetterController(
                deadLetterStore = deadLetterStore,
                auditStore = auditStore,
                apiProperties = DeadLetterApiProperties(maxOperatorNoteLength = 10),
            )

        val response =
            kotlin.runCatching {
                controller.replay(
                    id = deadLetterId,
                    request = DeadLetterReplayRequest("note-that-is-way-too-long"),
                    tenantScopeHeader = "tenant-alpha",
                    httpServletRequest = MockHttpServletRequest(),
                )
            }.exceptionOrNull()

        assertThat(response).isInstanceOf(ResponseStatusException::class.java)
        val responseStatusException = response as ResponseStatusException
        assertThat(responseStatusException.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `rejects dead-letter list query when tenant filter mismatches tenant scope`() {
        val deadLetterStore = RecordingDeadLetterStore()
        val auditStore = RecordingDeadLetterReplayAuditStore()
        val controller = deadLetterController(deadLetterStore, auditStore)

        val response =
            kotlin.runCatching {
                controller.findRecent(
                    status = null,
                    tenantId = "tenant-beta",
                    channel = null,
                    limit = 50,
                    cursor = null,
                    tenantScopeHeader = "tenant-alpha",
                )
            }.exceptionOrNull()

        assertThat(response).isInstanceOf(ResponseStatusException::class.java)
        assertThat((response as ResponseStatusException).statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `returns dead-letter records with cursor pagination under tenant scope`() {
        val deadLetterStore = RecordingDeadLetterStore()
        val auditStore = RecordingDeadLetterReplayAuditStore()
        val firstId = UUID.randomUUID()
        val secondId = UUID.randomUUID()
        deadLetterStore.records[firstId] =
            deadLetterStore.sampleRecord(
                id = firstId,
                tenantId = "tenant-alpha",
                createdAt = Instant.parse("2026-04-01T00:10:00Z"),
            )
        deadLetterStore.records[secondId] =
            deadLetterStore.sampleRecord(
                id = secondId,
                tenantId = "tenant-alpha",
                createdAt = Instant.parse("2026-04-01T00:05:00Z"),
            )
        val otherTenantId = UUID.randomUUID()
        deadLetterStore.records[otherTenantId] =
            deadLetterStore.sampleRecord(
                id = otherTenantId,
                tenantId = "tenant-beta",
                createdAt = Instant.parse("2026-04-01T00:15:00Z"),
            )
        val controller = deadLetterController(deadLetterStore, auditStore)

        val first =
            controller.findRecent(
                status = null,
                tenantId = null,
                channel = null,
                limit = 1,
                cursor = null,
                tenantScopeHeader = "tenant-alpha",
            )

        assertThat(first.items).hasSize(1)
        assertThat(first.items.single().tenantId).isEqualTo("tenant-alpha")
        assertThat(first.page.hasMore).isTrue()
        assertThat(first.page.nextCursor).isNotBlank()

        val second =
            controller.findRecent(
                status = null,
                tenantId = null,
                channel = null,
                limit = 1,
                cursor = first.page.nextCursor,
                tenantScopeHeader = "tenant-alpha",
            )

        assertThat(second.items).hasSize(1)
        assertThat(second.items.single().tenantId).isEqualTo("tenant-alpha")
        assertThat(second.page.hasMore).isFalse()
        assertThat(second.page.nextCursor).isNull()
    }

    private fun deadLetterController(
        deadLetterStore: RecordingDeadLetterStore,
        auditStore: RecordingDeadLetterReplayAuditStore,
        apiProperties: DeadLetterApiProperties = DeadLetterApiProperties(),
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
            deadLetterReplayAuthorizationService =
                DeadLetterReplayAuthorizationService(
                    deadLetterApiProperties = apiProperties,
                ),
            deadLetterApiProperties = apiProperties,
        )

    private class NoOpReplayPublisher : DeadLetterReplayPublisher {
        override fun publishAnalysisResult(result: AnalysisResult) {
        }
    }

    private class RecordingDeadLetterReplayAuditStore : DeadLetterReplayAuditStore {
        val records = mutableListOf<DeadLetterReplayAuditRecord>()
        var lastDeadLetterId: UUID? = null
        var lastQuery: DeadLetterReplayAuditQuery? = null

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
            query: DeadLetterReplayAuditQuery,
        ): List<DeadLetterReplayAuditRecord> {
            lastDeadLetterId = deadLetterId
            lastQuery = query
            val sorted =
                records
                    .filter { it.deadLetterId == deadLetterId }
                    .sortedWith(
                        compareByDescending<DeadLetterReplayAuditRecord> { it.createdAt }
                            .thenByDescending { it.id },
                    )

            val cursorFiltered =
                query.cursor?.let { cursor ->
                    sorted.filter { record ->
                        record.createdAt.isBefore(cursor.createdAt) ||
                            (record.createdAt == cursor.createdAt && record.id < cursor.id)
                    }
                } ?: sorted

            return cursorFiltered.take(query.limit.coerceAtLeast(1))
        }

        override fun countRecentReplayFailures(
            tenantId: String,
            channel: String?,
            since: Instant,
        ): Long = 0
    }

    private class RecordingDeadLetterStore : DeadLetterStore {
        val records = mutableMapOf<UUID, DeadLetterRecord>()

        fun sampleRecord(
            id: UUID,
            tenantId: String = "tenant-alpha",
            createdAt: Instant = Instant.now(),
        ): DeadLetterRecord =
            DeadLetterRecord(
                id = id,
                sourceStage = "delivery",
                sourceTopic = "sentinel.routed-results",
                tenantId = tenantId,
                eventId = UUID.randomUUID(),
                channel = "telegram",
                reason = "timeout",
                payloadType = DeadLetterPayloadType.ANALYSIS_RESULT,
                payload = "{}",
                status = DeadLetterStatus.OPEN,
                replayCount = 0,
                createdAt = createdAt,
                lastReplayAt = null,
                lastReplayError = null,
                lastReplayOperatorNote = null,
            )

        override fun save(write: DeadLetterWrite): DeadLetterRecord {
            val record = sampleRecord(write.id, tenantId = write.tenantId, createdAt = write.createdAt)
            records[write.id] = record
            return record
        }

        override fun findById(id: UUID): DeadLetterRecord? = records[id]

        override fun findRecent(query: DeadLetterQuery): List<DeadLetterRecord> {
            val sorted =
                records.values
                    .asSequence()
                    .filter { record -> query.status == null || record.status == query.status }
                    .filter { record -> query.tenantId == null || record.tenantId == query.tenantId }
                    .filter { record -> query.channel == null || record.channel == query.channel }
                    .sortedWith(
                        compareByDescending<DeadLetterRecord> { it.createdAt }
                            .thenByDescending { it.id },
                    )
                    .toList()

            val cursorFiltered =
                query.cursor?.let { cursor ->
                    sorted.filter { record ->
                        record.createdAt.isBefore(cursor.createdAt) ||
                            (record.createdAt == cursor.createdAt && record.id < cursor.id)
                    }
                } ?: sorted

            return cursorFiltered.take(query.limit.coerceAtLeast(1))
        }

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
