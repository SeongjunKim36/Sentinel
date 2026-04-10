package io.github.seongjunkim36.sentinel.deadletter

import io.github.seongjunkim36.sentinel.SentinelTopics
import io.github.seongjunkim36.sentinel.shared.DeadLetterEvent
import java.util.concurrent.TimeUnit
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
        val metadata = kafkaTemplate.send(SentinelTopics.DEAD_LETTER, event.tenantId, event).get(10, TimeUnit.SECONDS)

        logger.info(
            "Published dead-letter event: topic={}, partition={}, offset={}, deadLetterId={}, eventId={}, tenantId={}, stage={}",
            SentinelTopics.DEAD_LETTER,
            metadata.recordMetadata.partition(),
            metadata.recordMetadata.offset(),
            event.id,
            event.eventId,
            event.tenantId,
            event.sourceStage,
        )
    }
}
