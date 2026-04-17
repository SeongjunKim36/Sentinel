package io.github.seongjunkim36.sentinel.analysis

import io.github.seongjunkim36.sentinel.shared.ClassifiedEvent
import io.github.seongjunkim36.sentinel.shared.Severity
import io.github.seongjunkim36.sentinel.shared.TokenUsage
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    prefix = "sentinel.analysis.llm",
    name = ["provider"],
    havingValue = "bootstrap",
    matchIfMissing = true,
)
class BootstrapLlmClient : LlmClient {
    override fun analyze(classifiedEvent: ClassifiedEvent): LlmAnalysisResponse {
        val message = classifiedEvent.event.payload["message"]?.toString()?.trim().orEmpty()
        val normalizedMessage = message.lowercase()
        val subject =
            when (classifiedEvent.category) {
                "feed-update" -> "update"
                else -> "incident"
            }
        val severity =
            when {
                "outage" in normalizedMessage -> Severity.CRITICAL
                "timeout" in normalizedMessage -> Severity.HIGH
                else -> Severity.MEDIUM
            }

        return LlmAnalysisResponse(
            summary = "Potential $severity $subject detected from ${classifiedEvent.event.sourceType}.",
            analysis = buildString {
                append("Bootstrap analysis inferred an ")
                append(classifiedEvent.category)
                append(" event from the normalized message")
                if (message.isNotBlank()) {
                    append(": ")
                    append(message)
                } else {
                    append(".")
                }
            },
            actionItems = listOf(
                "Confirm whether the issue is reproducible in the affected flow.",
                "Check the latest deploys and infrastructure changes for the impacted tenant.",
            ),
            severity = severity,
            confidence = 0.68,
            model = "bootstrap-heuristic-llm",
            promptVersion = "bootstrap-v1",
            tokenUsage = TokenUsage(input = 120, output = 48),
            latencyMs = 25,
        )
    }
}
