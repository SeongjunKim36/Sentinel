package io.github.seongjunkim36.sentinel.shared

import java.time.Instant
import java.util.UUID

data class DeadLetterEvent(
    val id: UUID,
    val sourceStage: String,
    val sourceTopic: String,
    val tenantId: String,
    val eventId: UUID,
    val traceId: String? = null,
    val channel: String? = null,
    val reason: String,
    val payloadType: DeadLetterPayloadType,
    val payload: String,
    val createdAt: Instant,
)

enum class DeadLetterPayloadType {
    ANALYSIS_RESULT,
}
