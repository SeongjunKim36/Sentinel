package io.github.seongjunkim36.sentinel.deadletter

import io.github.seongjunkim36.sentinel.SentinelTopics
import io.github.seongjunkim36.sentinel.shared.AnalysisResult
import io.github.seongjunkim36.sentinel.shared.DeadLetterEvent
import io.github.seongjunkim36.sentinel.shared.DeadLetterPayloadType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper

@Service
class DeadLetterService(
    private val deadLetterStore: DeadLetterStore,
    private val deadLetterPublisher: DeadLetterPublisher,
    private val jsonMapper: JsonMapper,
) : DeadLetterRecorder {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun recordDeliveryFailure(
        result: AnalysisResult,
        channel: String,
        reason: String,
    ) {
        val payload = jsonMapper.writeValueAsString(result)
        val persisted =
            deadLetterStore.save(
                DeadLetterWrite(
                    sourceStage = "delivery",
                    sourceTopic = SentinelTopics.ROUTED_RESULTS,
                    tenantId = result.tenantId,
                    eventId = result.eventId,
                    channel = channel,
                    reason = reason,
                    payloadType = DeadLetterPayloadType.ANALYSIS_RESULT,
                    payload = payload,
                ),
            )

        try {
            deadLetterPublisher.publish(
                DeadLetterEvent(
                    id = persisted.id,
                    sourceStage = persisted.sourceStage,
                    sourceTopic = persisted.sourceTopic,
                    tenantId = persisted.tenantId,
                    eventId = persisted.eventId,
                    traceId = result.traceId,
                    channel = persisted.channel,
                    reason = persisted.reason,
                    payloadType = persisted.payloadType,
                    payload = persisted.payload,
                    createdAt = persisted.createdAt,
                ),
            )
        } catch (exception: Exception) {
            logger.error(
                "Dead-letter Kafka publish failed: deadLetterId={}, eventId={}, tenantId={}, stage={}",
                persisted.id,
                persisted.eventId,
                persisted.tenantId,
                persisted.sourceStage,
                exception,
            )
        }
    }
}
