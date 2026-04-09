package io.github.seongjunkim36.sentinel.evaluation

import io.github.seongjunkim36.sentinel.delivery.DeliveryProperties
import io.github.seongjunkim36.sentinel.delivery.TelegramDeliveryProperties
import io.github.seongjunkim36.sentinel.shared.AnalysisDetail
import io.github.seongjunkim36.sentinel.shared.AnalysisResult
import io.github.seongjunkim36.sentinel.shared.LlmMetadata
import io.github.seongjunkim36.sentinel.shared.ResultCategories
import io.github.seongjunkim36.sentinel.shared.RoutingDecision
import io.github.seongjunkim36.sentinel.shared.RoutingPriority
import io.github.seongjunkim36.sentinel.shared.Severity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EvaluationServiceTests {
    @Test
    fun `overrides routing channels with configured defaults`() {
        val evaluationService =
            EvaluationService(
                DeliveryProperties(
                    defaultChannels = listOf("telegram"),
                    telegram = TelegramDeliveryProperties(),
                ),
            )

        val evaluated =
            evaluationService.evaluate(
                AnalysisResult(
                    eventId = java.util.UUID.randomUUID(),
                    tenantId = "tenant-alpha",
                    category = "error",
                    severity = Severity.HIGH,
                    confidence = 0.32,
                    summary = "Checkout outage",
                    detail = AnalysisDetail(analysis = "Payment flow is failing"),
                    llmMetadata = LlmMetadata(model = "stub", promptVersion = "v1"),
                    routing = RoutingDecision(channels = listOf("slack"), priority = RoutingPriority.DIGEST),
                ),
            )

        assertThat(evaluated.confidence).isEqualTo(0.5)
        assertThat(evaluated.routing.channels).containsExactly("telegram")
        assertThat(evaluated.routing.priority).isEqualTo(RoutingPriority.IMMEDIATE)
    }

    @Test
    fun `keeps explicit failure routing channels for analysis failure results`() {
        val evaluationService =
            EvaluationService(
                DeliveryProperties(
                    defaultChannels = listOf("slack"),
                    telegram = TelegramDeliveryProperties(),
                ),
            )

        val evaluated =
            evaluationService.evaluate(
                AnalysisResult(
                    eventId = java.util.UUID.randomUUID(),
                    tenantId = "tenant-alpha",
                    category = ResultCategories.ANALYSIS_FAILURE,
                    severity = Severity.CRITICAL,
                    confidence = 1.0,
                    summary = "Analysis pipeline failure",
                    detail = AnalysisDetail(analysis = "LLM analysis failed after 3 attempt(s)"),
                    llmMetadata = LlmMetadata(model = "analysis-fallback", promptVersion = "failure-routing-v1"),
                    routing = RoutingDecision(channels = listOf("telegram"), priority = RoutingPriority.IMMEDIATE),
                ),
            )

        assertThat(evaluated.routing.channels).containsExactly("telegram")
        assertThat(evaluated.routing.priority).isEqualTo(RoutingPriority.IMMEDIATE)
        assertThat(evaluated.confidence).isEqualTo(1.0)
    }
}
