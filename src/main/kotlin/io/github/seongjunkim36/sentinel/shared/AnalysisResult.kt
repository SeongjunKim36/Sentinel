package io.github.seongjunkim36.sentinel.shared

import java.time.Instant
import java.util.UUID

data class AnalysisResult(
    val id: UUID = UUID.randomUUID(),
    val eventId: UUID,
    val tenantId: String,
    val traceId: String? = null,
    val category: String,
    val severity: Severity,
    val confidence: Double,
    val summary: String,
    val detail: AnalysisDetail,
    val llmMetadata: LlmMetadata,
    val routing: RoutingDecision,
    val createdAt: Instant = Instant.now(),
)

data class AnalysisDetail(
    val analysis: String,
    val actionItems: List<String> = emptyList(),
    val relatedEvents: List<UUID> = emptyList(),
)

data class LlmMetadata(
    val model: String,
    val promptVersion: String,
    val tokenUsage: TokenUsage = TokenUsage(),
    val costUsd: Double = 0.0,
    val latencyMs: Long = 0,
)

data class TokenUsage(
    val input: Int = 0,
    val output: Int = 0,
)

data class RoutingDecision(
    val channels: List<String> = emptyList(),
    val priority: RoutingPriority = RoutingPriority.DIGEST,
)
