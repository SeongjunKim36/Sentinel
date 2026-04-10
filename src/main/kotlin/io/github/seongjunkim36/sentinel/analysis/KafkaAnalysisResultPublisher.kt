package io.github.seongjunkim36.sentinel.analysis

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
class KafkaAnalysisResultPublisher(
    @Qualifier("analysisResultKafkaTemplate")
    private val kafkaTemplate: KafkaTemplate<String, AnalysisResult>,
) : AnalysisResultPublisher {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun publish(result: AnalysisResult) {
        val producerRecord = ProducerRecord(SentinelTopics.ANALYSIS_RESULTS, result.tenantId, result)
        result.traceId?.let { traceId ->
            producerRecord.headers().add("x-sentinel-trace-id", traceId.toByteArray(StandardCharsets.UTF_8))
        }

        val metadata = kafkaTemplate
            .send(producerRecord)
            .get(10, TimeUnit.SECONDS)

        logger.info(
            "Published analysis result to Kafka: topic={}, partition={}, offset={}, eventId={}, tenantId={}, severity={}, traceId={}",
            SentinelTopics.ANALYSIS_RESULTS,
            metadata.recordMetadata.partition(),
            metadata.recordMetadata.offset(),
            result.eventId,
            result.tenantId,
            result.severity,
            result.traceId,
        )
    }
}
