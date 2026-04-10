package io.github.seongjunkim36.sentinel.deadletter

import io.github.seongjunkim36.sentinel.shared.DeadLetterPayloadType
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate

class JdbcDeadLetterStore(
    private val jdbcTemplate: JdbcTemplate,
) : DeadLetterStore {
    override fun save(write: DeadLetterWrite): DeadLetterRecord {
        jdbcTemplate.update(
            """
            insert into dead_letter_event (
                id,
                source_stage,
                source_topic,
                tenant_id,
                event_id,
                channel,
                reason,
                payload_type,
                payload,
                status,
                replay_count,
                created_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            write.id,
            write.sourceStage,
            write.sourceTopic,
            write.tenantId,
            write.eventId,
            write.channel,
            write.reason,
            write.payloadType.name,
            write.payload,
            write.status.name,
            write.replayCount,
            Timestamp.from(write.createdAt),
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

    override fun findById(id: UUID): DeadLetterRecord? =
        jdbcTemplate.query(
            """
            select
                id,
                source_stage,
                source_topic,
                tenant_id,
                event_id,
                channel,
                reason,
                payload_type,
                payload,
                status,
                replay_count,
                created_at,
                last_replay_at,
                last_replay_error,
                last_replay_operator_note
            from dead_letter_event
            where id = ?
            """.trimIndent(),
            { rs, _ -> rs.toDeadLetterRecord() },
            id,
        ).firstOrNull()

    override fun findRecent(query: DeadLetterQuery): List<DeadLetterRecord> {
        val limit = query.limit.coerceIn(1, 200)
        val args = mutableListOf<Any>()
        val sql =
            buildString {
                append(
                    """
                    select
                        id,
                        source_stage,
                        source_topic,
                        tenant_id,
                        event_id,
                        channel,
                        reason,
                        payload_type,
                        payload,
                        status,
                        replay_count,
                        created_at,
                        last_replay_at,
                        last_replay_error,
                        last_replay_operator_note
                    from dead_letter_event
                    where 1 = 1
                    """.trimIndent(),
                )

                query.status?.let {
                    append(" and status = ?")
                    args += it.name
                }
                query.tenantId?.let {
                    append(" and tenant_id = ?")
                    args += it
                }
                query.channel?.let {
                    append(" and channel = ?")
                    args += it
                }

                append(" order by created_at desc limit ?")
                args += limit
            }

        return jdbcTemplate.query(sql, { rs, _ -> rs.toDeadLetterRecord() }, *args.toTypedArray())
    }

    override fun markReplayed(
        id: UUID,
        replayedAt: Instant,
        operatorNote: String?,
    ) {
        jdbcTemplate.update(
            """
            update dead_letter_event
            set
                status = ?,
                replay_count = replay_count + 1,
                last_replay_at = ?,
                last_replay_error = null,
                last_replay_operator_note = ?
            where id = ?
            """.trimIndent(),
            DeadLetterStatus.REPLAYED.name,
            Timestamp.from(replayedAt),
            operatorNote,
            id,
        )
    }

    override fun markReplayFailed(
        id: UUID,
        replayError: String,
        replayedAt: Instant,
        operatorNote: String?,
    ) {
        jdbcTemplate.update(
            """
            update dead_letter_event
            set
                status = ?,
                replay_count = replay_count + 1,
                last_replay_at = ?,
                last_replay_error = ?,
                last_replay_operator_note = ?
            where id = ?
            """.trimIndent(),
            DeadLetterStatus.REPLAY_FAILED.name,
            Timestamp.from(replayedAt),
            replayError,
            operatorNote,
            id,
        )
    }

    private fun ResultSet.toDeadLetterRecord(): DeadLetterRecord =
        DeadLetterRecord(
            id = UUID.fromString(getString("id")),
            sourceStage = getString("source_stage"),
            sourceTopic = getString("source_topic"),
            tenantId = getString("tenant_id"),
            eventId = UUID.fromString(getString("event_id")),
            channel = getString("channel"),
            reason = getString("reason"),
            payloadType = DeadLetterPayloadType.valueOf(getString("payload_type")),
            payload = getString("payload"),
            status = DeadLetterStatus.valueOf(getString("status")),
            replayCount = getInt("replay_count"),
            createdAt = getTimestamp("created_at").toInstant(),
            lastReplayAt = getTimestamp("last_replay_at")?.toInstant(),
            lastReplayError = getString("last_replay_error"),
            lastReplayOperatorNote = getString("last_replay_operator_note"),
        )
}
