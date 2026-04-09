package io.github.seongjunkim36.sentinel.ingestion.application

import io.github.seongjunkim36.sentinel.SentinelTopics
import io.github.seongjunkim36.sentinel.shared.Event
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class KafkaRawEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, Event>,
) : RawEventPublisher {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun publish(event: Event) {
        val result = kafkaTemplate
            .send(SentinelTopics.RAW_EVENTS, event.tenantId, event)
            .get(10, TimeUnit.SECONDS)

        logger.info(
            "Published raw event to Kafka: topic={}, partition={}, offset={}, sourceType={}, sourceId={}, tenantId={}",
            SentinelTopics.RAW_EVENTS,
            result.recordMetadata.partition(),
            result.recordMetadata.offset(),
            event.sourceType,
            event.sourceId,
            event.tenantId,
        )
    }
}
