package io.github.seongjunkim36.sentinel.ingestion.application

import io.github.seongjunkim36.sentinel.SentinelTopics
import io.github.seongjunkim36.sentinel.shared.Event
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class KafkaRawEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, Event>,
) : RawEventPublisher {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun publish(event: Event) {
        val producerRecord = ProducerRecord(SentinelTopics.RAW_EVENTS, event.tenantId, event)
        event.metadata.traceId?.let { traceId ->
            producerRecord.headers().add("x-sentinel-trace-id", traceId.toByteArray(StandardCharsets.UTF_8))
        }

        val result = kafkaTemplate
            .send(producerRecord)
            .get(10, TimeUnit.SECONDS)

        logger.info(
            "Published raw event to Kafka: topic={}, partition={}, offset={}, sourceType={}, sourceId={}, tenantId={}, traceId={}",
            SentinelTopics.RAW_EVENTS,
            result.recordMetadata.partition(),
            result.recordMetadata.offset(),
            event.sourceType,
            event.sourceId,
            event.tenantId,
            event.metadata.traceId,
        )
    }
}
