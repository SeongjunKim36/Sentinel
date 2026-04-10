package io.github.seongjunkim36.sentinel.observability

import io.github.seongjunkim36.sentinel.shared.ClassifiedEvent
import io.github.seongjunkim36.sentinel.shared.Event
import io.github.seongjunkim36.sentinel.shared.EventMetadata
import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PipelineMetricsTests {
    private lateinit var meterRegistry: SimpleMeterRegistry

    @BeforeEach
    fun setUp() {
        meterRegistry = SimpleMeterRegistry()
        Metrics.addRegistry(meterRegistry)
    }

    @AfterEach
    fun tearDown() {
        Metrics.removeRegistry(meterRegistry)
        meterRegistry.close()
    }

    @Test
    fun `records ingestion event counter by source type`() {
        PipelineMetrics.recordIngestion("sentry")
        PipelineMetrics.recordIngestion("sentry")

        val count =
            meterRegistry
                .find("sentinel.pipeline.ingestion.events")
                .tag("source_type", "sentry")
                .counter()
                ?.count()

        assertThat(count).isEqualTo(2.0)
    }

    @Test
    fun `records classification outcomes with reason tags`() {
        val filteredEvent =
            ClassifiedEvent(
                event = sampleEvent(),
                category = "error",
                analyzable = false,
                filtered = true,
                filterReason = "duplicate-event",
            )
        val analyzableEvent =
            ClassifiedEvent(
                event = sampleEvent(),
                category = "error",
                analyzable = true,
                filtered = false,
            )

        PipelineMetrics.recordClassification(filteredEvent)
        PipelineMetrics.recordClassification(analyzableEvent)

        val filteredCount =
            meterRegistry
                .find("sentinel.pipeline.classification.events")
                .tags(
                    "category",
                    "error",
                    "outcome",
                    "filtered",
                    "filter_reason",
                    "duplicate-event",
                ).counter()
                ?.count()
        val analyzableCount =
            meterRegistry
                .find("sentinel.pipeline.classification.events")
                .tags(
                    "category",
                    "error",
                    "outcome",
                    "analyzable",
                    "filter_reason",
                    "none",
                ).counter()
                ?.count()

        assertThat(filteredCount).isEqualTo(1.0)
        assertThat(analyzableCount).isEqualTo(1.0)
    }

    @Test
    fun `records delivery attempts and fanout summary`() {
        PipelineMetrics.recordDeliveryFanout(2)
        PipelineMetrics.recordDeliveryAttempt(
            tenantId = "tenant-alpha",
            channel = "slack",
            category = "error",
            success = true,
        )
        PipelineMetrics.recordDeliveryAttempt(
            tenantId = "tenant-alpha",
            channel = "telegram",
            category = "replay-failure-alert",
            success = false,
            failureType = "plugin_missing",
        )

        val successCount =
            meterRegistry
                .find("sentinel.pipeline.delivery.attempts")
                .tags(
                    "channel",
                    "slack",
                    "tenant_id",
                    "tenant-alpha",
                    "category",
                    "error",
                    "outcome",
                    "success",
                    "failure_type",
                    "none",
                ).counter()
                ?.count()
        val failureCount =
            meterRegistry
                .find("sentinel.pipeline.delivery.attempts")
                .tags(
                    "channel",
                    "telegram",
                    "tenant_id",
                    "tenant-alpha",
                    "category",
                    "replay-failure-alert",
                    "outcome",
                    "failure",
                    "failure_type",
                    "plugin_missing",
                ).counter()
                ?.count()
        val fanoutSummary =
            meterRegistry
                .find("sentinel.pipeline.delivery.fanout")
                .summary()

        assertThat(successCount).isEqualTo(1.0)
        assertThat(failureCount).isEqualTo(1.0)
        assertThat(fanoutSummary).isNotNull
        assertThat(fanoutSummary!!.count()).isEqualTo(1L)
        assertThat(fanoutSummary.totalAmount()).isEqualTo(2.0)
    }

    private fun sampleEvent(): Event =
        Event(
            id = UUID.randomUUID(),
            sourceType = "sentry",
            sourceId = "evt-${UUID.randomUUID()}",
            tenantId = "tenant-alpha",
            payload = mapOf("message" to "checkout timeout"),
            metadata = EventMetadata(sourceVersion = "v1"),
        )
}
