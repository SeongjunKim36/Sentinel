package io.github.seongjunkim36.sentinel.deadletter

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "sentinel.dead-letter.replay")
data class DeadLetterReplayProperties(
    val maxReplayAttempts: Int = 3,
    val cooldown: Duration = Duration.ofMinutes(5),
    val requireOperatorNote: Boolean = true,
    val failureAlert: DeadLetterReplayFailureAlertProperties = DeadLetterReplayFailureAlertProperties(),
)

data class DeadLetterReplayFailureAlertProperties(
    val enabled: Boolean = true,
    val threshold: Int = 3,
    val window: Duration = Duration.ofMinutes(30),
    val channels: List<String> = listOf("telegram"),
)
