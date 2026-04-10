package io.github.seongjunkim36.sentinel.analysis

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "sentinel.analysis")
data class AnalysisProperties(
    val retry: AnalysisRetryProperties = AnalysisRetryProperties(),
    val failureRouting: AnalysisFailureRoutingProperties = AnalysisFailureRoutingProperties(),
    val llm: AnalysisLlmProperties = AnalysisLlmProperties(),
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

data class AnalysisLlmProperties(
    val provider: String = "bootstrap",
    val promptVersion: String = "openai-v1",
    val openai: OpenAiLlmProperties = OpenAiLlmProperties(),
)

data class OpenAiLlmProperties(
    val apiBaseUrl: String = "https://api.openai.com",
    val apiKey: String = "",
    val model: String = "gpt-4.1-mini",
    val temperature: Double = 0.2,
)
