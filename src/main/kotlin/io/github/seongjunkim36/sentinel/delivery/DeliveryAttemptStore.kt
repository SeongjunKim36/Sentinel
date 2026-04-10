package io.github.seongjunkim36.sentinel.delivery

import io.github.seongjunkim36.sentinel.shared.AnalysisResult
import io.github.seongjunkim36.sentinel.shared.DeliveryResult
import java.time.Instant
import java.util.UUID

interface DeliveryAttemptStore {
    fun record(attempt: DeliveryAttemptWrite)

    fun findRecent(query: DeliveryAttemptQuery = DeliveryAttemptQuery()): List<DeliveryAttemptRecord>
}

data class DeliveryAttemptWrite(
    val analysisResultId: UUID,
    val eventId: UUID,
    val tenantId: String,
    val channel: String,
    val success: Boolean,
    val externalId: String?,
    val message: String?,
    val attemptedAt: Instant = Instant.now(),
) {
    companion object {
        fun from(
            analysisResult: AnalysisResult,
            channel: String,
            deliveryResult: DeliveryResult,
        ): DeliveryAttemptWrite =
            DeliveryAttemptWrite(
                analysisResultId = analysisResult.id,
                eventId = analysisResult.eventId,
                tenantId = analysisResult.tenantId,
                channel = channel,
                success = deliveryResult.success,
                externalId = deliveryResult.externalId,
                message = deliveryResult.message,
            )
    }
}

data class DeliveryAttemptRecord(
    val id: Long,
    val analysisResultId: UUID,
    val eventId: UUID,
    val tenantId: String,
    val channel: String,
    val success: Boolean,
    val externalId: String?,
    val message: String?,
    val attemptedAt: Instant,
)

data class DeliveryAttemptQuery(
    val eventId: UUID? = null,
    val tenantId: String? = null,
    val channel: String? = null,
    val success: Boolean? = null,
    val limit: Int = 50,
    val cursor: DeliveryAttemptCursor? = null,
)

data class DeliveryAttemptCursor(
    val attemptedAt: Instant,
    val id: Long,
)
