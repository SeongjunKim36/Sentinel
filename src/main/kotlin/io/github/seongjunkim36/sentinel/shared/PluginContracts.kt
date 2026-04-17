package io.github.seongjunkim36.sentinel.shared

import tools.jackson.databind.JsonNode

interface SourcePlugin {
    val type: String
}

interface WebhookSourcePlugin : SourcePlugin {
    fun normalize(
        rawPayload: JsonNode,
        tenantId: String,
        headers: Map<String, String> = emptyMap(),
    ): Event
}

interface PollingSourcePlugin : SourcePlugin {
    fun poll(
        tenantId: String,
        request: JsonNode,
        headers: Map<String, String> = emptyMap(),
    ): List<Event>
}

interface OutputPlugin {
    val type: String

    fun send(result: AnalysisResult): DeliveryResult

    fun sendBatch(results: List<AnalysisResult>): DeliveryResult
}

data class DeliveryResult(
    val success: Boolean,
    val externalId: String? = null,
    val message: String? = null,
)
