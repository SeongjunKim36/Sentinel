package io.github.seongjunkim36.sentinel.analysis

import io.github.seongjunkim36.sentinel.shared.AnalysisResult
import io.github.seongjunkim36.sentinel.shared.ClassifiedEvent
import io.github.seongjunkim36.sentinel.shared.Event
import io.github.seongjunkim36.sentinel.shared.EventMetadata
import io.github.seongjunkim36.sentinel.shared.ResultCategories
import io.github.seongjunkim36.sentinel.shared.Severity
import java.time.Duration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ClassifiedEventAnalysisConsumerTests {
    @Test
    fun `publishes failure analysis result when retries are exhausted`() {
        val analysisService =
            AnalysisService(
                llmClient =
                    object : LlmClient {
                        override fun analyze(classifiedEvent: ClassifiedEvent): LlmAnalysisResponse {
                            throw IllegalStateException("LLM provider unavailable")
                        }
                    },
                analysisProperties =
                    AnalysisProperties(
                        retry =
                            AnalysisRetryProperties(
                                maxAttempts = 2,
                                initialBackoff = Duration.ZERO,
                                multiplier = 2.0,
                                maxBackoff = Duration.ZERO,
                            ),
                        failureRouting = AnalysisFailureRoutingProperties(channels = listOf("telegram")),
                    ),
            )
        val publisher = RecordingAnalysisResultPublisher()
        val consumer =
            ClassifiedEventAnalysisConsumer(
                analysisService = analysisService,
                analysisResultPublisher = publisher,
            )

        consumer.consume(sampleClassifiedEvent())

        assertThat(publisher.publishedResults).hasSize(1)
        val result = publisher.publishedResults.single()
        assertThat(result.category).isEqualTo(ResultCategories.ANALYSIS_FAILURE)
        assertThat(result.severity).isEqualTo(Severity.CRITICAL)
        assertThat(result.routing.channels).containsExactly("telegram")
        assertThat(result.summary).contains("Analysis pipeline failure")
    }

    private fun sampleClassifiedEvent(): ClassifiedEvent =
        ClassifiedEvent(
            event =
                Event(
                    sourceType = "sentry",
                    sourceId = "evt-analysis-fail",
                    tenantId = "tenant-alpha",
                    payload = mapOf("message" to "Checkout timeout"),
                    metadata = EventMetadata(sourceVersion = "v1"),
                ),
            category = "error",
            analyzable = true,
            filtered = false,
        )

    private class RecordingAnalysisResultPublisher : AnalysisResultPublisher {
        val publishedResults = mutableListOf<AnalysisResult>()

        override fun publish(result: AnalysisResult) {
            publishedResults += result
        }
    }
}
