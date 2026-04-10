package io.github.seongjunkim36.sentinel.observability

import io.github.seongjunkim36.sentinel.shared.ClassifiedEvent
import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.Tag

object PipelineMetrics {
    private const val INGESTION_EVENTS = "sentinel.pipeline.ingestion.events"
    private const val CLASSIFICATION_EVENTS = "sentinel.pipeline.classification.events"
    private const val DELIVERY_ATTEMPTS = "sentinel.pipeline.delivery.attempts"
    private const val DELIVERY_FANOUT = "sentinel.pipeline.delivery.fanout"

    fun recordIngestion(sourceType: String) {
        Metrics
            .counter(
                INGESTION_EVENTS,
                listOf(
                    Tag.of("source_type", sourceType.lowercase()),
                ),
            ).increment()
    }

    fun recordClassification(classifiedEvent: ClassifiedEvent) {
        val outcome =
            when {
                classifiedEvent.filtered -> "filtered"
                classifiedEvent.analyzable -> "analyzable"
                else -> "non_analyzable"
            }

        Metrics
            .counter(
                CLASSIFICATION_EVENTS,
                listOf(
                    Tag.of("category", classifiedEvent.category),
                    Tag.of("outcome", outcome),
                    Tag.of("filter_reason", classifiedEvent.filterReason ?: "none"),
                ),
            ).increment()
    }

    fun recordDeliveryAttempt(
        channel: String,
        success: Boolean,
        failureType: String = "none",
    ) {
        Metrics
            .counter(
                DELIVERY_ATTEMPTS,
                listOf(
                    Tag.of("channel", channel),
                    Tag.of("outcome", if (success) "success" else "failure"),
                    Tag.of("failure_type", if (success) "none" else failureType),
                ),
            ).increment()
    }

    fun recordDeliveryFanout(targetChannels: Int) {
        Metrics
            .summary(DELIVERY_FANOUT)
            .record(targetChannels.coerceAtLeast(0).toDouble())
    }
}
