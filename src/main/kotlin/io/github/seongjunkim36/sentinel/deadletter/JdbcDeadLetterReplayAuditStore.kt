package io.github.seongjunkim36.sentinel.deadletter

import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate

class JdbcDeadLetterReplayAuditStore(
    private val jdbcTemplate: JdbcTemplate,
) : DeadLetterReplayAuditStore {
    override fun save(write: DeadLetterReplayAuditWrite) {
        jdbcTemplate.update(
            """
            insert into dead_letter_replay_audit (
                dead_letter_id,
                outcome,
                status,
                message,
                operator_note,
                created_at
            ) values (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            write.deadLetterId,
            write.outcome.name,
            write.status.name,
            write.message,
            write.operatorNote,
            Timestamp.from(write.createdAt),
        )
    }

    override fun findRecentByDeadLetterId(
        deadLetterId: UUID,
        limit: Int,
    ): List<DeadLetterReplayAuditRecord> {
        val normalizedLimit = limit.coerceIn(1, 200)
        return jdbcTemplate.query(
            """
            select
                id,
                dead_letter_id,
                outcome,
                status,
                message,
                operator_note,
                created_at
            from dead_letter_replay_audit
            where dead_letter_id = ?
            order by created_at desc
            limit ?
            """.trimIndent(),
            { rs, _ -> rs.toAuditRecord() },
            deadLetterId,
            normalizedLimit,
        )
    }

    override fun countRecentReplayFailures(
        tenantId: String,
        channel: String?,
        since: Instant,
    ): Long {
        val sql =
            """
            select count(*)
            from dead_letter_replay_audit audit
            join dead_letter_event event on event.id = audit.dead_letter_id
            where
                audit.outcome = ?
                and event.tenant_id = ?
                and (
                    (? is null and event.channel is null) or event.channel = ?
                )
                and audit.created_at >= ?
            """.trimIndent()

        return jdbcTemplate.queryForObject(
            sql,
            Long::class.java,
            DeadLetterReplayOutcome.REPLAY_FAILED.name,
            tenantId,
            channel,
            channel,
            Timestamp.from(since),
        ) ?: 0L
    }

    private fun ResultSet.toAuditRecord(): DeadLetterReplayAuditRecord =
        DeadLetterReplayAuditRecord(
            id = getLong("id"),
            deadLetterId = UUID.fromString(getString("dead_letter_id")),
            outcome = DeadLetterReplayOutcome.valueOf(getString("outcome")),
            status = DeadLetterStatus.valueOf(getString("status")),
            message = getString("message"),
            operatorNote = getString("operator_note"),
            createdAt = getTimestamp("created_at").toInstant(),
        )
}
