package io.github.seongjunkim36.sentinel.deadletter

import java.util.UUID

class NoOpDeadLetterReplayAuditStore : DeadLetterReplayAuditStore {
    override fun save(write: DeadLetterReplayAuditWrite) {
    }

    override fun findRecentByDeadLetterId(
        deadLetterId: UUID,
        limit: Int,
    ): List<DeadLetterReplayAuditRecord> = emptyList()
}
