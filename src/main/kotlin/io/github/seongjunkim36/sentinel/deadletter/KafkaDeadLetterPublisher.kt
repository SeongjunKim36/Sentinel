package io.github.seongjunkim36.sentinel.deadletter

import io.github.seongjunkim36.sentinel.SentinelTopics
import io.github.seongjunkim36.sentinel.shared.DeadLetterEvent
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class KafkaDeadLetterPublisher(
    @Qualifier("deadLetterEventKafkaTemplate")
    private val kafkaTemplate: KafkaTemplate<String, DeadLetterEvent>,
) : DeadLetterPublisher {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun publish(event: DeadLetterEvent) {
        val producerRecord = ProducerRecord(SentinelTopics.DEAD_LETTER, event.tenantId, event)
        event.traceId?.let { traceId ->
            producerRecord.headers().add("x-sentinel-trace-id", traceId.toByteArray(StandardCharsets.UTF_8))
        }
        val metadata = kafkaTemplate.send(producerRecord).get(10, TimeUnit.SECONDS)

        logger.info(
            "Published dead-letter event: topic={}, partition={}, offset={}, deadLetterId={}, eventId={}, tenantId={}, stage={}, traceId={}",
            SentinelTopics.DEAD_LETTER,
            metadata.recordMetadata.partition(),
            metadata.recordMetadata.offset(),
            event.id,
            event.eventId,
            event.tenantId,
            event.sourceStage,
            event.traceId,
        )
    }
}
