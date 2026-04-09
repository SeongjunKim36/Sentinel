package io.github.seongjunkim36.sentinel.analysis

import io.github.seongjunkim36.sentinel.shared.ClassifiedEvent
import io.github.seongjunkim36.sentinel.shared.Severity
import io.github.seongjunkim36.sentinel.shared.TokenUsage

interface LlmClient {
    fun analyze(classifiedEvent: ClassifiedEvent): LlmAnalysisResponse
}

data class LlmAnalysisResponse(
    val summary: String,
    val analysis: String,
    val actionItems: List<String>,
    val severity: Severity,
    val confidence: Double,
    val model: String,
    val promptVersion: String,
    val tokenUsage: TokenUsage = TokenUsage(),
    val costUsd: Double = 0.0,
    val latencyMs: Long = 0,
)
