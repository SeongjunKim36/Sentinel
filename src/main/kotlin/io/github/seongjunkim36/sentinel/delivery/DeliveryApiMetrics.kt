package io.github.seongjunkim36.sentinel.delivery

import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.Tag

object DeliveryApiMetrics {
    private const val DELIVERY_ATTEMPT_QUERY_REQUESTS = "sentinel.delivery.api.query.requests"

    fun recordDeliveryAttemptQuery(
        tenantId: String,
        outcome: String,
    ) {
        Metrics
            .counter(
                DELIVERY_ATTEMPT_QUERY_REQUESTS,
                listOf(
                    Tag.of("tenant_id", tenantId),
                    Tag.of("outcome", outcome),
                ),
            ).increment()
    }
}
