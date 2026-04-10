package io.github.seongjunkim36.sentinel.delivery

import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.stereotype.Component

@Component("deliveryPlugins")
class DeliveryPluginReadinessHealthIndicator(
    private val deliveryPluginReadinessService: DeliveryPluginReadinessService,
) : HealthIndicator {
    override fun health(): Health {
        val snapshot = deliveryPluginReadinessService.snapshot()
        val builder = if (snapshot.ready) Health.up() else Health.down()

        builder.withDetail("requiredChannels", snapshot.requiredChannels)
        builder.withDetail(
            "checks",
            snapshot.checks.map { check ->
                mapOf(
                    "channel" to check.channel,
                    "required" to check.required,
                    "registered" to check.registered,
                    "configured" to check.configured,
                    "ready" to check.ready,
                    "reason" to check.reason,
                )
            },
        )

        return builder.build()
    }
}
