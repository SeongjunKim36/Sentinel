package io.github.seongjunkim36.sentinel.analysis

import io.github.seongjunkim36.sentinel.shared.AnalysisDetail
import io.github.seongjunkim36.sentinel.shared.AnalysisResult
import io.github.seongjunkim36.sentinel.shared.ClassifiedEvent
import io.github.seongjunkim36.sentinel.shared.LlmMetadata
import io.github.seongjunkim36.sentinel.shared.RoutingDecision
import io.github.seongjunkim36.sentinel.shared.RoutingPriority
import org.springframework.stereotype.Service

@Service
class AnalysisService(
    private val llmClient: LlmClient,
) {
    fun analyze(classifiedEvent: ClassifiedEvent): AnalysisResult {
        val llmResponse = llmClient.analyze(classifiedEvent)

        return AnalysisResult(
            eventId = classifiedEvent.event.id,
            tenantId = classifiedEvent.event.tenantId,
            category = classifiedEvent.category,
            severity = llmResponse.severity,
            confidence = llmResponse.confidence,
            summary = llmResponse.summary,
            detail = AnalysisDetail(
                analysis = llmResponse.analysis,
                actionItems = llmResponse.actionItems,
            ),
            llmMetadata = LlmMetadata(
                model = llmResponse.model,
                promptVersion = llmResponse.promptVersion,
                tokenUsage = llmResponse.tokenUsage,
                costUsd = llmResponse.costUsd,
                latencyMs = llmResponse.latencyMs,
            ),
            routing = RoutingDecision(
                channels = listOf("slack"),
                priority = RoutingPriority.BATCHED,
            ),
        )
    }
}
