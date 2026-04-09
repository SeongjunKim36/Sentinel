package io.github.seongjunkim36.sentinel.analysis

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "sentinel.analysis")
data class AnalysisProperties(
    val retry: AnalysisRetryProperties = AnalysisRetryProperties(),
    val failureRouting: AnalysisFailureRoutingProperties = AnalysisFailureRoutingProperties(),
)

data class AnalysisRetryProperties(
    val maxAttempts: Int = 3,
    val initialBackoff: Duration = Duration.ofMillis(200),
    val multiplier: Double = 2.0,
    val maxBackoff: Duration = Duration.ofSeconds(2),
)

data class AnalysisFailureRoutingProperties(
    val channels: List<String> = listOf("telegram"),
)
