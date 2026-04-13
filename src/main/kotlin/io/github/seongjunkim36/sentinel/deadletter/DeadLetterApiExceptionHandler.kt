package io.github.seongjunkim36.sentinel.deadletter

import java.net.URI
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

@RestControllerAdvice(assignableTypes = [DeadLetterController::class])
class DeadLetterApiExceptionHandler {
    companion object {
        private const val SCOPE = "dead-letter-replay"
        private const val ERROR_CODE_UNAUTHORIZED = "DEAD_LETTER_REPLAY_UNAUTHORIZED"
        private const val ERROR_CODE_NOT_FOUND = "DEAD_LETTER_REPLAY_NOT_FOUND"
        private const val ERROR_CODE_BLOCKED = "DEAD_LETTER_REPLAY_BLOCKED"
        private const val ERROR_CODE_OPERATOR_NOTE_TOO_LONG = "DEAD_LETTER_REPLAY_OPERATOR_NOTE_TOO_LONG"
        private const val ERROR_CODE_INVALID_CURSOR = "DEAD_LETTER_API_CURSOR_INVALID"
        private const val ERROR_CODE_TENANT_SCOPE_REQUIRED = "DEAD_LETTER_API_TENANT_SCOPE_REQUIRED"
        private const val ERROR_CODE_TENANT_SCOPE_MISMATCH = "DEAD_LETTER_API_TENANT_SCOPE_MISMATCH"
        private const val ERROR_CODE_BAD_REQUEST = "DEAD_LETTER_API_BAD_REQUEST"
    }

    @ExceptionHandler(DeadLetterReplayUnauthorizedException::class)
    fun handleReplayUnauthorized(ex: DeadLetterReplayUnauthorizedException): ResponseEntity<ProblemDetail> {
        val problemDetail =
            ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                ex.message ?: "Replay authorization failed",
            )
        problemDetail.title = "Unauthorized"
        problemDetail.type = URI.create("urn:sentinel:error:dead-letter-replay-unauthorized")
        problemDetail.setProperty("scope", SCOPE)
        problemDetail.setProperty("errorCode", ERROR_CODE_UNAUTHORIZED)

        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(problemDetail)
    }

    @ExceptionHandler(DeadLetterReplayNotFoundException::class)
    fun handleReplayNotFound(ex: DeadLetterReplayNotFoundException): ResponseEntity<ProblemDetail> {
        val problemDetail =
            ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.message,
            )
        problemDetail.title = "Not Found"
        problemDetail.type = URI.create("urn:sentinel:error:dead-letter-replay-not-found")
        problemDetail.setProperty("scope", SCOPE)
        problemDetail.setProperty("errorCode", ERROR_CODE_NOT_FOUND)
        problemDetail.setProperty("deadLetterId", ex.deadLetterId)

        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(problemDetail)
    }

    @ExceptionHandler(DeadLetterReplayBlockedException::class)
    fun handleReplayBlocked(ex: DeadLetterReplayBlockedException): ResponseEntity<ProblemDetail> {
        val problemDetail =
            ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                ex.message,
            )
        problemDetail.title = "Conflict"
        problemDetail.type = URI.create("urn:sentinel:error:dead-letter-replay-blocked")
        problemDetail.setProperty("scope", SCOPE)
        problemDetail.setProperty("errorCode", ERROR_CODE_BLOCKED)
        problemDetail.setProperty("deadLetterId", ex.deadLetterId)
        problemDetail.setProperty("replayStatus", ex.replayStatus.name)
        problemDetail.setProperty("replayOutcome", ex.replayOutcome.name)

        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(problemDetail)
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatusException(ex: ResponseStatusException): ResponseEntity<ProblemDetail> {
        val detail = ex.reason ?: ex.message ?: "Bad request"
        val statusCode = ex.statusCode
        val problemDetail = ProblemDetail.forStatusAndDetail(statusCode, detail)
        if (statusCode == HttpStatus.BAD_REQUEST) {
            problemDetail.title = "Bad Request"
        }
        problemDetail.type = URI.create(resolveErrorType(detail))
        problemDetail.setProperty("scope", SCOPE)
        problemDetail.setProperty("errorCode", resolveErrorCode(detail))

        return ResponseEntity
            .status(statusCode)
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(problemDetail)
    }

    private fun resolveErrorCode(detail: String): String =
        when {
            detail.startsWith("operatorNote exceeds maximum length") -> ERROR_CODE_OPERATOR_NOTE_TOO_LONG
            detail == "Invalid cursor format" -> ERROR_CODE_INVALID_CURSOR
            detail.endsWith("header is required") -> ERROR_CODE_TENANT_SCOPE_REQUIRED
            detail == "tenantId filter must match scoped tenant header" -> ERROR_CODE_TENANT_SCOPE_MISMATCH
            else -> ERROR_CODE_BAD_REQUEST
        }

    private fun resolveErrorType(detail: String): String =
        when (resolveErrorCode(detail)) {
            ERROR_CODE_OPERATOR_NOTE_TOO_LONG -> "urn:sentinel:error:dead-letter-replay-operator-note-too-long"
            ERROR_CODE_INVALID_CURSOR -> "urn:sentinel:error:dead-letter-api-cursor-invalid"
            ERROR_CODE_TENANT_SCOPE_REQUIRED -> "urn:sentinel:error:dead-letter-api-tenant-scope-required"
            ERROR_CODE_TENANT_SCOPE_MISMATCH -> "urn:sentinel:error:dead-letter-api-tenant-scope-mismatch"
            else -> "urn:sentinel:error:dead-letter-api-bad-request"
        }
}
