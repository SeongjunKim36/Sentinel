package io.github.seongjunkim36.sentinel.deadletter

import jakarta.servlet.http.HttpServletRequest
import java.net.URI
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

@RestControllerAdvice(assignableTypes = [DeadLetterController::class])
class DeadLetterApiExceptionHandler {
    companion object {
        private const val REPLAY_SCOPE = "dead-letter-replay"
        private const val LIST_SCOPE = "dead-letter-list"
        private const val REPLAY_AUDITS_SCOPE = "dead-letter-replay-audits"

        private const val ERROR_CODE_UNAUTHORIZED = "DEAD_LETTER_REPLAY_UNAUTHORIZED"
        private const val ERROR_CODE_NOT_FOUND = "DEAD_LETTER_REPLAY_NOT_FOUND"
        private const val ERROR_CODE_BLOCKED = "DEAD_LETTER_REPLAY_BLOCKED"
        private const val ERROR_CODE_OPERATOR_NOTE_TOO_LONG = "DEAD_LETTER_REPLAY_OPERATOR_NOTE_TOO_LONG"
        private const val ERROR_CODE_INVALID_CURSOR = "DEAD_LETTER_API_CURSOR_INVALID"
        private const val ERROR_CODE_TENANT_SCOPE_REQUIRED = "DEAD_LETTER_API_TENANT_SCOPE_REQUIRED"
        private const val ERROR_CODE_TENANT_SCOPE_MISMATCH = "DEAD_LETTER_API_TENANT_SCOPE_MISMATCH"
        private const val ERROR_CODE_BAD_REQUEST = "DEAD_LETTER_API_BAD_REQUEST"

        private const val ERROR_CODE_LIST_CURSOR_INVALID = "DEAD_LETTER_LIST_CURSOR_INVALID"
        private const val ERROR_CODE_LIST_TENANT_SCOPE_REQUIRED = "DEAD_LETTER_LIST_TENANT_SCOPE_REQUIRED"
        private const val ERROR_CODE_LIST_TENANT_SCOPE_MISMATCH = "DEAD_LETTER_LIST_TENANT_SCOPE_MISMATCH"
        private const val ERROR_CODE_LIST_BAD_REQUEST = "DEAD_LETTER_LIST_BAD_REQUEST"

        private const val ERROR_CODE_REPLAY_AUDITS_NOT_FOUND = "DEAD_LETTER_REPLAY_AUDITS_NOT_FOUND"
        private const val ERROR_CODE_REPLAY_AUDITS_CURSOR_INVALID = "DEAD_LETTER_REPLAY_AUDITS_CURSOR_INVALID"
        private const val ERROR_CODE_REPLAY_AUDITS_TENANT_SCOPE_REQUIRED = "DEAD_LETTER_REPLAY_AUDITS_TENANT_SCOPE_REQUIRED"
        private const val ERROR_CODE_REPLAY_AUDITS_BAD_REQUEST = "DEAD_LETTER_REPLAY_AUDITS_BAD_REQUEST"
    }

    @ExceptionHandler(DeadLetterReplayUnauthorizedException::class)
    fun handleReplayUnauthorized(ex: DeadLetterReplayUnauthorizedException): ResponseEntity<ProblemDetail> {
        return buildProblemResponse(
            status = HttpStatus.UNAUTHORIZED,
            title = "Unauthorized",
            detail = ex.message ?: "Replay authorization failed",
            type = "urn:sentinel:error:dead-letter-replay-unauthorized",
            scope = REPLAY_SCOPE,
            errorCode = ERROR_CODE_UNAUTHORIZED,
        )
    }

    @ExceptionHandler(DeadLetterReplayNotFoundException::class)
    fun handleReplayNotFound(ex: DeadLetterReplayNotFoundException): ResponseEntity<ProblemDetail> {
        return buildProblemResponse(
            status = HttpStatus.NOT_FOUND,
            title = "Not Found",
            detail = ex.message,
            type = "urn:sentinel:error:dead-letter-replay-not-found",
            scope = REPLAY_SCOPE,
            errorCode = ERROR_CODE_NOT_FOUND,
            properties = mapOf("deadLetterId" to ex.deadLetterId),
        )
    }

    @ExceptionHandler(DeadLetterReplayAuditsNotFoundException::class)
    fun handleReplayAuditsNotFound(ex: DeadLetterReplayAuditsNotFoundException): ResponseEntity<ProblemDetail> {
        return buildProblemResponse(
            status = HttpStatus.NOT_FOUND,
            title = "Not Found",
            detail = ex.message,
            type = "urn:sentinel:error:dead-letter-replay-audits-not-found",
            scope = REPLAY_AUDITS_SCOPE,
            errorCode = ERROR_CODE_REPLAY_AUDITS_NOT_FOUND,
            properties = mapOf("deadLetterId" to ex.deadLetterId),
        )
    }

