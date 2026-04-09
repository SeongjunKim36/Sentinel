package io.github.seongjunkim36.sentinel.evaluation

import io.github.seongjunkim36.sentinel.delivery.DeliveryProperties
import io.github.seongjunkim36.sentinel.shared.AnalysisResult
import io.github.seongjunkim36.sentinel.shared.RoutingDecision
import io.github.seongjunkim36.sentinel.shared.RoutingPriority
import io.github.seongjunkim36.sentinel.shared.Severity
import org.springframework.stereotype.Service

@Service
class EvaluationService(
    private val deliveryProperties: DeliveryProperties,
) {
    fun evaluate(result: AnalysisResult): AnalysisResult {
        val priority = when (result.severity) {
            Severity.CRITICAL,
            Severity.HIGH,
            -> RoutingPriority.IMMEDIATE

            Severity.MEDIUM -> RoutingPriority.BATCHED
            Severity.LOW,
            Severity.INFO,
            -> RoutingPriority.DIGEST
        }
        val targetChannels =
            if (deliveryProperties.defaultChannels.isNotEmpty()) {
                deliveryProperties.defaultChannels
            } else {
                result.routing.channels
            }

        return result.copy(
            confidence = result.confidence.coerceAtLeast(0.5),
            routing = RoutingDecision(
                channels = targetChannels,
                priority = priority,
            ),
        )
    }
}
