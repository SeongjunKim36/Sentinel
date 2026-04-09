package io.github.seongjunkim36.sentinel.analysis

import io.github.seongjunkim36.sentinel.shared.AnalysisResult
import io.github.seongjunkim36.sentinel.shared.ClassifiedEvent
import io.github.seongjunkim36.sentinel.shared.Event
import io.github.seongjunkim36.sentinel.shared.EventMetadata
import io.github.seongjunkim36.sentinel.shared.Severity
import io.github.seongjunkim36.sentinel.shared.TokenUsage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AnalysisServiceTests {
    @Test
    fun `maps llm response into analysis result`() {
        val analysisService =
            AnalysisService(
                llmClient =
                    object : LlmClient {
                        override fun analyze(classifiedEvent: ClassifiedEvent): LlmAnalysisResponse =
                            LlmAnalysisResponse(
                                summary = "Checkout timeout requires investigation.",
                                analysis = "The checkout flow is failing with repeated timeout errors.",
                                actionItems = listOf("Inspect database latency"),
                                severity = Severity.HIGH,
                                confidence = 0.81,
                                model = "stub-llm",
                                promptVersion = "test-v1",
                                tokenUsage = TokenUsage(input = 10, output = 20),
                                costUsd = 0.12,
                                latencyMs = 450,
                            )
                    },
            )

        val result: AnalysisResult =
            analysisService.analyze(
                ClassifiedEvent(
                    event =
                        Event(
                            sourceType = "sentry",
                            sourceId = "evt-789",
                            tenantId = "tenant-alpha",
                            payload = mapOf("message" to "Checkout timeout"),
                            metadata = EventMetadata(sourceVersion = "v1"),
                        ),
                    category = "error",
                    analyzable = true,
                    filtered = false,
                ),
            )

        assertThat(result.summary).isEqualTo("Checkout timeout requires investigation.")
        assertThat(result.severity).isEqualTo(Severity.HIGH)
        assertThat(result.detail.actionItems).containsExactly("Inspect database latency")
        assertThat(result.llmMetadata.model).isEqualTo("stub-llm")
        assertThat(result.llmMetadata.promptVersion).isEqualTo("test-v1")
        assertThat(result.llmMetadata.costUsd).isEqualTo(0.12)
    }
}
