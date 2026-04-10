package io.github.seongjunkim36.sentinel.deadletter

import jakarta.servlet.http.HttpServletRequest
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.ResponseStatus

@Component
class DeadLetterReplayAuthorizationService(
    private val deadLetterApiProperties: DeadLetterApiProperties,
) {
    init {
        val replayAuthorization = deadLetterApiProperties.replayAuthorization
        if (replayAuthorization.enabled) {
            require(replayAuthorization.headerName.isNotBlank()) {
                "sentinel.dead-letter.api.replay-authorization.header-name must not be blank when replay authorization is enabled"
            }
            require(replayAuthorization.token.isNotBlank()) {
                "sentinel.dead-letter.api.replay-authorization.token must be configured when replay authorization is enabled"
            }
        }
    }

    fun authorizeReplayOrThrow(request: HttpServletRequest) {
        val replayAuthorization = deadLetterApiProperties.replayAuthorization
        if (!replayAuthorization.enabled) {
            return
        }

        val providedToken = request.getHeader(replayAuthorization.headerName)?.trim().orEmpty()
        if (providedToken.isBlank()) {
            throw DeadLetterReplayUnauthorizedException("Missing replay authorization header")
        }

        val expectedToken = replayAuthorization.token.trim()
        val isAuthorized =
            MessageDigest.isEqual(
                providedToken.toByteArray(StandardCharsets.UTF_8),
                expectedToken.toByteArray(StandardCharsets.UTF_8),
            )
        if (!isAuthorized) {
            throw DeadLetterReplayUnauthorizedException("Invalid replay authorization token")
        }
    }
}

@ResponseStatus(HttpStatus.UNAUTHORIZED)
class DeadLetterReplayUnauthorizedException(
    override val message: String,
) : RuntimeException(message)
