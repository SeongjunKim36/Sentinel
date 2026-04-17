package io.github.seongjunkim36.sentinel.ingestion.api

import io.github.seongjunkim36.sentinel.ingestion.application.SourcePollingValidationException
import io.github.seongjunkim36.sentinel.ingestion.application.UnsupportedSourceTypeException
import java.net.URI
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

@RestControllerAdvice(assignableTypes = [SourcePollingController::class])
class SourcePollingApiExceptionHandler {
    companion object {
        private const val SCOPE = "source-polling"
        private const val SOURCE_PLUGIN_NOT_FOUND = "SOURCE_PLUGIN_NOT_FOUND"
        private const val SOURCE_POLL_REQUEST_INVALID = "SOURCE_POLL_REQUEST_INVALID"
        private const val SOURCE_POLL_BAD_REQUEST = "SOURCE_POLL_BAD_REQUEST"
    }

    @ExceptionHandler(UnsupportedSourceTypeException::class)
    fun handleUnsupportedSourceType(ex: UnsupportedSourceTypeException): ResponseEntity<ProblemDetail> =
        buildProblemResponse(
            status = HttpStatus.NOT_FOUND,
            title = "Not Found",
            detail = ex.message ?: "Source plugin not found",
            type = "urn:sentinel:error:source-plugin-not-found",
            errorCode = SOURCE_PLUGIN_NOT_FOUND,
        )

    @ExceptionHandler(SourcePollingValidationException::class)
    fun handleSourcePollingValidation(ex: SourcePollingValidationException): ResponseEntity<ProblemDetail> =
        buildProblemResponse(
            status = HttpStatus.BAD_REQUEST,
            title = "Bad Request",
            detail = ex.message,
            type = "urn:sentinel:error:source-poll-request-invalid",
            errorCode = SOURCE_POLL_REQUEST_INVALID,
            properties = mapOf("sourceType" to ex.sourceType),
        )

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingRequestHeader(ex: MissingRequestHeaderException): ResponseEntity<ProblemDetail> =
        buildProblemResponse(
            status = HttpStatus.BAD_REQUEST,
            title = "Bad Request",
            detail = "${ex.headerName} header is required",
            type = "urn:sentinel:error:source-poll-bad-request",
            errorCode = SOURCE_POLL_BAD_REQUEST,
        )

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatusException(ex: ResponseStatusException): ResponseEntity<ProblemDetail> {
        val status = HttpStatus.resolve(ex.statusCode.value()) ?: HttpStatus.BAD_REQUEST
        val title = if (status == HttpStatus.BAD_REQUEST) "Bad Request" else status.reasonPhrase
        return buildProblemResponse(
            status = status,
            title = title,
            detail = ex.reason ?: ex.message ?: title,
            type = "urn:sentinel:error:source-poll-bad-request",
            errorCode = SOURCE_POLL_BAD_REQUEST,
        )
    }

    private fun buildProblemResponse(
        status: HttpStatus,
        title: String,
        detail: String,
        type: String,
        errorCode: String,
        properties: Map<String, Any> = emptyMap(),
    ): ResponseEntity<ProblemDetail> {
        val problemDetail = ProblemDetail.forStatusAndDetail(status, detail)
        problemDetail.title = title
        problemDetail.type = URI.create(type)
        problemDetail.setProperty("scope", SCOPE)
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
