package io.github.seongjunkim36.sentinel.delivery

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.ResponseStatus

@Component
class DeliveryAttemptQueryRateLimitService(
    private val deliveryApiProperties: DeliveryApiProperties,
) {
    private val requestWindowsByTenant = ConcurrentHashMap<String, DeliveryAttemptQueryRateLimitWindow>()
    private val cleanupCounter = AtomicInteger()

    init {
        val queryRateLimit = deliveryApiProperties.queryRateLimit
        if (queryRateLimit.enabled) {
            require(queryRateLimit.maxRequests > 0) {
                "sentinel.delivery.api.query-rate-limit.max-requests must be greater than zero when rate limit is enabled"
            }
            require(queryRateLimit.window > Duration.ZERO) {
                "sentinel.delivery.api.query-rate-limit.window must be greater than zero when rate limit is enabled"
            }
        }
    }

    fun enforceRateLimitOrThrow(tenantId: String) {
        enforceRateLimitOrThrow(tenantId = tenantId, now = Instant.now())
    }

    internal fun enforceRateLimitOrThrow(
        tenantId: String,
        now: Instant,
    ) {
        val normalizedTenantId = tenantId.trim()
        val queryRateLimit = deliveryApiProperties.queryRateLimit
        if (!queryRateLimit.enabled) {
            DeliveryApiMetrics.recordDeliveryAttemptQuery(
                tenantId = normalizedTenantId,
                outcome = "allowed",
            )
            return
        }

        val window = queryRateLimit.window.coerceAtLeast(Duration.ofSeconds(1))
        val maxRequests = queryRateLimit.maxRequests.coerceAtLeast(1)
        var allowed = false
        var retryAfterSeconds = 1L

        requestWindowsByTenant.compute(normalizedTenantId) { _, currentWindow ->
            val boundary = currentWindow?.windowStartedAt?.plus(window)
            val shouldStartNewWindow = currentWindow == null || (boundary != null && !now.isBefore(boundary))
            val updatedWindow =
                if (shouldStartNewWindow) {
                    DeliveryAttemptQueryRateLimitWindow(windowStartedAt = now, requestCount = 1)
                } else {
                    DeliveryAttemptQueryRateLimitWindow(
                        windowStartedAt = currentWindow.windowStartedAt,
                        requestCount = currentWindow.requestCount + 1,
                    )
                }
            allowed = updatedWindow.requestCount <= maxRequests
            if (!allowed) {
                retryAfterSeconds = retryAfterSeconds(updatedWindow.windowStartedAt, window, now)
            }
            updatedWindow
        }

        DeliveryApiMetrics.recordDeliveryAttemptQuery(
            tenantId = normalizedTenantId,
            outcome = if (allowed) "allowed" else "rate_limited",
        )

        // Remove stale tenant windows periodically so long-lived processes do not accumulate idle keys forever.
        cleanupExpiredWindows(now = now, window = window)

        if (!allowed) {
            throw DeliveryAttemptQueryRateLimitExceededException(retryAfterSeconds = retryAfterSeconds)
        }
    }

    private fun cleanupExpiredWindows(
        now: Instant,
        window: Duration,
    ) {
        if (cleanupCounter.incrementAndGet() % 100 != 0) {
            return
        }

        requestWindowsByTenant.entries.removeIf { (_, windowState) ->
            val windowEnd = windowState.windowStartedAt.plus(window)
            !now.isBefore(windowEnd)
        }
    }

    private fun retryAfterSeconds(
        windowStartedAt: Instant,
        window: Duration,
        now: Instant,
    ): Long {
        val windowEnd = windowStartedAt.plus(window)
        if (!now.isBefore(windowEnd)) {
            return 1
        }
        val remainingMillis = Duration.between(now, windowEnd).toMillis().coerceAtLeast(0)
        return ((remainingMillis + 999) / 1_000).coerceAtLeast(1)
    }
}

private data class DeliveryAttemptQueryRateLimitWindow(
    val windowStartedAt: Instant,
    val requestCount: Int,
)

@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
class DeliveryAttemptQueryRateLimitExceededException(
    val retryAfterSeconds: Long,
    override val message: String = "Delivery-attempt query rate limit exceeded. Retry after $retryAfterSeconds seconds.",
) : RuntimeException(message)
