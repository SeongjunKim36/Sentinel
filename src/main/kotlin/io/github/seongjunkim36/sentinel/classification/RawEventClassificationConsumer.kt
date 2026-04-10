package io.github.seongjunkim36.sentinel.classification

import io.github.seongjunkim36.sentinel.SentinelTopics
import io.github.seongjunkim36.sentinel.observability.PipelineMetrics
import io.github.seongjunkim36.sentinel.shared.Event
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class RawEventClassificationConsumer(
    private val classificationService: ClassificationService,
    private val classifiedEventPublisher: ClassifiedEventPublisher,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        id = "raw-event-classification-consumer",
        topics = [SentinelTopics.RAW_EVENTS],
        groupId = "sentinel-classification-v1",
        containerFactory = "eventKafkaListenerContainerFactory",
    )
    fun consume(event: Event) {
        val classifiedEvent = classificationService.classify(event)
        PipelineMetrics.recordClassification(classifiedEvent)

        if (classifiedEvent.filtered || !classifiedEvent.analyzable) {
            logger.info(
                "Skipped classified event publication: eventId={}, tenantId={}, filtered={}, analyzable={}, filterReason={}",
                event.id,
                event.tenantId,
                classifiedEvent.filtered,
                classifiedEvent.analyzable,
                classifiedEvent.filterReason,
            )
            return
        }

        classifiedEventPublisher.publish(classifiedEvent)
    }
}
