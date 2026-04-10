package io.github.seongjunkim36.sentinel.delivery

import io.github.seongjunkim36.sentinel.SentinelTopics
import io.github.seongjunkim36.sentinel.deadletter.DeadLetterRecorder
import io.github.seongjunkim36.sentinel.observability.PipelineMetrics
import io.github.seongjunkim36.sentinel.shared.AnalysisResult
import io.github.seongjunkim36.sentinel.shared.DeliveryResult
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class RoutedResultDeliveryConsumer(
    private val outputPluginRegistry: OutputPluginRegistry,
    private val deliveryAttemptStore: DeliveryAttemptStore,
    private val deadLetterRecorder: DeadLetterRecorder,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        id = "routed-result-delivery-consumer",
        topics = [SentinelTopics.ROUTED_RESULTS],
        groupId = "sentinel-delivery-v1",
        containerFactory = "analysisResultKafkaListenerContainerFactory",
    )
    fun consume(result: AnalysisResult) {
        val channels = result.routing.channels.distinct()
        PipelineMetrics.recordDeliveryFanout(channels.size)

        channels.forEach { channel ->
            val plugin = outputPluginRegistry.find(channel)

            if (plugin == null) {
                val message = "No output plugin registered for channel=$channel"
                logger.warn(message)
                deadLetterRecorder.recordDeliveryFailure(
                    result = result,
                    channel = channel,
                    reason = message,
                )
                deliveryAttemptStore.record(
                    DeliveryAttemptWrite.from(
                        analysisResult = result,
                        channel = channel,
                        deliveryResult = DeliveryResult(success = false, message = message),
                    ),
                )
                PipelineMetrics.recordDeliveryAttempt(
                    tenantId = result.tenantId,
                    channel = channel,
                    category = result.category,
                    success = false,
                    failureType = "plugin_missing",
                )
                return@forEach
            }

            var failureType = "none"
            val deliveryResult =
                try {
                    plugin.send(result)
                } catch (exception: Exception) {
                    logger.error(
                        "Delivery plugin threw an exception: eventId={}, channel={}",
                        result.eventId,
                        channel,
                        exception,
                    )
                    failureType = "plugin_exception"
                    DeliveryResult(success = false, message = exception.message ?: exception.javaClass.simpleName)
                }

            if (!deliveryResult.success && failureType == "none") {
                failureType = "delivery_failed"
            }

            deliveryAttemptStore.record(
                DeliveryAttemptWrite.from(
                    analysisResult = result,
                    channel = channel,
                    deliveryResult = deliveryResult,
                ),
            )

            if (!deliveryResult.success) {
                deadLetterRecorder.recordDeliveryFailure(
                    result = result,
                    channel = channel,
                    reason = deliveryResult.message ?: "Delivery plugin returned failure",
                )
            }
            PipelineMetrics.recordDeliveryAttempt(
                tenantId = result.tenantId,
                channel = channel,
                category = result.category,
                success = deliveryResult.success,
                failureType = failureType,
            )

            logger.info(
                "Delivery attempted: eventId={}, channel={}, success={}, message={}",
                result.eventId,
                channel,
                deliveryResult.success,
                deliveryResult.message,
            )
        }
    }
}
