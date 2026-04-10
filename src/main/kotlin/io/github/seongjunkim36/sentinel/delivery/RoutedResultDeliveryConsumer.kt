package io.github.seongjunkim36.sentinel.delivery

import io.github.seongjunkim36.sentinel.SentinelTopics
import io.github.seongjunkim36.sentinel.deadletter.DeadLetterRecorder
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
        result.routing.channels.distinct().forEach { channel ->
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
                return@forEach
            }

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
                    DeliveryResult(success = false, message = exception.message ?: exception.javaClass.simpleName)
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
