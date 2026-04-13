package io.github.seongjunkim36.sentinel.delivery

import java.time.Duration
import java.time.Instant

interface DeliveryAttemptQueryDistributedRateLimiter {
    fun acquire(
        tenantId: String,
        maxRequests: Int,
        window: Duration,
        keyPrefix: String,
        now: Instant,
    ): DeliveryAttemptQueryDistributedRateLimitDecision
}

data class DeliveryAttemptQueryDistributedRateLimitDecision(
    val allowed: Boolean,
    val retryAfterSeconds: Long,
)
