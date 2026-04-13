package io.github.seongjunkim36.sentinel.delivery

import java.time.Duration
import java.time.Instant
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Component
@ConditionalOnBean(StringRedisTemplate::class)
@ConditionalOnProperty(
    prefix = "sentinel.delivery.api.query-rate-limit.distributed",
    name = ["enabled"],
    havingValue = "true",
)
class RedisDeliveryAttemptQueryDistributedRateLimiter(
    private val stringRedisTemplate: StringRedisTemplate,
) : DeliveryAttemptQueryDistributedRateLimiter {
    override fun acquire(
        tenantId: String,
        maxRequests: Int,
        window: Duration,
        keyPrefix: String,
        now: Instant,
    ): DeliveryAttemptQueryDistributedRateLimitDecision {
        val windowMillis = window.toMillis().coerceAtLeast(1_000)
        val normalizedTenantId = tenantId.trim()
        val slot = now.toEpochMilli() / windowMillis
        val key = "$keyPrefix:$normalizedTenantId:$slot"

        val count = stringRedisTemplate.opsForValue().increment(key) ?: 0L
        if (count <= 0L) {
            throw IllegalStateException("Redis distributed limiter returned non-positive counter value")
        }
        if (count == 1L) {
            // Keep slot keys only for the active window plus a small safety margin.
            stringRedisTemplate.expire(key, window.plusSeconds(1))
        }

        if (count <= maxRequests.toLong()) {
            return DeliveryAttemptQueryDistributedRateLimitDecision(
                allowed = true,
                retryAfterSeconds = 0L,
            )
        }

        val windowEndMillis = (slot + 1) * windowMillis
        val remainingMillis = (windowEndMillis - now.toEpochMilli()).coerceAtLeast(0)
        val retryAfterSeconds = ((remainingMillis + 999) / 1_000).coerceAtLeast(1)

        return DeliveryAttemptQueryDistributedRateLimitDecision(
            allowed = false,
            retryAfterSeconds = retryAfterSeconds,
        )
    }
}
