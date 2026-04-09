package io.github.seongjunkim36.sentinel.delivery

import io.github.seongjunkim36.sentinel.SentinelTopics
import io.github.seongjunkim36.sentinel.shared.AnalysisResult
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class RoutedResultDeliveryConsumer(
    private val outputPluginRegistry: OutputPluginRegistry,
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
                logger.warn("No output plugin registered for channel={}", channel)
                return@forEach
            }

            val deliveryResult = plugin.send(result)

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
