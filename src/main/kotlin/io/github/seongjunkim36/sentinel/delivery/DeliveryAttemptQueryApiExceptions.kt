package io.github.seongjunkim36.sentinel.delivery

enum class DeliveryAttemptQueryValidationReason {
    TENANT_SCOPE_REQUIRED,
    TENANT_SCOPE_MISMATCH,
    CURSOR_INVALID,
    LIMIT_OUT_OF_RANGE,
    PARAMETER_INVALID,
    BAD_REQUEST,
}

class DeliveryAttemptQueryValidationException(
    val reason: DeliveryAttemptQueryValidationReason,
    override val message: String,
    val parameter: String? = null,
) : RuntimeException(message)
