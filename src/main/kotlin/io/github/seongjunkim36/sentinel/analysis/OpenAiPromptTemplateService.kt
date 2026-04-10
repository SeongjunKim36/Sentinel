package io.github.seongjunkim36.sentinel.analysis

import io.github.seongjunkim36.sentinel.shared.ClassifiedEvent
import java.nio.charset.StandardCharsets
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

@Component
@ConditionalOnProperty(
    prefix = "sentinel.analysis.llm",
    name = ["provider"],
    havingValue = "openai",
)
class OpenAiPromptTemplateService(
    private val analysisProperties: AnalysisProperties,
    private val resourceLoader: ResourceLoader,
    private val jsonMapper: JsonMapper,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val variablePattern = Regex("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}")
    private val templatesByVersion = loadTemplates()

    init {
        require(templatesByVersion.isNotEmpty()) {
            "At least one prompt template must be configured under sentinel.analysis.llm.prompt-templates"
        }
        require(templatesByVersion.containsKey(defaultVersion())) {
            "Default prompt version '${defaultVersion()}' is not defined in sentinel.analysis.llm.prompt-templates"
        }
    }

    fun resolve(classifiedEvent: ClassifiedEvent): ResolvedOpenAiPrompt {
        val selectedVersion = selectPromptVersion(classifiedEvent)
        val template =
            templatesByVersion[selectedVersion]
                ?: run {
                    logger.warn(
                        "Prompt template version '{}' not found. Falling back to default '{}'.",
                        selectedVersion,
                        defaultVersion(),
                    )
                    templatesByVersion.getValue(defaultVersion())
                }

        val promptVariables =
            mapOf(
                "eventId" to classifiedEvent.event.id.toString(),
                "tenantId" to classifiedEvent.event.tenantId,
                "sourceType" to classifiedEvent.event.sourceType,
                "sourceId" to classifiedEvent.event.sourceId,
                "category" to classifiedEvent.category,
                "analyzable" to classifiedEvent.analyzable.toString(),
                "filterReason" to (classifiedEvent.filterReason ?: "none"),
                "tags" to classifiedEvent.tags.sorted().joinToString(",").ifBlank { "none" },
                "payloadJson" to jsonMapper.writeValueAsString(classifiedEvent.event.payload),
            )

        return ResolvedOpenAiPrompt(
            version = template.version,
            systemPrompt = renderTemplate(template.systemTemplate, promptVariables),
            userPrompt = renderTemplate(template.userTemplate, promptVariables),
        )
    }

    private fun selectPromptVersion(classifiedEvent: ClassifiedEvent): String {
        val rollout = analysisProperties.llm.promptRollout
        val tenantId = classifiedEvent.event.tenantId
        rollout.tenantOverrides[tenantId]
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { overriddenVersion ->
                if (templatesByVersion.containsKey(overriddenVersion)) {
                    return overriddenVersion
                }
                logger.warn(
                    "Tenant override prompt version '{}' is not configured for tenant '{}'. Using fallback selection.",
                    overriddenVersion,
                    tenantId,
                )
            }

        val canaryVersion = rollout.canaryVersion.trim()
        val canaryPercentage = rollout.canaryPercentage.coerceIn(0, 100)
        if (canaryVersion.isNotBlank() && canaryPercentage > 0 && shouldUseCanary(classifiedEvent, canaryPercentage)) {
            if (templatesByVersion.containsKey(canaryVersion)) {
                return canaryVersion
            }
            logger.warn(
                "Canary prompt version '{}' is not configured. Falling back to default '{}'.",
                canaryVersion,
                defaultVersion(),
            )
        }

        return defaultVersion()
    }

    private fun shouldUseCanary(
        classifiedEvent: ClassifiedEvent,
        canaryPercentage: Int,
    ): Boolean {
        val rolloutKey =
            "${classifiedEvent.event.tenantId}:${classifiedEvent.event.sourceType}:${classifiedEvent.event.sourceId}"
        val bucket = (rolloutKey.hashCode() and Int.MAX_VALUE) % 100
        return bucket < canaryPercentage
    }

    private fun renderTemplate(
        template: String,
        promptVariables: Map<String, String>,
    ): String =
        variablePattern.replace(template) { match ->
            promptVariables[match.groupValues[1]].orEmpty()
        }

    private fun loadTemplates(): Map<String, OpenAiPromptTemplate> =
        analysisProperties.llm.promptTemplates
            .associate { configured ->
                configured.version to
                    OpenAiPromptTemplate(
                        version = configured.version,
                        systemTemplate = readTemplate(configured.systemResource),
                        userTemplate = readTemplate(configured.userResource),
                    )
            }

    private fun readTemplate(location: String): String {
        val resource = resourceLoader.getResource(location)
        require(resource.exists()) { "Prompt template resource not found: $location" }
        val contents =
            resource.inputStream
                .bufferedReader(StandardCharsets.UTF_8)
                .use { it.readText() }
                .trim()
        require(contents.isNotBlank()) { "Prompt template resource is blank: $location" }
        return contents
    }

    private fun defaultVersion(): String = analysisProperties.llm.promptVersion
}

data class ResolvedOpenAiPrompt(
    val version: String,
    val systemPrompt: String,
    val userPrompt: String,
)

private data class OpenAiPromptTemplate(
    val version: String,
    val systemTemplate: String,
    val userTemplate: String,
)
