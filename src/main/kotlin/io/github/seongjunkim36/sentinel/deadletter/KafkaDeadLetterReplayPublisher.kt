package io.github.seongjunkim36.sentinel.deadletter

import io.github.seongjunkim36.sentinel.SentinelTopics
import io.github.seongjunkim36.sentinel.shared.AnalysisResult
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import org.apache.kafka.clients.producer.ProducerRecord
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
        val producerRecord = ProducerRecord(SentinelTopics.ROUTED_RESULTS, result.tenantId, result)
        result.traceId?.let { traceId ->
            producerRecord.headers().add("x-sentinel-trace-id", traceId.toByteArray(StandardCharsets.UTF_8))
        }
        val metadata = kafkaTemplate.send(producerRecord).get(10, TimeUnit.SECONDS)

        logger.info(
            "Replayed dead-letter payload to routed results topic: topic={}, partition={}, offset={}, eventId={}, tenantId={}, traceId={}",
            SentinelTopics.ROUTED_RESULTS,
            metadata.recordMetadata.partition(),
            metadata.recordMetadata.offset(),
            result.eventId,
            result.tenantId,
            result.traceId,
        )
    }
}
