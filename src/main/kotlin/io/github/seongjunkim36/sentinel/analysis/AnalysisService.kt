package io.github.seongjunkim36.sentinel.analysis

import io.github.seongjunkim36.sentinel.shared.AnalysisDetail
import io.github.seongjunkim36.sentinel.shared.AnalysisResult
import io.github.seongjunkim36.sentinel.shared.ClassifiedEvent
import io.github.seongjunkim36.sentinel.shared.LlmMetadata
import io.github.seongjunkim36.sentinel.shared.RoutingDecision
import io.github.seongjunkim36.sentinel.shared.RoutingPriority
import io.github.seongjunkim36.sentinel.shared.Severity
import org.springframework.stereotype.Service

@Service
class AnalysisService {
    fun analyze(classifiedEvent: ClassifiedEvent): AnalysisResult =
        AnalysisResult(
            eventId = classifiedEvent.event.id,
            tenantId = classifiedEvent.event.tenantId,
            category = classifiedEvent.category,
            severity = Severity.MEDIUM,
            confidence = 0.0,
            summary = "Analysis pipeline bootstrap is in place, but LLM integration is not implemented yet.",
            detail = AnalysisDetail(
                analysis = "The analyzer module exists and is ready for provider integration.",
                actionItems = listOf("Implement Anthropic primary client", "Add OpenAI fallback client"),
            ),
            llmMetadata = LlmMetadata(
                model = "bootstrap-placeholder",
                promptVersion = "bootstrap",
            ),
            routing = RoutingDecision(
                channels = listOf("slack"),
                priority = RoutingPriority.BATCHED,
            ),
        )
}
