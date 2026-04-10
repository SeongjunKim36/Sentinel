package io.github.seongjunkim36.sentinel.deadletter

import java.time.Instant
import java.util.UUID

interface DeadLetterReplayAuditStore {
    fun save(write: DeadLetterReplayAuditWrite)

    fun findRecentByDeadLetterId(
        deadLetterId: UUID,
        limit: Int = 50,
    ): List<DeadLetterReplayAuditRecord>
}

data class DeadLetterReplayAuditWrite(
    val deadLetterId: UUID,
    val outcome: DeadLetterReplayOutcome,
    val status: DeadLetterStatus,
    val message: String,
    val operatorNote: String? = null,
    val createdAt: Instant = Instant.now(),
)

data class DeadLetterReplayAuditRecord(
    val id: Long,
    val deadLetterId: UUID,
    val outcome: DeadLetterReplayOutcome,
    val status: DeadLetterStatus,
    val message: String,
    val operatorNote: String?,
    val createdAt: Instant,
)
