package io.github.seongjunkim36.sentinel.delivery

import jakarta.servlet.http.HttpServletRequest
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.ResponseStatus

@Component
class DeliveryAttemptQueryAuthorizationService(
    private val deliveryApiProperties: DeliveryApiProperties,
) {
    init {
        val queryAuthorization = deliveryApiProperties.queryAuthorization
        if (queryAuthorization.enabled) {
            require(queryAuthorization.headerName.isNotBlank()) {
                "sentinel.delivery.api.query-authorization.header-name must not be blank when query authorization is enabled"
            }
            require(queryAuthorization.token.isNotBlank()) {
                "sentinel.delivery.api.query-authorization.token must be configured when query authorization is enabled"
            }
        }
    }

    fun authorizeQueryOrThrow(request: HttpServletRequest) {
        val queryAuthorization = deliveryApiProperties.queryAuthorization
        if (!queryAuthorization.enabled) {
            return
        }

        val providedToken = request.getHeader(queryAuthorization.headerName)?.trim().orEmpty()
        if (providedToken.isBlank()) {
            throw DeliveryAttemptQueryUnauthorizedException("Missing delivery-attempt query authorization header")
        }

        val expectedToken = queryAuthorization.token.trim()
        val isAuthorized =
            MessageDigest.isEqual(
                providedToken.toByteArray(StandardCharsets.UTF_8),
                expectedToken.toByteArray(StandardCharsets.UTF_8),
            )
        if (!isAuthorized) {
            throw DeliveryAttemptQueryUnauthorizedException("Invalid delivery-attempt query authorization token")
        }
    }
}

@ResponseStatus(HttpStatus.UNAUTHORIZED)
class DeliveryAttemptQueryUnauthorizedException(
    override val message: String,
) : RuntimeException(message)
