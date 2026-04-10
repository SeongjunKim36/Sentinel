package io.github.seongjunkim36.sentinel.analysis

import io.github.seongjunkim36.sentinel.shared.ClassifiedEvent
import io.github.seongjunkim36.sentinel.shared.Event
import io.github.seongjunkim36.sentinel.shared.EventMetadata
import io.github.seongjunkim36.sentinel.shared.Severity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.DefaultResourceLoader
import tools.jackson.databind.json.JsonMapper

class OpenAiLlmClientTests {
    @Test
    fun `maps openai completion payload into llm response`() {
        val chatClient = RecordingOpenAiChatCompletionClient()
        chatClient.nextResponse =
            OpenAiChatCompletionResponse(
                model = "gpt-4.1-mini",
                choices =
                    listOf(
                        OpenAiChatCompletionChoice(
                            message =
                                OpenAiMessageContent(
                                    content =
                                        """
                                        {
                                          "summary": "Checkout timeout is impacting order completion.",
                                          "analysis": "Database latency spike likely caused timeout amplification.",
                                          "actionItems": ["Inspect DB CPU", "Scale connection pool"],
                                          "severity": "HIGH",
                                          "confidence": 0.84
                                        }
                                        """.trimIndent(),
                                ),
                        ),
                    ),
                usage = OpenAiUsage(promptTokens = 120, completionTokens = 64),
            )

        val analysisProperties = analysisProperties()
        val jsonMapper = JsonMapper.builder().findAndAddModules().build()
        val promptTemplateService =
            OpenAiPromptTemplateService(
                analysisProperties = analysisProperties,
                resourceLoader = DefaultResourceLoader(),
                jsonMapper = jsonMapper,
            )
        val client =
            OpenAiLlmClient(
                openAiChatCompletionClient = chatClient,
                openAiPromptTemplateService = promptTemplateService,
                analysisProperties = analysisProperties,
                jsonMapper = jsonMapper,
            )

        val result = client.analyze(sampleClassifiedEvent())

        assertThat(chatClient.lastRequest).isNotNull
        assertThat(chatClient.lastRequest!!.model).isEqualTo("gpt-4.1-mini")
        assertThat(chatClient.lastRequest!!.messages).hasSize(2)
        assertThat(chatClient.lastRequest!!.messages[1].content).contains("tenant-alpha")

        assertThat(result.summary).contains("Checkout timeout")
        assertThat(result.analysis).contains("latency spike")
        assertThat(result.actionItems).containsExactly("Inspect DB CPU", "Scale connection pool")
        assertThat(result.severity).isEqualTo(Severity.HIGH)
        assertThat(result.confidence).isEqualTo(0.84)
        assertThat(result.model).isEqualTo("gpt-4.1-mini")
        assertThat(result.promptVersion).isEqualTo("openai-v1")
        assertThat(result.tokenUsage.input).isEqualTo(120)
        assertThat(result.tokenUsage.output).isEqualTo(64)
    }

    @Test
    fun `falls back to safe defaults when openai payload has invalid values`() {
        val chatClient = RecordingOpenAiChatCompletionClient()
        chatClient.nextResponse =
            OpenAiChatCompletionResponse(
                model = "gpt-4.1-mini",
                choices =
                    listOf(
                        OpenAiChatCompletionChoice(
                            message =
                                OpenAiMessageContent(
                                    content =
                                        """
                                        {
                                          "summary": "",
                                          "analysis": "",
                                          "actionItems": [],
                                          "severity": "unknown",
                                          "confidence": 1.7
                                        }
                                        """.trimIndent(),
                                ),
                        ),
                    ),
            )

        val analysisProperties = analysisProperties()
        val jsonMapper = JsonMapper.builder().findAndAddModules().build()
        val promptTemplateService =
            OpenAiPromptTemplateService(
                analysisProperties = analysisProperties,
                resourceLoader = DefaultResourceLoader(),
                jsonMapper = jsonMapper,
            )
        val client =
            OpenAiLlmClient(
                openAiChatCompletionClient = chatClient,
                openAiPromptTemplateService = promptTemplateService,
                analysisProperties = analysisProperties,
                jsonMapper = jsonMapper,
            )

        val result = client.analyze(sampleClassifiedEvent())

        assertThat(result.summary).isEqualTo("Potential incident requires investigation.")
        assertThat(result.analysis).isEqualTo("No analysis details were returned by the LLM.")
        assertThat(result.actionItems).containsExactly("Review logs and telemetry for this event.")
        assertThat(result.severity).isEqualTo(Severity.MEDIUM)
        assertThat(result.confidence).isEqualTo(1.0)
    }

    private fun analysisProperties(): AnalysisProperties =
        AnalysisProperties(
            llm =
                AnalysisLlmProperties(
                    provider = "openai",
                    promptVersion = "openai-v1",
                    openai =
                        OpenAiLlmProperties(
                            apiBaseUrl = "https://api.openai.com",
                            apiKey = "test-key",
                            model = "gpt-4.1-mini",
                            temperature = 0.2,
                        ),
                ),
        )

    private fun sampleClassifiedEvent(): ClassifiedEvent =
        ClassifiedEvent(
            event =
                Event(
                    sourceType = "sentry",
                    sourceId = "evt-1001",
                    tenantId = "tenant-alpha",
                    payload = mapOf("message" to "Checkout timeout caused payment failures"),
                    metadata = EventMetadata(sourceVersion = "v1"),
                ),
            category = "error",
            analyzable = true,
            filtered = false,
        )

    private class RecordingOpenAiChatCompletionClient : OpenAiChatCompletionClient {
        var lastRequest: OpenAiChatCompletionRequest? = null
        var nextResponse: OpenAiChatCompletionResponse? = null

        override fun createCompletion(request: OpenAiChatCompletionRequest): OpenAiChatCompletionResponse {
            lastRequest = request
            return nextResponse ?: error("nextResponse must be set in test")
        }
    }
}
