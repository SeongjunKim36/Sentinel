package io.github.seongjunkim36.sentinel.ingestion.api

import io.github.seongjunkim36.sentinel.ingestion.application.WebhookIntakeService
import jakarta.servlet.http.HttpServletRequest
import java.util.Collections
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode

@RestController
@RequestMapping("/api/v1/webhooks")
class WebhookController(
    private val webhookIntakeService: WebhookIntakeService,
) {
    @PostMapping("/{sourceType}")
    fun receiveWebhook(
        @PathVariable sourceType: String,
        @RequestHeader(name = "X-Sentinel-Tenant-Id", defaultValue = "dev-sentinel") tenantId: String,
        @RequestBody payload: JsonNode,
        request: HttpServletRequest,
    ): ResponseEntity<Map<String, Any>> {
        val normalizedEvent = webhookIntakeService.acceptWebhook(
            sourceType = sourceType,
            tenantId = tenantId,
            payload = payload,
            headers = Collections.list(request.headerNames).associateWith { request.getHeader(it) ?: "" },
        )

        return ResponseEntity.accepted().body(
            mapOf(
                "accepted" to true,
                "eventId" to normalizedEvent.id.toString(),
                "sourceType" to normalizedEvent.sourceType,
                "tenantId" to normalizedEvent.tenantId,
            ),
        )
    }
}
