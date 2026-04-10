package io.github.seongjunkim36.sentinel.evaluation

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
class KafkaRoutedResultPublisher(
    @Qualifier("routedResultKafkaTemplate")
    private val kafkaTemplate: KafkaTemplate<String, AnalysisResult>,
) : RoutedResultPublisher {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun publish(result: AnalysisResult) {
        val producerRecord = ProducerRecord(SentinelTopics.ROUTED_RESULTS, result.tenantId, result)
        result.traceId?.let { traceId ->
            producerRecord.headers().add("x-sentinel-trace-id", traceId.toByteArray(StandardCharsets.UTF_8))
        }

        val metadata = kafkaTemplate
            .send(producerRecord)
            .get(10, TimeUnit.SECONDS)

        logger.info(
            "Published routed result to Kafka: topic={}, partition={}, offset={}, eventId={}, tenantId={}, priority={}, traceId={}",
            SentinelTopics.ROUTED_RESULTS,
            metadata.recordMetadata.partition(),
            metadata.recordMetadata.offset(),
            result.eventId,
            result.tenantId,
            result.routing.priority,
            result.traceId,
        )
    }
}
