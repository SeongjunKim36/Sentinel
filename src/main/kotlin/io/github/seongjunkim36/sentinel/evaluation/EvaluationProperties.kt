package io.github.seongjunkim36.sentinel.evaluation

import io.github.seongjunkim36.sentinel.shared.ResultCategories
import io.github.seongjunkim36.sentinel.shared.RoutingPriority
import io.github.seongjunkim36.sentinel.shared.Severity
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "sentinel.evaluation")
data class EvaluationProperties(
    val minimumConfidence: Double = 0.5,
    val routing: EvaluationRoutingProperties = EvaluationRoutingProperties(),
)

data class EvaluationRoutingProperties(
    val defaultChannels: List<String> = emptyList(),
    val severityPolicies: Map<Severity, EvaluationRoutingPolicy> = defaultSeverityPolicies(),
    val categoryPolicies: Map<String, EvaluationRoutingPolicy> = defaultCategoryPolicies(),
) {
    companion object {
        private fun defaultSeverityPolicies(): Map<Severity, EvaluationRoutingPolicy> =
            mapOf(
                Severity.CRITICAL to EvaluationRoutingPolicy(priority = RoutingPriority.IMMEDIATE),
                Severity.HIGH to EvaluationRoutingPolicy(priority = RoutingPriority.IMMEDIATE),
                Severity.MEDIUM to EvaluationRoutingPolicy(priority = RoutingPriority.BATCHED),
                Severity.LOW to EvaluationRoutingPolicy(priority = RoutingPriority.DIGEST),
                Severity.INFO to EvaluationRoutingPolicy(priority = RoutingPriority.DIGEST),
            )

        private fun defaultCategoryPolicies(): Map<String, EvaluationRoutingPolicy> =
            mapOf(
                ResultCategories.ANALYSIS_FAILURE to
                    EvaluationRoutingPolicy(
                        priority = RoutingPriority.IMMEDIATE,
                        preferResultChannels = true,
                        skipMinimumConfidence = true,
                    ),
            )
    }
}

data class EvaluationRoutingPolicy(
    val channels: List<String> = emptyList(),
    val priority: RoutingPriority? = null,
    val preferResultChannels: Boolean = false,
    val skipMinimumConfidence: Boolean = false,
)
