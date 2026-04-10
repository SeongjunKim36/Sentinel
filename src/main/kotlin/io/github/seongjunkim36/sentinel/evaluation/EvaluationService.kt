package io.github.seongjunkim36.sentinel.evaluation

import io.github.seongjunkim36.sentinel.delivery.DeliveryProperties
import io.github.seongjunkim36.sentinel.shared.AnalysisResult
import io.github.seongjunkim36.sentinel.shared.RoutingDecision
import org.springframework.stereotype.Service

@Service
class EvaluationService(
    private val deliveryProperties: DeliveryProperties,
    private val evaluationProperties: EvaluationProperties,
) {
    fun evaluate(result: AnalysisResult): AnalysisResult {
        val routing = evaluationProperties.routing
        val severityPolicy = routing.severityPolicies[result.severity]
        val categoryPolicy = routing.categoryPolicies[result.category]

        val minimumConfidence =
            if (categoryPolicy?.skipMinimumConfidence == true) {
                0.0
            } else {
                evaluationProperties.minimumConfidence.coerceIn(0.0, 1.0)
            }
        val priority = categoryPolicy?.priority ?: severityPolicy?.priority ?: result.routing.priority
        val targetChannels = resolveTargetChannels(result = result, categoryPolicy = categoryPolicy, severityPolicy = severityPolicy)

        return result.copy(
            confidence = result.confidence.coerceAtLeast(minimumConfidence),
            routing = RoutingDecision(
                channels = targetChannels,
                priority = priority,
            ),
        )
    }

    private fun resolveTargetChannels(
        result: AnalysisResult,
        categoryPolicy: EvaluationRoutingPolicy?,
        severityPolicy: EvaluationRoutingPolicy?,
    ): List<String> {
        if (categoryPolicy?.preferResultChannels == true && result.routing.channels.isNotEmpty()) {
            return result.routing.channels.distinct()
        }

        val routingDefaults =
            evaluationProperties.routing.defaultChannels
                .ifEmpty { deliveryProperties.defaultChannels }

        return firstNonEmpty(
            categoryPolicy?.channels.orEmpty(),
            severityPolicy?.channels.orEmpty(),
            routingDefaults,
            result.routing.channels,
        ).distinct()
    }

    private fun firstNonEmpty(vararg candidates: List<String>): List<String> =
        candidates.firstOrNull { it.isNotEmpty() } ?: emptyList()
}
