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
}
