package io.github.seongjunkim36.sentinel.analysis

import io.github.seongjunkim36.sentinel.SentinelTopics
import io.github.seongjunkim36.sentinel.shared.AnalysisResult
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class KafkaAnalysisResultPublisher(
    private val kafkaTemplate: KafkaTemplate<String, AnalysisResult>,
) : AnalysisResultPublisher {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun publish(result: AnalysisResult) {
        val metadata = kafkaTemplate
            .send(SentinelTopics.ANALYSIS_RESULTS, result.tenantId, result)
            .get(10, TimeUnit.SECONDS)

        logger.info(
            "Published analysis result to Kafka: topic={}, partition={}, offset={}, eventId={}, tenantId={}, severity={}",
            SentinelTopics.ANALYSIS_RESULTS,
            metadata.recordMetadata.partition(),
            metadata.recordMetadata.offset(),
            result.eventId,
            result.tenantId,
            result.severity,
        )
    }
}
