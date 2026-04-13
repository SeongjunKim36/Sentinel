package io.github.seongjunkim36.sentinel.delivery

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class DeliveryAttemptQueryApiExceptionHandler {
    @ExceptionHandler(DeliveryAttemptQueryRateLimitExceededException::class)
    fun handleRateLimitExceeded(ex: DeliveryAttemptQueryRateLimitExceededException): ResponseEntity<ProblemDetail> {
        val problemDetail =
            ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.message ?: "Rate limit exceeded")
        problemDetail.title = "Too Many Requests"
        problemDetail.setProperty("retryAfterSeconds", ex.retryAfterSeconds)

        return ResponseEntity
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, ex.retryAfterSeconds.toString())
            .body(problemDetail)
    }
}
