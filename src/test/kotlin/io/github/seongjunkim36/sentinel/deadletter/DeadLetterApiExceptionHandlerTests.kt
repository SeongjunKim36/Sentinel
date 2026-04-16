package io.github.seongjunkim36.sentinel.deadletter

import io.github.seongjunkim36.sentinel.shared.AnalysisResult
import io.github.seongjunkim36.sentinel.shared.DeadLetterPayloadType
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import tools.jackson.databind.json.JsonMapper

class DeadLetterApiExceptionHandlerTests {
    @Test
    fun `returns stabilized 401 contract when replay authorization fails`() {
        val deadLetterId = UUID.randomUUID()
        val mockMvc =
            replayMockMvc(
                record = sampleRecord(deadLetterId),
                apiProperties =
                    DeadLetterApiProperties(
                        replayAuthorization =
                            DeadLetterReplayAuthorizationProperties(
                                enabled = true,
                                token = "replay-token",
                            ),
                    ),
            )

        mockMvc.post("/api/v1/dead-letters/$deadLetterId/replay") {
            header("X-Sentinel-Tenant-Id", "tenant-alpha")
        }.andExpect {
            status { isUnauthorized() }
            header { string("Cache-Control", "no-store") }
            jsonPath("$.title") { value("Unauthorized") }
            jsonPath("$.type") { value("urn:sentinel:error:dead-letter-replay-unauthorized") }
            jsonPath("$.scope") { value("dead-letter-replay") }
            jsonPath("$.errorCode") { value("DEAD_LETTER_REPLAY_UNAUTHORIZED") }
        }
    }

    @Test
    fun `returns stabilized 404 contract when replay target is missing`() {
        val deadLetterId = UUID.randomUUID()
        val mockMvc = replayMockMvc(record = null)

        mockMvc.post("/api/v1/dead-letters/$deadLetterId/replay") {
            header("X-Sentinel-Tenant-Id", "tenant-alpha")
        }.andExpect {
            status { isNotFound() }
            header { string("Cache-Control", "no-store") }
            jsonPath("$.title") { value("Not Found") }
            jsonPath("$.type") { value("urn:sentinel:error:dead-letter-replay-not-found") }
            jsonPath("$.scope") { value("dead-letter-replay") }
            jsonPath("$.errorCode") { value("DEAD_LETTER_REPLAY_NOT_FOUND") }
            jsonPath("$.deadLetterId") { value(deadLetterId.toString()) }
        }
    }

    @Test
    fun `returns stabilized 409 contract when replay is blocked`() {
        val deadLetterId = UUID.randomUUID()
        val mockMvc = replayMockMvc(record = sampleRecord(deadLetterId))

        mockMvc.post("/api/v1/dead-letters/$deadLetterId/replay") {
            header("X-Sentinel-Tenant-Id", "tenant-alpha")
        }.andExpect {
            status { isConflict() }
            header { string("Cache-Control", "no-store") }
            jsonPath("$.title") { value("Conflict") }
            jsonPath("$.type") { value("urn:sentinel:error:dead-letter-replay-blocked") }
            jsonPath("$.scope") { value("dead-letter-replay") }
            jsonPath("$.errorCode") { value("DEAD_LETTER_REPLAY_BLOCKED") }
            jsonPath("$.deadLetterId") { value(deadLetterId.toString()) }
            jsonPath("$.replayStatus") { value("OPEN") }
            jsonPath("$.replayOutcome") { value("REPLAY_BLOCKED") }
            jsonPath("$.detail") { value("Replay requires an operator note") }
        }
    }

    @Test
    fun `returns stabilized 400 contract when operator note is too long`() {
        val deadLetterId = UUID.randomUUID()
        val mockMvc =
            replayMockMvc(
                record = sampleRecord(deadLetterId),
                apiProperties = DeadLetterApiProperties(maxOperatorNoteLength = 5),
            )

        mockMvc.post("/api/v1/dead-letters/$deadLetterId/replay") {
            header("X-Sentinel-Tenant-Id", "tenant-alpha")
            contentType = MediaType.APPLICATION_JSON
            content = """{"operatorNote":"note-is-too-long"}"""
        }.andExpect {
            status { isBadRequest() }
            header { string("Cache-Control", "no-store") }
            jsonPath("$.title") { value("Bad Request") }
            jsonPath("$.type") { value("urn:sentinel:error:dead-letter-replay-operator-note-too-long") }
            jsonPath("$.scope") { value("dead-letter-replay") }
            jsonPath("$.errorCode") { value("DEAD_LETTER_REPLAY_OPERATOR_NOTE_TOO_LONG") }
        }
    }

    @Test
    fun `returns stabilized 400 contract when dead-letter list tenant scope mismatches`() {
        val mockMvc = deadLetterMockMvc(record = sampleRecord(UUID.randomUUID()))

        mockMvc.get("/api/v1/dead-letters") {
            header("X-Sentinel-Tenant-Id", "tenant-alpha")
            param("tenantId", "tenant-beta")
        }.andExpect {
            status { isBadRequest() }
            header { string("Cache-Control", "no-store") }
            jsonPath("$.title") { value("Bad Request") }
            jsonPath("$.type") { value("urn:sentinel:error:dead-letter-list-tenant-scope-mismatch") }
            jsonPath("$.scope") { value("dead-letter-list") }
            jsonPath("$.errorCode") { value("DEAD_LETTER_LIST_TENANT_SCOPE_MISMATCH") }
        }
    }

