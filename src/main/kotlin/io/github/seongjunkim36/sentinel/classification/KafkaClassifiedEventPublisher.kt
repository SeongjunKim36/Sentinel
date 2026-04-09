package io.github.seongjunkim36.sentinel.classification

import io.github.seongjunkim36.sentinel.SentinelTopics
import io.github.seongjunkim36.sentinel.shared.ClassifiedEvent
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class KafkaClassifiedEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, ClassifiedEvent>,
) : ClassifiedEventPublisher {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun publish(classifiedEvent: ClassifiedEvent) {
        val result = kafkaTemplate
            .send(SentinelTopics.CLASSIFIED_EVENTS, classifiedEvent.event.tenantId, classifiedEvent)
            .get(10, TimeUnit.SECONDS)

        logger.info(
            "Published classified event to Kafka: topic={}, partition={}, offset={}, eventId={}, tenantId={}, category={}",
            SentinelTopics.CLASSIFIED_EVENTS,
            result.recordMetadata.partition(),
            result.recordMetadata.offset(),
            classifiedEvent.event.id,
            classifiedEvent.event.tenantId,
            classifiedEvent.category,
        )
    }
}
