package io.github.seongjunkim36.sentinel.delivery

import java.net.URI
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice(assignableTypes = [DeliveryAttemptQueryController::class])
class DeliveryAttemptQueryApiExceptionHandler {
    companion object {
        private const val SCOPE = "delivery-attempt-query"
        private const val UNAUTHORIZED_ERROR_CODE = "DELIVERY_ATTEMPT_QUERY_UNAUTHORIZED"
        private const val TENANT_SCOPE_REQUIRED_ERROR_CODE = "DELIVERY_ATTEMPT_QUERY_TENANT_SCOPE_REQUIRED"
        private const val TENANT_SCOPE_MISMATCH_ERROR_CODE = "DELIVERY_ATTEMPT_QUERY_TENANT_SCOPE_MISMATCH"
        private const val CURSOR_INVALID_ERROR_CODE = "DELIVERY_ATTEMPT_QUERY_CURSOR_INVALID"
        private const val LIMIT_OUT_OF_RANGE_ERROR_CODE = "DELIVERY_ATTEMPT_QUERY_LIMIT_OUT_OF_RANGE"
        private const val PARAMETER_INVALID_ERROR_CODE = "DELIVERY_ATTEMPT_QUERY_PARAMETER_INVALID"
        private const val BAD_REQUEST_ERROR_CODE = "DELIVERY_ATTEMPT_QUERY_BAD_REQUEST"
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

    @ExceptionHandler(DeliveryAttemptQueryValidationException::class)
    fun handleQueryValidation(ex: DeliveryAttemptQueryValidationException): ResponseEntity<ProblemDetail> =
        buildProblemResponse(
            status = HttpStatus.BAD_REQUEST,
            title = "Bad Request",
            detail = ex.message,
            contract = resolveValidationContract(ex.reason, ex.parameter),
        )

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingRequestHeader(ex: MissingRequestHeaderException): ResponseEntity<ProblemDetail> {
        val reason =
            if (ex.headerName == "X-Sentinel-Tenant-Id") {
                DeliveryAttemptQueryValidationReason.TENANT_SCOPE_REQUIRED
            } else {
                DeliveryAttemptQueryValidationReason.BAD_REQUEST
            }
        val detail = "${ex.headerName} header is required"
        return buildProblemResponse(
            status = HttpStatus.BAD_REQUEST,
            title = "Bad Request",
            detail = detail,
            contract = resolveValidationContract(reason, ex.headerName),
        )
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleMethodArgumentTypeMismatch(ex: MethodArgumentTypeMismatchException): ResponseEntity<ProblemDetail> {
        val detail =
            when (ex.name) {
                "eventId" -> "eventId must be a valid UUID"
                "success" -> "success must be true or false"
                "limit" -> "limit must be a valid integer"
                else -> "${ex.name} parameter has an invalid value"
            }
        return buildProblemResponse(
            status = HttpStatus.BAD_REQUEST,
            title = "Bad Request",
            detail = detail,
            contract = resolveValidationContract(DeliveryAttemptQueryValidationReason.PARAMETER_INVALID, ex.name),
        )
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

    private fun resolveValidationContract(
        reason: DeliveryAttemptQueryValidationReason,
        parameter: String? = null,
    ): DeliveryAttemptQueryErrorContract =
        when (reason) {
            DeliveryAttemptQueryValidationReason.TENANT_SCOPE_REQUIRED ->
                DeliveryAttemptQueryErrorContract(
                    errorCode = TENANT_SCOPE_REQUIRED_ERROR_CODE,
                    type = "urn:sentinel:error:delivery-attempt-query-tenant-scope-required",
                )
            DeliveryAttemptQueryValidationReason.TENANT_SCOPE_MISMATCH ->
                DeliveryAttemptQueryErrorContract(
                    errorCode = TENANT_SCOPE_MISMATCH_ERROR_CODE,
                    type = "urn:sentinel:error:delivery-attempt-query-tenant-scope-mismatch",
                )
            DeliveryAttemptQueryValidationReason.CURSOR_INVALID ->
                DeliveryAttemptQueryErrorContract(
                    errorCode = CURSOR_INVALID_ERROR_CODE,
                    type = "urn:sentinel:error:delivery-attempt-query-cursor-invalid",
                )
            DeliveryAttemptQueryValidationReason.LIMIT_OUT_OF_RANGE ->
                DeliveryAttemptQueryErrorContract(
                    errorCode = LIMIT_OUT_OF_RANGE_ERROR_CODE,
                    type = "urn:sentinel:error:delivery-attempt-query-limit-out-of-range",
                    properties = mapOf("parameter" to (parameter ?: "limit")),
                )
            DeliveryAttemptQueryValidationReason.PARAMETER_INVALID ->
                DeliveryAttemptQueryErrorContract(
                    errorCode = PARAMETER_INVALID_ERROR_CODE,
                    type = "urn:sentinel:error:delivery-attempt-query-parameter-invalid",
                    properties = parameter?.let { mapOf("parameter" to it) } ?: emptyMap(),
                )
            DeliveryAttemptQueryValidationReason.BAD_REQUEST ->
                DeliveryAttemptQueryErrorContract(
                    errorCode = BAD_REQUEST_ERROR_CODE,
                    type = "urn:sentinel:error:delivery-attempt-query-bad-request",
                )
        }

    private fun buildProblemResponse(
        status: HttpStatus,
        title: String,
        detail: String,
        contract: DeliveryAttemptQueryErrorContract,
    ): ResponseEntity<ProblemDetail> {
        val problemDetail = ProblemDetail.forStatusAndDetail(status, detail)
        problemDetail.title = title
        problemDetail.type = URI.create(contract.type)
        problemDetail.setProperty("scope", SCOPE)
        problemDetail.setProperty("errorCode", contract.errorCode)
        contract.properties.forEach { (key, value) ->
            problemDetail.setProperty(key, value)
        }

        return ResponseEntity
            .status(status)
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(problemDetail)
    }
}

private data class DeliveryAttemptQueryErrorContract(
    val errorCode: String,
    val type: String,
    val properties: Map<String, Any> = emptyMap(),
)
