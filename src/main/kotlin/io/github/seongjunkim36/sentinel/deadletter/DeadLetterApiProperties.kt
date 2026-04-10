package io.github.seongjunkim36.sentinel.deadletter

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "sentinel.dead-letter.api")
data class DeadLetterApiProperties(
    val maxQueryLimit: Int = 200,
    val maxOperatorNoteLength: Int = 500,
    val replayAuthorization: DeadLetterReplayAuthorizationProperties = DeadLetterReplayAuthorizationProperties(),
)

data class DeadLetterReplayAuthorizationProperties(
    val enabled: Boolean = false,
    val headerName: String = "X-Sentinel-Replay-Token",
    val token: String = "",
)
