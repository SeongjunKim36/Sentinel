package io.github.seongjunkim36.sentinel.classification

import io.github.seongjunkim36.sentinel.SentinelTopics
import io.github.seongjunkim36.sentinel.shared.ClassifiedEvent
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class KafkaClassifiedEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, ClassifiedEvent>,
) : ClassifiedEventPublisher {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun publish(classifiedEvent: ClassifiedEvent) {
        val producerRecord =
            ProducerRecord(
                SentinelTopics.CLASSIFIED_EVENTS,
                classifiedEvent.event.tenantId,
                classifiedEvent,
            )
        classifiedEvent.event.metadata.traceId?.let { traceId ->
            producerRecord.headers().add("x-sentinel-trace-id", traceId.toByteArray(StandardCharsets.UTF_8))
        }

        val result = kafkaTemplate
            .send(producerRecord)
            .get(10, TimeUnit.SECONDS)

        logger.info(
            "Published classified event to Kafka: topic={}, partition={}, offset={}, eventId={}, tenantId={}, category={}, traceId={}",
            SentinelTopics.CLASSIFIED_EVENTS,
            result.recordMetadata.partition(),
            result.recordMetadata.offset(),
            classifiedEvent.event.id,
            classifiedEvent.event.tenantId,
            classifiedEvent.category,
            classifiedEvent.event.metadata.traceId,
        )
    }
}
