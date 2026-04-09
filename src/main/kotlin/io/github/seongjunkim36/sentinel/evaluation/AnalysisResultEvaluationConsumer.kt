package io.github.seongjunkim36.sentinel.evaluation

import io.github.seongjunkim36.sentinel.SentinelTopics
import io.github.seongjunkim36.sentinel.shared.AnalysisResult
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class AnalysisResultEvaluationConsumer(
    private val evaluationService: EvaluationService,
    private val routedResultPublisher: RoutedResultPublisher,
) {
    @KafkaListener(
        id = "analysis-result-evaluation-consumer",
        topics = [SentinelTopics.ANALYSIS_RESULTS],
        groupId = "sentinel-evaluation-v1",
        containerFactory = "analysisResultKafkaListenerContainerFactory",
    )
    fun consume(result: AnalysisResult) {
        routedResultPublisher.publish(evaluationService.evaluate(result))
    }
}
