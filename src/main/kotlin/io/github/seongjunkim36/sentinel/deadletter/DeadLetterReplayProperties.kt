package io.github.seongjunkim36.sentinel.deadletter

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "sentinel.dead-letter.replay")
data class DeadLetterReplayProperties(
    val maxReplayAttempts: Int = 3,
    val cooldown: Duration = Duration.ofMinutes(5),
    val requireOperatorNote: Boolean = true,
)
