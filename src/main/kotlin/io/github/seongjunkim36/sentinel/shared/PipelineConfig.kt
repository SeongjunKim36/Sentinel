package io.github.seongjunkim36.sentinel.shared

data class PipelineConfig(
    val tenantId: String,
    val sources: List<SourceConfig>,
    val analysis: AnalysisConfig,
    val routing: List<RoutingConfig>,
    val costLimit: CostLimit,
)

data class SourceConfig(
    val type: String,
    val config: Map<String, Any?> = emptyMap(),
    val filters: FilterConfig = FilterConfig(),
)

data class FilterConfig(
    val include: List<String> = emptyList(),
    val exclude: List<String> = emptyList(),
)

data class AnalysisConfig(
    val promptTemplate: String,
    val model: String,
    val maxTokens: Int,
    val temperature: Double,
    val fallbackModel: String,
)

data class RoutingConfig(
    val channel: String,
    val config: Map<String, Any?> = emptyMap(),
    val conditions: RoutingConditions,
)

data class RoutingConditions(
    val minSeverity: Severity,
    val categories: List<String> = emptyList(),
)

data class CostLimit(
    val dailyMaxUsd: Double,
    val alertThresholdPercent: Int,
)