    @Test
    fun `returns stabilized 400 contract when dead-letter list tenant header is missing`() {
        val mockMvc = deadLetterMockMvc(record = sampleRecord(UUID.randomUUID()))

        mockMvc.get("/api/v1/dead-letters").andExpect {
            status { isBadRequest() }
            header { string("Cache-Control", "no-store") }
            jsonPath("$.title") { value("Bad Request") }
            jsonPath("$.type") { value("urn:sentinel:error:dead-letter-list-tenant-scope-required") }
            jsonPath("$.scope") { value("dead-letter-list") }
            jsonPath("$.errorCode") { value("DEAD_LETTER_LIST_TENANT_SCOPE_REQUIRED") }
        }
    }

    @Test
    fun `returns stabilized 404 contract when replay audits target is missing`() {
        val deadLetterId = UUID.randomUUID()
        val mockMvc = deadLetterMockMvc(record = null)

        mockMvc.get("/api/v1/dead-letters/$deadLetterId/replay-audits") {
            header("X-Sentinel-Tenant-Id", "tenant-alpha")
        }.andExpect {
            status { isNotFound() }
            header { string("Cache-Control", "no-store") }
            jsonPath("$.title") { value("Not Found") }
            jsonPath("$.type") { value("urn:sentinel:error:dead-letter-replay-audits-not-found") }
            jsonPath("$.scope") { value("dead-letter-replay-audits") }
            jsonPath("$.errorCode") { value("DEAD_LETTER_REPLAY_AUDITS_NOT_FOUND") }
            jsonPath("$.deadLetterId") { value(deadLetterId.toString()) }
        }
    }

    @Test
    fun `returns stabilized 400 contract when replay audits cursor is invalid`() {
        val deadLetterId = UUID.randomUUID()
        val mockMvc = deadLetterMockMvc(record = sampleRecord(deadLetterId))

        mockMvc.get("/api/v1/dead-letters/$deadLetterId/replay-audits") {
            header("X-Sentinel-Tenant-Id", "tenant-alpha")
            param("cursor", "invalid-cursor")
        }.andExpect {
            status { isBadRequest() }
            header { string("Cache-Control", "no-store") }
            jsonPath("$.title") { value("Bad Request") }
            jsonPath("$.type") { value("urn:sentinel:error:dead-letter-replay-audits-cursor-invalid") }
            jsonPath("$.scope") { value("dead-letter-replay-audits") }
            jsonPath("$.errorCode") { value("DEAD_LETTER_REPLAY_AUDITS_CURSOR_INVALID") }
        }
    }

    private fun replayMockMvc(
        record: DeadLetterRecord?,
        apiProperties: DeadLetterApiProperties = DeadLetterApiProperties(),
    ): org.springframework.test.web.servlet.MockMvc = deadLetterMockMvc(record = record, apiProperties = apiProperties)

    private fun deadLetterMockMvc(
        record: DeadLetterRecord?,
        apiProperties: DeadLetterApiProperties = DeadLetterApiProperties(),
    ): org.springframework.test.web.servlet.MockMvc {
        val deadLetterStore = TestDeadLetterStore(record)
        val replayAuditStore = TestDeadLetterReplayAuditStore()

        return MockMvcBuilders
            .standaloneSetup(
                DeadLetterController(
                    deadLetterStore = deadLetterStore,
                    deadLetterReplayAuditStore = replayAuditStore,
                    deadLetterReplayService =
                        DeadLetterReplayService(
                            deadLetterStore = deadLetterStore,
                            deadLetterReplayAuditStore = replayAuditStore,
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
                ),
            ).setControllerAdvice(DeadLetterApiExceptionHandler())
            .build()
    }

    private fun sampleRecord(id: UUID): DeadLetterRecord =
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
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            lastReplayAt = null,
            lastReplayError = null,
            lastReplayOperatorNote = null,
        )

    private class TestDeadLetterStore(
        record: DeadLetterRecord?,
    ) : DeadLetterStore {
        private val records = mutableMapOf<UUID, DeadLetterRecord>()

        init {
            if (record != null) {
                records[record.id] = record
            }
        }

        override fun save(write: DeadLetterWrite): DeadLetterRecord {
            val saved =
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
            records[saved.id] = saved
            return saved
        }

        override fun findById(id: UUID): DeadLetterRecord? = records[id]

        override fun findRecent(query: DeadLetterQuery): List<DeadLetterRecord> =
            records.values.take(query.limit.coerceAtLeast(1))

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

    private class TestDeadLetterReplayAuditStore : DeadLetterReplayAuditStore {
        override fun save(write: DeadLetterReplayAuditWrite) {
        }

        override fun findRecentByDeadLetterId(
            deadLetterId: UUID,
            query: DeadLetterReplayAuditQuery,
        ): List<DeadLetterReplayAuditRecord> = emptyList()

        override fun countRecentReplayFailures(
            tenantId: String,
            channel: String?,
            since: Instant,
        ): Long = 0
    }

    private class NoOpReplayPublisher : DeadLetterReplayPublisher {
        override fun publishAnalysisResult(result: AnalysisResult) {
        }
    }
}
