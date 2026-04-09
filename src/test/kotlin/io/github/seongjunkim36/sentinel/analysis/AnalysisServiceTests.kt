package io.github.seongjunkim36.sentinel.analysis

import io.github.seongjunkim36.sentinel.shared.AnalysisResult
import io.github.seongjunkim36.sentinel.shared.ClassifiedEvent
import io.github.seongjunkim36.sentinel.shared.Event
import io.github.seongjunkim36.sentinel.shared.EventMetadata
import io.github.seongjunkim36.sentinel.shared.ResultCategories
import io.github.seongjunkim36.sentinel.shared.RoutingPriority
import io.github.seongjunkim36.sentinel.shared.Severity
import io.github.seongjunkim36.sentinel.shared.TokenUsage
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
                analysisProperties = AnalysisProperties(),
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

    @Test
    fun `retries failed analysis and succeeds before max attempts`() {
        val invocationCount = AtomicInteger(0)
        val analysisService =
            AnalysisService(
                llmClient =
                    object : LlmClient {
                        override fun analyze(classifiedEvent: ClassifiedEvent): LlmAnalysisResponse {
                            if (invocationCount.incrementAndGet() < 3) {
                                throw IllegalStateException("Temporary LLM outage")
                            }

                            return LlmAnalysisResponse(
                                summary = "Recovered after retry",
                                analysis = "Succeeded on third attempt.",
                                actionItems = listOf("Monitor provider error rate"),
                                severity = Severity.MEDIUM,
                                confidence = 0.7,
                                model = "stub-llm",
                                promptVersion = "test-v1",
                            )
                        }
                    },
                analysisProperties =
                    AnalysisProperties(
                        retry =
                            AnalysisRetryProperties(
                                maxAttempts = 3,
                                initialBackoff = Duration.ZERO,
                                multiplier = 2.0,
                                maxBackoff = Duration.ZERO,
                            ),
                    ),
            )

        val result = analysisService.analyze(sampleClassifiedEvent())

        assertThat(invocationCount.get()).isEqualTo(3)
        assertThat(result.summary).isEqualTo("Recovered after retry")
    }

    @Test
    fun `throws retries exhausted exception when llm keeps failing`() {
        val invocationCount = AtomicInteger(0)
        val analysisService =
            AnalysisService(
                llmClient =
                    object : LlmClient {
                        override fun analyze(classifiedEvent: ClassifiedEvent): LlmAnalysisResponse {
                            invocationCount.incrementAndGet()
                            throw IllegalStateException("Provider timeout")
                        }
                    },
                analysisProperties =
                    AnalysisProperties(
                        retry =
                            AnalysisRetryProperties(
                                maxAttempts = 3,
                                initialBackoff = Duration.ZERO,
                                multiplier = 2.0,
                                maxBackoff = Duration.ZERO,
                            ),
                    ),
            )

        assertThatThrownBy { analysisService.analyze(sampleClassifiedEvent()) }
            .isInstanceOf(AnalysisRetriesExhaustedException::class.java)
            .hasMessageContaining("Analysis failed after 3 attempt(s)")

        assertThat(invocationCount.get()).isEqualTo(3)
    }

    @Test
    fun `builds fallback result routed to telegram when retries are exhausted`() {
        val analysisService =
            AnalysisService(
                llmClient =
                    object : LlmClient {
                        override fun analyze(classifiedEvent: ClassifiedEvent): LlmAnalysisResponse {
                            throw IllegalStateException("not used in this test")
                        }
                    },
                analysisProperties =
                    AnalysisProperties(
                        failureRouting = AnalysisFailureRoutingProperties(channels = listOf("telegram")),
                    ),
            )

        val classifiedEvent = sampleClassifiedEvent()
        val failureResult =
            analysisService.toFailureResult(
                classifiedEvent = classifiedEvent,
                exception =
                    AnalysisRetriesExhaustedException(
                        eventId = classifiedEvent.event.id,
                        tenantId = classifiedEvent.event.tenantId,
                        attempts = 3,
                        cause = IllegalStateException("Provider timeout"),
                    ),
            )

        assertThat(failureResult.category).isEqualTo(ResultCategories.ANALYSIS_FAILURE)
        assertThat(failureResult.severity).isEqualTo(Severity.CRITICAL)
        assertThat(failureResult.routing.channels).containsExactly("telegram")
        assertThat(failureResult.routing.priority).isEqualTo(RoutingPriority.IMMEDIATE)
        assertThat(failureResult.detail.analysis).contains("failed after 3 attempt(s)")
        assertThat(failureResult.summary).contains("Analysis pipeline failure")
    }

    private fun sampleClassifiedEvent(): ClassifiedEvent =
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
        )
}
