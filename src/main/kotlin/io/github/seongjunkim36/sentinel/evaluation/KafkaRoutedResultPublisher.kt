package io.github.seongjunkim36.sentinel.evaluation

import io.github.seongjunkim36.sentinel.SentinelTopics
import io.github.seongjunkim36.sentinel.shared.AnalysisResult
import java.util.concurrent.TimeUnit
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
        val metadata = kafkaTemplate
            .send(SentinelTopics.ROUTED_RESULTS, result.tenantId, result)
            .get(10, TimeUnit.SECONDS)

        logger.info(
            "Published routed result to Kafka: topic={}, partition={}, offset={}, eventId={}, tenantId={}, priority={}",
            SentinelTopics.ROUTED_RESULTS,
            metadata.recordMetadata.partition(),
            metadata.recordMetadata.offset(),
            result.eventId,
            result.tenantId,
            result.routing.priority,
        )
    }
}
