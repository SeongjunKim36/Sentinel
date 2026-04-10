package io.github.seongjunkim36.sentinel.deadletter

import io.github.seongjunkim36.sentinel.SentinelTopics
import io.github.seongjunkim36.sentinel.shared.AnalysisResult
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class KafkaDeadLetterReplayPublisher(
    @Qualifier("routedResultKafkaTemplate")
    private val kafkaTemplate: KafkaTemplate<String, AnalysisResult>,
) : DeadLetterReplayPublisher {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun publishAnalysisResult(result: AnalysisResult) {
        val metadata = kafkaTemplate.send(SentinelTopics.ROUTED_RESULTS, result.tenantId, result).get(10, TimeUnit.SECONDS)

        logger.info(
            "Replayed dead-letter payload to routed results topic: topic={}, partition={}, offset={}, eventId={}, tenantId={}",
            SentinelTopics.ROUTED_RESULTS,
            metadata.recordMetadata.partition(),
            metadata.recordMetadata.offset(),
            result.eventId,
            result.tenantId,
        )
    }
}
