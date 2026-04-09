package io.github.seongjunkim36.sentinel.shared

import tools.jackson.databind.JsonNode

interface SourcePlugin {
    val type: String

    fun normalize(
        rawPayload: JsonNode,
        tenantId: String,
        headers: Map<String, String> = emptyMap(),
    ): Event
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