    @ExceptionHandler(DeadLetterReplayBlockedException::class)
    fun handleReplayBlocked(ex: DeadLetterReplayBlockedException): ResponseEntity<ProblemDetail> {
        return buildProblemResponse(
            status = HttpStatus.CONFLICT,
            title = "Conflict",
            detail = ex.message,
            type = "urn:sentinel:error:dead-letter-replay-blocked",
            scope = REPLAY_SCOPE,
            errorCode = ERROR_CODE_BLOCKED,
            properties =
                mapOf(
                    "deadLetterId" to ex.deadLetterId,
                    "replayStatus" to ex.replayStatus.name,
                    "replayOutcome" to ex.replayOutcome.name,
                ),
        )
    }

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingRequestHeader(
        ex: MissingRequestHeaderException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        val scope = resolveScope(request.requestURI)
        val detail = "${ex.headerName} header is required"
        val errorContract = resolveErrorContract(scope = scope, detail = detail)
        return buildProblemResponse(
            status = HttpStatus.BAD_REQUEST,
            title = "Bad Request",
            detail = detail,
            type = errorContract.type,
            scope = scope,
            errorCode = errorContract.errorCode,
        )
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatusException(
        ex: ResponseStatusException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        val detail = ex.reason ?: ex.message ?: "Bad request"
        val status = HttpStatus.resolve(ex.statusCode.value()) ?: HttpStatus.BAD_REQUEST
        val scope = resolveScope(request.requestURI)
        val errorContract = resolveErrorContract(scope = scope, detail = detail)

        return buildProblemResponse(
            status = status,
            title = if (status == HttpStatus.BAD_REQUEST) "Bad Request" else status.reasonPhrase,
            detail = detail,
            type = errorContract.type,
            scope = scope,
            errorCode = errorContract.errorCode,
        )
    }

    private fun resolveScope(requestUri: String): String =
        when {
            requestUri.endsWith("/replay") -> REPLAY_SCOPE
            requestUri.endsWith("/replay-audits") -> REPLAY_AUDITS_SCOPE
            else -> LIST_SCOPE
        }

    private fun resolveErrorContract(
        scope: String,
        detail: String,
    ): DeadLetterErrorContract =
        when (scope) {
            LIST_SCOPE ->
                when {
                    detail == "Invalid cursor format" ->
                        DeadLetterErrorContract(
                            errorCode = ERROR_CODE_LIST_CURSOR_INVALID,
                            type = "urn:sentinel:error:dead-letter-list-cursor-invalid",
                        )
                    detail.endsWith("header is required") ->
                        DeadLetterErrorContract(
                            errorCode = ERROR_CODE_LIST_TENANT_SCOPE_REQUIRED,
                            type = "urn:sentinel:error:dead-letter-list-tenant-scope-required",
                        )
                    detail == "tenantId filter must match scoped tenant header" ->
                        DeadLetterErrorContract(
                            errorCode = ERROR_CODE_LIST_TENANT_SCOPE_MISMATCH,
                            type = "urn:sentinel:error:dead-letter-list-tenant-scope-mismatch",
                        )
                    else ->
                        DeadLetterErrorContract(
                            errorCode = ERROR_CODE_LIST_BAD_REQUEST,
                            type = "urn:sentinel:error:dead-letter-list-bad-request",
                        )
                }
            REPLAY_AUDITS_SCOPE ->
                when {
                    detail == "Invalid cursor format" ->
                        DeadLetterErrorContract(
                            errorCode = ERROR_CODE_REPLAY_AUDITS_CURSOR_INVALID,
                            type = "urn:sentinel:error:dead-letter-replay-audits-cursor-invalid",
                        )
                    detail.endsWith("header is required") ->
                        DeadLetterErrorContract(
                            errorCode = ERROR_CODE_REPLAY_AUDITS_TENANT_SCOPE_REQUIRED,
                            type = "urn:sentinel:error:dead-letter-replay-audits-tenant-scope-required",
                        )
                    else ->
                        DeadLetterErrorContract(
                            errorCode = ERROR_CODE_REPLAY_AUDITS_BAD_REQUEST,
                            type = "urn:sentinel:error:dead-letter-replay-audits-bad-request",
                        )
                }
            else ->
                when {
                    detail.startsWith("operatorNote exceeds maximum length") ->
                        DeadLetterErrorContract(
                            errorCode = ERROR_CODE_OPERATOR_NOTE_TOO_LONG,
                            type = "urn:sentinel:error:dead-letter-replay-operator-note-too-long",
                        )
                    detail == "Invalid cursor format" ->
                        DeadLetterErrorContract(
                            errorCode = ERROR_CODE_INVALID_CURSOR,
                            type = "urn:sentinel:error:dead-letter-api-cursor-invalid",
                        )
                    detail.endsWith("header is required") ->
                        DeadLetterErrorContract(
                            errorCode = ERROR_CODE_TENANT_SCOPE_REQUIRED,
                            type = "urn:sentinel:error:dead-letter-api-tenant-scope-required",
                        )
                    detail == "tenantId filter must match scoped tenant header" ->
                        DeadLetterErrorContract(
                            errorCode = ERROR_CODE_TENANT_SCOPE_MISMATCH,
                            type = "urn:sentinel:error:dead-letter-api-tenant-scope-mismatch",
                        )
                    else ->
                        DeadLetterErrorContract(
                            errorCode = ERROR_CODE_BAD_REQUEST,
                            type = "urn:sentinel:error:dead-letter-api-bad-request",
                        )
                }
        }

    private fun buildProblemResponse(
        status: HttpStatus,
        title: String,
        detail: String,
        type: String,
        scope: String,
        errorCode: String,
        properties: Map<String, Any> = emptyMap(),
    ): ResponseEntity<ProblemDetail> {
        val problemDetail = ProblemDetail.forStatusAndDetail(status, detail)
        problemDetail.title = title
        problemDetail.type = URI.create(type)
        problemDetail.setProperty("scope", scope)
        problemDetail.setProperty("errorCode", errorCode)
        properties.forEach { (key, value) ->
            problemDetail.setProperty(key, value)
        }

        return ResponseEntity
            .status(status)
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(problemDetail)
    }
}

private data class DeadLetterErrorContract(
    val errorCode: String,
    val type: String,
)
