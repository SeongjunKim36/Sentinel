package io.github.seongjunkim36.sentinel.classification

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "sentinel.classification")
data class ClassificationProperties(
    val deduplication: DeduplicationProperties = DeduplicationProperties(),
)

data class DeduplicationProperties(
    val enabled: Boolean = true,
    val ttl: Duration = Duration.ofMinutes(30),
    val keyPrefix: String = "sentinel:classification:dedup",
)
