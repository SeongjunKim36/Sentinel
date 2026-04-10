package io.github.seongjunkim36.sentinel.deadletter

import io.github.seongjunkim36.sentinel.shared.DeadLetterPayloadType
import java.time.Instant
import java.util.UUID

interface DeadLetterStore {
    fun save(write: DeadLetterWrite): DeadLetterRecord

    fun findById(id: UUID): DeadLetterRecord?

    fun findRecent(query: DeadLetterQuery = DeadLetterQuery()): List<DeadLetterRecord>

    fun markReplayed(id: UUID, replayedAt: Instant = Instant.now())

    fun markReplayFailed(
        id: UUID,
        replayError: String,
        replayedAt: Instant = Instant.now(),
    )
}

data class DeadLetterWrite(
    val id: UUID = UUID.randomUUID(),
    val sourceStage: String,
    val sourceTopic: String,
    val tenantId: String,
    val eventId: UUID,
    val channel: String? = null,
    val reason: String,
    val payloadType: DeadLetterPayloadType,
    val payload: String,
    val status: DeadLetterStatus = DeadLetterStatus.OPEN,
    val replayCount: Int = 0,
    val createdAt: Instant = Instant.now(),
)

data class DeadLetterRecord(
    val id: UUID,
    val sourceStage: String,
    val sourceTopic: String,
    val tenantId: String,
    val eventId: UUID,
    val channel: String?,
    val reason: String,
    val payloadType: DeadLetterPayloadType,
    val payload: String,
    val status: DeadLetterStatus,
    val replayCount: Int,
    val createdAt: Instant,
    val lastReplayAt: Instant?,
    val lastReplayError: String?,
)

data class DeadLetterQuery(
    val status: DeadLetterStatus? = null,
    val tenantId: String? = null,
    val channel: String? = null,
    val limit: Int = 50,
)

enum class DeadLetterStatus {
    OPEN,
    REPLAYED,
    REPLAY_FAILED,
}
