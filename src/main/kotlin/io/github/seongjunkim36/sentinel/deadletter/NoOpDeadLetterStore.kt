package io.github.seongjunkim36.sentinel.deadletter

import java.time.Instant
import java.util.UUID
import org.slf4j.LoggerFactory

class NoOpDeadLetterStore : DeadLetterStore {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun save(write: DeadLetterWrite): DeadLetterRecord {
        logger.debug(
            "Skipping dead-letter persistence because no JDBC store is available: eventId={}, stage={}, reason={}",
            write.eventId,
            write.sourceStage,
            write.reason,
        )

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
            lastReplayOperatorNote = null,
        )
    }

    override fun findById(id: UUID): DeadLetterRecord? = null

    override fun findRecent(query: DeadLetterQuery): List<DeadLetterRecord> = emptyList()

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
