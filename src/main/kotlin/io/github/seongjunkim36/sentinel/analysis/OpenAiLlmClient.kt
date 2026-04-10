package io.github.seongjunkim36.sentinel.analysis

import com.fasterxml.jackson.annotation.JsonProperty
import io.github.seongjunkim36.sentinel.shared.ClassifiedEvent
import io.github.seongjunkim36.sentinel.shared.Severity
import io.github.seongjunkim36.sentinel.shared.TokenUsage
import java.time.Duration
import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

@Component
@ConditionalOnProperty(
    prefix = "sentinel.analysis.llm",
    name = ["provider"],
    havingValue = "openai",
)
class OpenAiLlmClient(
    private val openAiChatCompletionClient: OpenAiChatCompletionClient,
    private val analysisProperties: AnalysisProperties,
    private val jsonMapper: JsonMapper,
) : LlmClient {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun analyze(classifiedEvent: ClassifiedEvent): LlmAnalysisResponse {
        val openAi = analysisProperties.llm.openai
        val startedAt = Instant.now()

        try {
            val completionResponse =
                openAiChatCompletionClient.createCompletion(
                    OpenAiChatCompletionRequest(
                        model = openAi.model,
                        temperature = openAi.temperature.coerceIn(0.0, 1.0),
                        responseFormat = OpenAiResponseFormat(type = "json_object"),
                        messages =
                            listOf(
                                OpenAiMessage(
                                    role = "system",
                                    content = systemPrompt(),
                                ),
                                OpenAiMessage(
                                    role = "user",
                                    content = userPrompt(classifiedEvent),
                                ),
                            ),
                    ),
                )

            val content =
                completionResponse.choices
                    .firstOrNull()
                    ?.message
                    ?.content
                    ?.trim()
                    .orEmpty()
            if (content.isBlank()) {
                throw IllegalStateException("OpenAI returned an empty completion content")
            }

            val parsed = parseStructuredContent(content)
            val latencyMs = Duration.between(startedAt, Instant.now()).toMillis()
            val usage = completionResponse.usage

            return LlmAnalysisResponse(
                summary = parsed.summary,
                analysis = parsed.analysis,
                actionItems = parsed.actionItems,
                severity = parsed.severity,
                confidence = parsed.confidence,
                model = completionResponse.model ?: openAi.model,
                promptVersion = analysisProperties.llm.promptVersion,
                tokenUsage =
                    TokenUsage(
                        input = usage?.promptTokens ?: 0,
                        output = usage?.completionTokens ?: 0,
                    ),
                latencyMs = latencyMs,
            )
        } catch (exception: RestClientException) {
            logger.error(
                "OpenAI analysis call failed: eventId={}, tenantId={}",
                classifiedEvent.event.id,
                classifiedEvent.event.tenantId,
                exception,
            )
            throw IllegalStateException("OpenAI analysis request failed", exception)
        }
    }

    private fun systemPrompt(): String =
        """
        You are Sentinel's incident analysis engine.
        Return ONLY valid JSON with keys:
        - summary: concise summary
        - analysis: root-cause oriented analysis
        - actionItems: array of concrete next actions
        - severity: one of CRITICAL,HIGH,MEDIUM,LOW,INFO
        - confidence: number between 0 and 1
        """.trimIndent()

    private fun userPrompt(classifiedEvent: ClassifiedEvent): String =
        """
        Analyze this classified event and produce JSON output.
        
        tenantId: ${classifiedEvent.event.tenantId}
        sourceType: ${classifiedEvent.event.sourceType}
        sourceId: ${classifiedEvent.event.sourceId}
        category: ${classifiedEvent.category}
        analyzable: ${classifiedEvent.analyzable}
        filterReason: ${classifiedEvent.filterReason ?: "none"}
        tags: ${classifiedEvent.tags.sorted().joinToString(",").ifBlank { "none" }}
        payload: ${jsonMapper.writeValueAsString(classifiedEvent.event.payload)}
        """.trimIndent()

    private fun parseStructuredContent(content: String): ParsedLlmOutput {
        val root = jsonMapper.readTree(content)
        val summary = root.string("summary").ifBlank { "Potential incident requires investigation." }
        val analysis = root.string("analysis").ifBlank { "No analysis details were returned by the LLM." }
        val actionItems =
            root.array("actionItems")
                .ifEmpty { root.array("action_items") }
                .ifEmpty { listOf("Review logs and telemetry for this event.") }
        val severity = root.enumValue("severity", Severity.MEDIUM)
        val confidence = root.double("confidence", 0.6).coerceIn(0.0, 1.0)

        return ParsedLlmOutput(
            summary = summary,
            analysis = analysis,
            actionItems = actionItems,
            severity = severity,
            confidence = confidence,
        )
    }

    private fun JsonNode.string(field: String): String = nodeText(path(field))

    private fun JsonNode.array(field: String): List<String> {
        val node = get(field) ?: return emptyList()
        if (!node.isArray) {
            return emptyList()
        }

        return node.mapNotNull { child ->
            nodeText(child).takeIf { it.isNotBlank() }
        }
    }

    private fun JsonNode.double(
        field: String,
        defaultValue: Double,
    ): Double = if (has(field)) path(field).asDouble(defaultValue) else defaultValue

    private fun <T : Enum<T>> JsonNode.enumValue(
        field: String,
        defaultValue: T,
    ): T {
        val text = string(field)
        if (text.isBlank()) {
            return defaultValue
        }

        @Suppress("UNCHECKED_CAST")
        val enumType = defaultValue::class.java as Class<T>
        return runCatching { java.lang.Enum.valueOf(enumType, text.uppercase()) }.getOrElse { defaultValue }
    }

    private fun nodeText(node: JsonNode): String =
        runCatching { jsonMapper.convertValue(node, String::class.java) }
            .getOrDefault("")
            .trim()
}

