package io.github.seongjunkim36.sentinel.ingestion

import io.github.seongjunkim36.sentinel.shared.Event
import io.github.seongjunkim36.sentinel.shared.EventMetadata
import io.github.seongjunkim36.sentinel.shared.WebhookSourcePlugin
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode

@Component
class SentrySourcePlugin(
    private val jsonMapper: JsonMapper,
) : WebhookSourcePlugin {
    override val type: String = "sentry"

    override fun normalize(
        rawPayload: JsonNode,
        tenantId: String,
        headers: Map<String, String>,
    ): Event {
        val traceId =
            headers["x-sentinel-trace-id"]?.ifBlank { null }
                ?: headers["traceparent"]
                    ?.split("-")
                    ?.getOrNull(1)
                    ?.takeIf { it.isNotBlank() }
        val sourceId = rawPayload.path("event_id").toString().trim('"').ifBlank { "unknown-sentry-event" }
        val normalizedPayload =
            jsonMapper.convertValue(
                rawPayload,
                object : TypeReference<Map<String, Any?>>() {},
            )

        return Event(
            sourceType = type,
            sourceId = sourceId,
            tenantId = tenantId,
            payload = normalizedPayload,
            metadata = EventMetadata(
                sourceVersion = headers["sentry-hook-version"] ?: "unknown",
                headers = headers,
                traceId = traceId,
            ),
        )
    }
}
