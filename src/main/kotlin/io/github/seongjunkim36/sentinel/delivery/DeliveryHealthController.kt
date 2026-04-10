package io.github.seongjunkim36.sentinel.delivery

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/delivery")
class DeliveryHealthController(
    private val deliveryPluginReadinessService: DeliveryPluginReadinessService,
) {
    @GetMapping("/health")
    fun health(): DeliveryHealthResponse {
        val snapshot = deliveryPluginReadinessService.snapshot()
        return DeliveryHealthResponse(
            status = if (snapshot.ready) "UP" else "DOWN",
            requiredChannels = snapshot.requiredChannels,
            checks = snapshot.checks,
        )
    }
}

data class DeliveryHealthResponse(
    val status: String,
    val requiredChannels: List<String>,
    val checks: List<DeliveryChannelReadiness>,
)
