package io.github.seongjunkim36.sentinel.delivery

import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.time.Duration
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DeliveryAttemptQueryRateLimitServiceTests {
    private lateinit var meterRegistry: SimpleMeterRegistry

    @BeforeEach
    fun setUp() {
        meterRegistry = SimpleMeterRegistry()
        Metrics.addRegistry(meterRegistry)
    }

    @AfterEach
    fun tearDown() {
        Metrics.removeRegistry(meterRegistry)
        meterRegistry.close()
    }

    @Test
    fun `allows requests again after rate-limit window passes`() {
        val service =
            DeliveryAttemptQueryRateLimitService(
                deliveryApiProperties =
                    DeliveryApiProperties(
                        queryRateLimit =
                            DeliveryAttemptQueryRateLimitProperties(
                                enabled = true,
                                maxRequests = 2,
                                window = Duration.ofSeconds(30),
                            ),
                    ),
                distributedRateLimiter = NoOpDistributedRateLimiter,
            )
        val start = Instant.parse("2026-04-01T00:00:00Z")

        service.enforceRateLimitOrThrow(tenantId = "tenant-alpha", now = start)
        service.enforceRateLimitOrThrow(tenantId = "tenant-alpha", now = start.plusSeconds(5))
        val blockedException =
            kotlin.runCatching {
                service.enforceRateLimitOrThrow(tenantId = "tenant-alpha", now = start.plusSeconds(10))
            }.exceptionOrNull()

        assertThat(blockedException).isInstanceOf(DeliveryAttemptQueryRateLimitExceededException::class.java)

        val afterWindow =
            kotlin.runCatching {
                service.enforceRateLimitOrThrow(tenantId = "tenant-alpha", now = start.plusSeconds(35))
            }.exceptionOrNull()

        assertThat(afterWindow).isNull()
    }

    @Test
    fun `records allowed and rate-limited outcomes`() {
        val service =
            DeliveryAttemptQueryRateLimitService(
                deliveryApiProperties =
                    DeliveryApiProperties(
                        queryRateLimit =
                            DeliveryAttemptQueryRateLimitProperties(
                                enabled = true,
                                maxRequests = 1,
                                window = Duration.ofMinutes(1),
                            ),
                    ),
                distributedRateLimiter = NoOpDistributedRateLimiter,
            )
        val now = Instant.parse("2026-04-01T00:00:00Z")

        service.enforceRateLimitOrThrow(tenantId = "tenant-alpha", now = now)
        kotlin.runCatching {
            service.enforceRateLimitOrThrow(tenantId = "tenant-alpha", now = now.plusSeconds(1))
        }

        val allowedCount =
            meterRegistry
                .find("sentinel.delivery.api.query.requests")
                .tags(
                    "tenant_id",
                    "tenant-alpha",
                    "outcome",
                    "allowed",
                ).counter()
                ?.count()
        val rateLimitedCount =
            meterRegistry
                .find("sentinel.delivery.api.query.requests")
                .tags(
                    "tenant_id",
                    "tenant-alpha",
                    "outcome",
                    "rate_limited",
                ).counter()
                ?.count()

        assertThat(allowedCount).isEqualTo(1.0)
        assertThat(rateLimitedCount).isEqualTo(1.0)
    }

    @Test
    fun `records allowed outcome when rate limit is disabled`() {
        val service =
            DeliveryAttemptQueryRateLimitService(
                deliveryApiProperties =
                    DeliveryApiProperties(
                        queryRateLimit =
                            DeliveryAttemptQueryRateLimitProperties(
                                enabled = false,
                            ),
                    ),
                distributedRateLimiter = NoOpDistributedRateLimiter,
            )

        service.enforceRateLimitOrThrow(tenantId = "tenant-alpha", now = Instant.parse("2026-04-01T00:00:00Z"))
        service.enforceRateLimitOrThrow(tenantId = "tenant-alpha", now = Instant.parse("2026-04-01T00:00:01Z"))

        val allowedCount =
            meterRegistry
                .find("sentinel.delivery.api.query.requests")
                .tags(
                    "tenant_id",
                    "tenant-alpha",
                    "outcome",
                    "allowed",
                ).counter()
                ?.count()

        assertThat(allowedCount).isEqualTo(2.0)
    }

    @Test
    fun `uses distributed limiter decision when distributed mode is enabled`() {
        val distributedLimiter =
            RecordingDistributedRateLimiter(
                result =
                    DeliveryAttemptQueryDistributedRateLimitDecision(
                        allowed = false,
                        retryAfterSeconds = 17L,
                    ),
            )
        val service =
            DeliveryAttemptQueryRateLimitService(
                deliveryApiProperties =
                    DeliveryApiProperties(
                        queryRateLimit =
                            DeliveryAttemptQueryRateLimitProperties(
                                enabled = true,
                                maxRequests = 10,
                                window = Duration.ofMinutes(1),
                                distributed =
                                    DeliveryAttemptQueryDistributedRateLimitProperties(
                                        enabled = true,
                                        keyPrefix = "sentinel:test",
                                    ),
                            ),
                    ),
                distributedRateLimiter = distributedLimiter,
            )

        val exception =
            kotlin.runCatching {
                service.enforceRateLimitOrThrow(tenantId = "tenant-alpha", now = Instant.parse("2026-04-01T00:00:00Z"))
            }.exceptionOrNull()

        assertThat(exception).isInstanceOf(DeliveryAttemptQueryRateLimitExceededException::class.java)
        val rateLimitException = exception as DeliveryAttemptQueryRateLimitExceededException
        assertThat(rateLimitException.retryAfterSeconds).isEqualTo(17L)
        assertThat(distributedLimiter.invocationCount).isEqualTo(1)
    }

    @Test
    fun `allows request with degraded_allow outcome when distributed limiter fails and fail-open is enabled`() {
        val service =
            DeliveryAttemptQueryRateLimitService(
                deliveryApiProperties =
                    DeliveryApiProperties(
                        queryRateLimit =
                            DeliveryAttemptQueryRateLimitProperties(
                                enabled = true,
                                distributed =
                                    DeliveryAttemptQueryDistributedRateLimitProperties(
                                        enabled = true,
                                        failOpen = true,
                                    ),
                            ),
                    ),
                distributedRateLimiter = FailingDistributedRateLimiter(),
            )

        val exception =
            kotlin.runCatching {
                service.enforceRateLimitOrThrow(tenantId = "tenant-alpha", now = Instant.parse("2026-04-01T00:00:00Z"))
            }.exceptionOrNull()

        assertThat(exception).isNull()
        val degradedAllowCount =
            meterRegistry
                .find("sentinel.delivery.api.query.requests")
                .tags(
                    "tenant_id",
                    "tenant-alpha",
                    "outcome",
                    "degraded_allow",
                ).counter()
                ?.count()
        assertThat(degradedAllowCount).isEqualTo(1.0)
    }

    @Test
    fun `rejects request when distributed limiter fails and fail-open is disabled`() {
        val service =
            DeliveryAttemptQueryRateLimitService(
                deliveryApiProperties =
                    DeliveryApiProperties(
                        queryRateLimit =
                            DeliveryAttemptQueryRateLimitProperties(
                                enabled = true,
                                distributed =
                                    DeliveryAttemptQueryDistributedRateLimitProperties(
                                        enabled = true,
                                        failOpen = false,
                                    ),
                            ),
                    ),
                distributedRateLimiter = FailingDistributedRateLimiter(),
            )

        val exception =
            kotlin.runCatching {
                service.enforceRateLimitOrThrow(tenantId = "tenant-alpha", now = Instant.parse("2026-04-01T00:00:00Z"))
            }.exceptionOrNull()

        assertThat(exception).isInstanceOf(DeliveryAttemptQueryRateLimitUnavailableException::class.java)
    }

    private object NoOpDistributedRateLimiter : DeliveryAttemptQueryDistributedRateLimiter {
        override fun acquire(
            tenantId: String,
            maxRequests: Int,
            window: Duration,
            keyPrefix: String,
            now: Instant,
        ): DeliveryAttemptQueryDistributedRateLimitDecision =
            DeliveryAttemptQueryDistributedRateLimitDecision(
                allowed = true,
                retryAfterSeconds = 0L,
            )
    }

    private class RecordingDistributedRateLimiter(
        private val result: DeliveryAttemptQueryDistributedRateLimitDecision,
    ) : DeliveryAttemptQueryDistributedRateLimiter {
        var invocationCount: Int = 0
            private set

        override fun acquire(
            tenantId: String,
            maxRequests: Int,
            window: Duration,
            keyPrefix: String,
            now: Instant,
        ): DeliveryAttemptQueryDistributedRateLimitDecision {
            invocationCount += 1
            return result
        }
    }

    private class FailingDistributedRateLimiter : DeliveryAttemptQueryDistributedRateLimiter {
        override fun acquire(
            tenantId: String,
            maxRequests: Int,
            window: Duration,
            keyPrefix: String,
            now: Instant,
        ): DeliveryAttemptQueryDistributedRateLimitDecision {
            throw IllegalStateException("redis unavailable")
        }
    }
}
