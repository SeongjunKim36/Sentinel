package io.github.seongjunkim36.sentinel.delivery

import java.util.UUID
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/delivery-attempts")
class DeliveryAttemptQueryController(
    private val deliveryAttemptStore: DeliveryAttemptStore,
) {
    @GetMapping
    fun findRecent(
        @RequestParam(required = false) eventId: UUID?,
        @RequestParam(required = false) tenantId: String?,
        @RequestParam(required = false) channel: String?,
        @RequestParam(required = false) success: Boolean?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): List<DeliveryAttemptRecord> =
        deliveryAttemptStore.findRecent(
            DeliveryAttemptQuery(
                eventId = eventId,
                tenantId = tenantId,
                channel = channel,
                success = success,
                limit = limit,
            ),
        )
}
