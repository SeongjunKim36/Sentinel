package io.github.seongjunkim36.sentinel.ingestion.application

import io.github.seongjunkim36.sentinel.observability.PipelineMetrics
import io.github.seongjunkim36.sentinel.shared.Event
import io.github.seongjunkim36.sentinel.shared.PollingSourcePlugin
import org.springframework.stereotype.Service
import tools.jackson.databind.JsonNode

@Service
class SourcePollingService(
    pollingSourcePlugins: List<PollingSourcePlugin>,
    private val rawEventPublisher: RawEventPublisher,
) {
    private val sourcePluginIndex = pollingSourcePlugins.associateBy { it.type }

    fun pollSource(
        sourceType: String,
        tenantId: String,
        request: JsonNode,
        headers: Map<String, String>,
    ): List<Event> {
        val plugin = sourcePluginIndex[sourceType]
            ?: throw UnsupportedSourceTypeException(sourceType)

        return plugin.poll(
            tenantId = tenantId,
            request = request,
            headers = headers,
        ).onEach { event ->
            rawEventPublisher.publish(event)
            PipelineMetrics.recordIngestion(event.sourceType)
        }
    }
}
