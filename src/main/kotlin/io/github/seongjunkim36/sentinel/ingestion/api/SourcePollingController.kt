package io.github.seongjunkim36.sentinel.ingestion.api

import io.micrometer.tracing.Tracer
import io.github.seongjunkim36.sentinel.ingestion.application.SourcePollingService
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
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

@RestController
@RequestMapping("/api/v1/sources")
class SourcePollingController(
    private val sourcePollingService: SourcePollingService,
    private val tracerProvider: ObjectProvider<Tracer>,
    private val jsonMapper: JsonMapper,
) {
    @PostMapping("/{sourceType}/poll")
    fun pollSource(
        @PathVariable sourceType: String,
        @RequestHeader(name = "X-Sentinel-Tenant-Id") tenantId: String,
        @RequestBody(required = false) payload: String?,
        request: HttpServletRequest,
    ): ResponseEntity<Map<String, Any>> {
        val traceId = currentTraceId()
        val requestHeaders =
            Collections.list(request.headerNames).associate { headerName ->
                headerName.lowercase() to (request.getHeader(headerName) ?: "")
            } + ("x-sentinel-trace-id" to traceId)
        val jsonPayload = parsePayload(payload)
        val events =
            sourcePollingService.pollSource(
                sourceType = sourceType,
                tenantId = tenantId,
                request = jsonPayload,
                headers = requestHeaders,
            )

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(
            mapOf(
                "accepted" to true,
                "sourceType" to sourceType,
                "tenantId" to tenantId,
                "publishedCount" to events.size,
                "sourceIds" to events.map { it.sourceId },
                "traceId" to traceId,
            ),
        )
    }

    private fun currentTraceId(): String {
        val currentTraceId = tracerProvider.getIfAvailable()?.currentSpan()?.context()?.traceId()
        return currentTraceId ?: UUID.randomUUID().toString().replace("-", "")
    }

    private fun parsePayload(payload: String?): JsonNode =
        runCatching {
            jsonMapper.readTree(payload?.takeIf { it.isNotBlank() } ?: "{}")
        }.getOrElse { exception ->
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid source poll JSON payload", exception)
        }
}
