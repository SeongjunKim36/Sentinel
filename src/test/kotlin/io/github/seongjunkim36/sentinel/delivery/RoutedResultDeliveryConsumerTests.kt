package io.github.seongjunkim36.sentinel.delivery

import io.github.seongjunkim36.sentinel.shared.AnalysisDetail
import io.github.seongjunkim36.sentinel.shared.AnalysisResult
import io.github.seongjunkim36.sentinel.shared.DeliveryResult
import io.github.seongjunkim36.sentinel.shared.LlmMetadata
import io.github.seongjunkim36.sentinel.shared.OutputPlugin
import io.github.seongjunkim36.sentinel.shared.RoutingDecision
import io.github.seongjunkim36.sentinel.shared.Severity
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RoutedResultDeliveryConsumerTests {
    @Test
    fun `dispatches routed results to matching plugin`() {
        val telegramPlugin = RecordingOutputPlugin(type = "telegram")
        val attemptStore = RecordingDeliveryAttemptStore()
        val consumer =
            RoutedResultDeliveryConsumer(
                outputPluginRegistry = OutputPluginRegistry(listOf(telegramPlugin)),
                deliveryAttemptStore = attemptStore,
            )

        consumer.consume(
            AnalysisResult(
                eventId = UUID.randomUUID(),
                tenantId = "tenant-alpha",
                category = "error",
                severity = Severity.HIGH,
                confidence = 0.72,
                summary = "Checkout timeout",
                detail = AnalysisDetail(analysis = "Payment flow is degraded"),
                llmMetadata = LlmMetadata(model = "stub", promptVersion = "v1"),
                routing = RoutingDecision(channels = listOf("telegram")),
            ),
        )

        assertThat(telegramPlugin.sentResults).hasSize(1)
        assertThat(telegramPlugin.sentResults.single().routing.channels).containsExactly("telegram")
        assertThat(attemptStore.recordedAttempts).hasSize(1)
        assertThat(attemptStore.recordedAttempts.single().channel).isEqualTo("telegram")
        assertThat(attemptStore.recordedAttempts.single().success).isTrue()
    }

    @Test
    fun `records failed attempt when output plugin is missing`() {
        val attemptStore = RecordingDeliveryAttemptStore()
        val consumer =
            RoutedResultDeliveryConsumer(
                outputPluginRegistry = OutputPluginRegistry(emptyList()),
                deliveryAttemptStore = attemptStore,
            )

        consumer.consume(
            AnalysisResult(
                eventId = UUID.randomUUID(),
                tenantId = "tenant-alpha",
                category = "error",
                severity = Severity.HIGH,
                confidence = 0.72,
                summary = "Checkout timeout",
                detail = AnalysisDetail(analysis = "Payment flow is degraded"),
                llmMetadata = LlmMetadata(model = "stub", promptVersion = "v1"),
                routing = RoutingDecision(channels = listOf("telegram")),
            ),
        )

        assertThat(attemptStore.recordedAttempts).hasSize(1)
        val attempt = attemptStore.recordedAttempts.single()
        assertThat(attempt.channel).isEqualTo("telegram")
        assertThat(attempt.success).isFalse()
        assertThat(attempt.message).contains("No output plugin registered")
    }

    private class RecordingOutputPlugin(
        override val type: String,
    ) : OutputPlugin {
        val sentResults = mutableListOf<AnalysisResult>()

        override fun send(result: AnalysisResult): DeliveryResult {
            sentResults += result
            return DeliveryResult(success = true)
        }

        override fun sendBatch(results: List<AnalysisResult>): DeliveryResult {
            sentResults += results
            return DeliveryResult(success = true)
        }
    }

    private class RecordingDeliveryAttemptStore : DeliveryAttemptStore {
        val recordedAttempts = mutableListOf<DeliveryAttemptWrite>()

        override fun record(attempt: DeliveryAttemptWrite) {
            recordedAttempts += attempt
        }

        override fun findRecent(query: DeliveryAttemptQuery): List<DeliveryAttemptRecord> =
            recordedAttempts
                .sortedByDescending { it.attemptedAt }
                .take(query.limit)
                .mapIndexed { index, attempt ->
                    DeliveryAttemptRecord(
                        id = (index + 1).toLong(),
                        analysisResultId = attempt.analysisResultId,
                        eventId = attempt.eventId,
                        tenantId = attempt.tenantId,
                        channel = attempt.channel,
                        success = attempt.success,
                        externalId = attempt.externalId,
                        message = attempt.message,
                        attemptedAt = attempt.attemptedAt,
                    )
                }
    }
}
