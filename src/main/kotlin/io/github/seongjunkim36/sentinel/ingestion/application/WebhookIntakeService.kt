package io.github.seongjunkim36.sentinel.ingestion.application

import io.github.seongjunkim36.sentinel.shared.Event
import io.github.seongjunkim36.sentinel.shared.SourcePlugin
import org.springframework.stereotype.Service
import tools.jackson.databind.JsonNode

@Service
class WebhookIntakeService(
    sourcePlugins: List<SourcePlugin>,
    private val rawEventPublisher: RawEventPublisher,
) {
    private val sourcePluginIndex = sourcePlugins.associateBy { it.type }

    fun acceptWebhook(
        sourceType: String,
        tenantId: String,
        payload: JsonNode,
        headers: Map<String, String>,
    ): Event {
        val plugin = sourcePluginIndex[sourceType]
            ?: throw UnsupportedSourceTypeException(sourceType)

        val event = plugin.normalize(
            rawPayload = payload,
            tenantId = tenantId,
            headers = headers,
        )

        rawEventPublisher.publish(event)
        return event
    }
}

class UnsupportedSourceTypeException(sourceType: String) :
    IllegalArgumentException("No source plugin registered for type '$sourceType'")
