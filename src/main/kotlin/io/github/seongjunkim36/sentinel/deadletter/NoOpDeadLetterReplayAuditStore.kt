package io.github.seongjunkim36.sentinel.deadletter

import java.time.Instant
import java.util.UUID

class NoOpDeadLetterReplayAuditStore : DeadLetterReplayAuditStore {
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
