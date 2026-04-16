package io.github.seongjunkim36.sentinel.delivery

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class DeliveryAttemptQueryApiExceptionHandler {
    companion object {
        private const val SCOPE = "delivery-attempt-query"
        private const val UNAUTHORIZED_ERROR_CODE = "DELIVERY_ATTEMPT_QUERY_UNAUTHORIZED"
        private const val RATE_LIMITED_ERROR_CODE = "DELIVERY_ATTEMPT_QUERY_RATE_LIMITED"
        private const val RATE_LIMIT_UNAVAILABLE_ERROR_CODE = "DELIVERY_ATTEMPT_QUERY_RATE_LIMIT_UNAVAILABLE"
    }

    @ExceptionHandler(DeliveryAttemptQueryUnauthorizedException::class)
    fun handleQueryUnauthorized(ex: DeliveryAttemptQueryUnauthorizedException): ResponseEntity<ProblemDetail> {
        val problemDetail =
            ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                ex.message ?: "Delivery-attempt query authorization failed",
            )
        problemDetail.title = "Unauthorized"
        problemDetail.type = java.net.URI.create("urn:sentinel:error:delivery-attempt-query-unauthorized")
        problemDetail.setProperty("scope", SCOPE)
        problemDetail.setProperty("errorCode", UNAUTHORIZED_ERROR_CODE)

        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(problemDetail)
    }

    @ExceptionHandler(DeliveryAttemptQueryRateLimitExceededException::class)
    fun handleRateLimitExceeded(ex: DeliveryAttemptQueryRateLimitExceededException): ResponseEntity<ProblemDetail> {
        val problemDetail =
            ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.message ?: "Rate limit exceeded")
        problemDetail.title = "Too Many Requests"
        problemDetail.type = java.net.URI.create("urn:sentinel:error:delivery-attempt-query-rate-limited")
        problemDetail.setProperty("scope", SCOPE)
        problemDetail.setProperty("errorCode", RATE_LIMITED_ERROR_CODE)
        problemDetail.setProperty("retryAfterSeconds", ex.retryAfterSeconds)

        return ResponseEntity
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, ex.retryAfterSeconds.toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(problemDetail)
    }

    @ExceptionHandler(DeliveryAttemptQueryRateLimitUnavailableException::class)
    fun handleRateLimitUnavailable(ex: DeliveryAttemptQueryRateLimitUnavailableException): ResponseEntity<ProblemDetail> {
        val problemDetail =
            ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                ex.message ?: "Rate limiter unavailable",
            )
        problemDetail.title = "Service Unavailable"
        problemDetail.type = java.net.URI.create("urn:sentinel:error:delivery-attempt-query-rate-limit-unavailable")
        problemDetail.setProperty("scope", SCOPE)
        problemDetail.setProperty("errorCode", RATE_LIMIT_UNAVAILABLE_ERROR_CODE)

        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(problemDetail)
    }
}
