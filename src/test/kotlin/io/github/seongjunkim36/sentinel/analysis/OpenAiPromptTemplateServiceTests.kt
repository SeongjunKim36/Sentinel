package io.github.seongjunkim36.sentinel.analysis

import io.github.seongjunkim36.sentinel.shared.ClassifiedEvent
import io.github.seongjunkim36.sentinel.shared.Event
import io.github.seongjunkim36.sentinel.shared.EventMetadata
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.DefaultResourceLoader
import tools.jackson.databind.json.JsonMapper

class OpenAiPromptTemplateServiceTests {
    private val jsonMapper = JsonMapper.builder().findAndAddModules().build()

    @Test
    fun `resolves default prompt version and renders placeholders`() {
        val service = promptService(analysisProperties())

        val resolved = service.resolve(sampleClassifiedEvent())

        assertThat(resolved.version).isEqualTo("openai-v1")
        assertThat(resolved.systemPrompt).contains("incident analysis engine")
        assertThat(resolved.userPrompt).contains("tenant-alpha")
        assertThat(resolved.userPrompt).contains("\"message\":\"Checkout timeout\"")
    }

    @Test
    fun `applies tenant override before canary rollout`() {
        val service =
            promptService(
                analysisProperties(
                    promptRollout =
                        AnalysisPromptRolloutProperties(
                            canaryVersion = "openai-v1",
                            canaryPercentage = 100,
                            tenantOverrides = mapOf("tenant-alpha" to "openai-v2"),
                        ),
                ),
            )

        val resolved = service.resolve(sampleClassifiedEvent())

        assertThat(resolved.version).isEqualTo("openai-v2")
        assertThat(resolved.systemPrompt).contains("production troubleshooting")
    }

    @Test
    fun `uses canary version when rollout percentage matches`() {
        val service =
            promptService(
                analysisProperties(
                    promptRollout =
                        AnalysisPromptRolloutProperties(
                            canaryVersion = "openai-v2",
                            canaryPercentage = 100,
                        ),
                ),
            )

        val resolved = service.resolve(sampleClassifiedEvent())

        assertThat(resolved.version).isEqualTo("openai-v2")
    }

    private fun promptService(analysisProperties: AnalysisProperties): OpenAiPromptTemplateService =
        OpenAiPromptTemplateService(
            analysisProperties = analysisProperties,
            resourceLoader = DefaultResourceLoader(),
            jsonMapper = jsonMapper,
        )

    private fun analysisProperties(promptRollout: AnalysisPromptRolloutProperties = AnalysisPromptRolloutProperties()): AnalysisProperties =
        AnalysisProperties(
            llm =
                AnalysisLlmProperties(
                    provider = "openai",
                    promptVersion = "openai-v1",
                    promptRollout = promptRollout,
                ),
        )

    private fun sampleClassifiedEvent(): ClassifiedEvent =
        ClassifiedEvent(
            event =
                Event(
                    sourceType = "sentry",
                    sourceId = "evt-1001",
                    tenantId = "tenant-alpha",
                    payload = mapOf("message" to "Checkout timeout"),
                    metadata = EventMetadata(sourceVersion = "v1"),
                ),
            category = "error",
            analyzable = true,
            filtered = false,
        )
}
