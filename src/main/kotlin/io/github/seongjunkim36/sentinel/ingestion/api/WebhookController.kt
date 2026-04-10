package io.github.seongjunkim36.sentinel.ingestion.api

import io.micrometer.tracing.Tracer
import io.github.seongjunkim36.sentinel.ingestion.application.WebhookIntakeService
import jakarta.servlet.http.HttpServletRequest
import java.util.Collections
import java.util.UUID
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.JsonNode

@RestController
@RequestMapping("/api/v1/webhooks")
class WebhookController(
    private val webhookIntakeService: WebhookIntakeService,
    private val webhookSignatureValidator: WebhookSignatureValidator,
    private val tracerProvider: ObjectProvider<Tracer>,
    private val jsonMapper: JsonMapper,
) {
    @PostMapping("/{sourceType}")
    fun receiveWebhook(
        @PathVariable sourceType: String,
        @RequestHeader(name = "X-Sentinel-Tenant-Id", defaultValue = "dev-sentinel") tenantId: String,
        @RequestBody payload: String,
        request: HttpServletRequest,
    ): ResponseEntity<Map<String, Any>> {
        val traceId = currentTraceId()
        val requestHeaders =
            Collections.list(request.headerNames).associate { headerName ->
                headerName.lowercase() to (request.getHeader(headerName) ?: "")
            } + ("x-sentinel-trace-id" to traceId)
        webhookSignatureValidator.validateOrThrow(
            sourceType = sourceType,
            rawPayload = payload,
            headers = requestHeaders,
        )
        val jsonPayload = parsePayload(payload)

        val normalizedEvent = webhookIntakeService.acceptWebhook(
            sourceType = sourceType,
            tenantId = tenantId,
            payload = jsonPayload,
            headers = requestHeaders,
        )

        return ResponseEntity.accepted().body(
            mapOf(
                "accepted" to true,
                "eventId" to normalizedEvent.id.toString(),
                "sourceType" to normalizedEvent.sourceType,
                "tenantId" to normalizedEvent.tenantId,
                "traceId" to traceId,
            ),
        )
    }

    private fun currentTraceId(): String {
        val currentTraceId = tracerProvider.getIfAvailable()?.currentSpan()?.context()?.traceId()
        return currentTraceId ?: UUID.randomUUID().toString().replace("-", "")
    }

    private fun parsePayload(payload: String): JsonNode =
        runCatching {
            jsonMapper.readTree(payload)
        }.getOrElse { exception ->
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook JSON payload", exception)
        }
}
