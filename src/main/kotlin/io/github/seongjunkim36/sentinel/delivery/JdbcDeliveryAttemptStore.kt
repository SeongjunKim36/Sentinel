package io.github.seongjunkim36.sentinel.delivery

import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate

class JdbcDeliveryAttemptStore(
    private val jdbcTemplate: JdbcTemplate,
) : DeliveryAttemptStore {
    override fun record(attempt: DeliveryAttemptWrite) {
        jdbcTemplate.update(
            """
            insert into delivery_attempt (
                analysis_result_id,
                event_id,
                tenant_id,
                channel,
                success,
                external_id,
                message,
                attempted_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            attempt.analysisResultId,
            attempt.eventId,
            attempt.tenantId,
            attempt.channel,
            attempt.success,
            attempt.externalId,
            attempt.message,
            Timestamp.from(attempt.attemptedAt),
        )
    }

    override fun findRecent(query: DeliveryAttemptQuery): List<DeliveryAttemptRecord> {
        val limit = query.limit.coerceIn(1, 200)
        val args = mutableListOf<Any>()
        val sql =
            buildString {
                append(
                    """
                    select
                        id,
                        analysis_result_id,
                        event_id,
                        tenant_id,
                        channel,
                        success,
                        external_id,
                        message,
                        attempted_at
                    from delivery_attempt
                    where 1 = 1
                    """.trimIndent(),
                )

                query.eventId?.let {
                    append(" and event_id = ?")
                    args += it
                }
                query.tenantId?.let {
                    append(" and tenant_id = ?")
                    args += it
                }
                query.channel?.let {
                    append(" and channel = ?")
                    args += it
                }
                query.success?.let {
                    append(" and success = ?")
                    args += it
                }

                query.cursor?.let {
                    append(" and (attempted_at < ? or (attempted_at = ? and id < ?))")
                    args += Timestamp.from(it.attemptedAt)
                    args += Timestamp.from(it.attemptedAt)
                    args += it.id
                }

                append(" order by attempted_at desc, id desc limit ?")
                args += limit
            }

        return jdbcTemplate.query(sql, { rs, _ -> rs.toDeliveryAttemptRecord() }, *args.toTypedArray())
    }

    private fun ResultSet.toDeliveryAttemptRecord(): DeliveryAttemptRecord =
        DeliveryAttemptRecord(
            id = getLong("id"),
            analysisResultId = UUID.fromString(getString("analysis_result_id")),
            eventId = UUID.fromString(getString("event_id")),
            tenantId = getString("tenant_id"),
            channel = getString("channel"),
            success = getBoolean("success"),
            externalId = getString("external_id"),
            message = getString("message"),
            attemptedAt = getTimestamp("attempted_at").toInstant(),
        )
}