@Component
@ConditionalOnProperty(
    prefix = "sentinel.analysis.llm",
    name = ["provider"],
    havingValue = "openai",
)
class HttpOpenAiChatCompletionClient(
    private val analysisProperties: AnalysisProperties,
    restClientBuilder: RestClient.Builder,
) : OpenAiChatCompletionClient {
    private val openAi = analysisProperties.llm.openai
    private val restClient =
        restClientBuilder
            .baseUrl(openAi.apiBaseUrl)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${openAi.apiKey}")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build()

    init {
        require(openAi.apiKey.isNotBlank()) {
            "sentinel.analysis.llm.openai.api-key must be configured when provider=openai"
        }
    }

    override fun createCompletion(request: OpenAiChatCompletionRequest): OpenAiChatCompletionResponse =
        restClient.post()
            .uri("/v1/chat/completions")
            .body(request)
            .retrieve()
            .body(OpenAiChatCompletionResponse::class.java)
            ?: throw IllegalStateException("OpenAI returned an empty response body")
}

interface OpenAiChatCompletionClient {
    fun createCompletion(request: OpenAiChatCompletionRequest): OpenAiChatCompletionResponse
}

data class OpenAiChatCompletionRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    val temperature: Double,
    @JsonProperty("response_format")
    val responseFormat: OpenAiResponseFormat? = null,
)

data class OpenAiResponseFormat(
    val type: String,
)

data class OpenAiMessage(
    val role: String,
    val content: String,
)

data class OpenAiChatCompletionResponse(
    val model: String? = null,
    val choices: List<OpenAiChatCompletionChoice> = emptyList(),
    val usage: OpenAiUsage? = null,
)

data class OpenAiChatCompletionChoice(
    val message: OpenAiMessageContent? = null,
)

data class OpenAiMessageContent(
    val content: String? = null,
)

data class OpenAiUsage(
    @JsonProperty("prompt_tokens")
    val promptTokens: Int? = null,
    @JsonProperty("completion_tokens")
    val completionTokens: Int? = null,
)

private data class ParsedLlmOutput(
    val summary: String,
    val analysis: String,
    val actionItems: List<String>,
    val severity: Severity,
    val confidence: Double,
)
