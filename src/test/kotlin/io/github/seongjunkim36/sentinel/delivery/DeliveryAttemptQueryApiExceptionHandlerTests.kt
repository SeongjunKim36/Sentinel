package io.github.seongjunkim36.sentinel.delivery

import java.time.Duration
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class DeliveryAttemptQueryApiExceptionHandlerTests {
    @Test
    fun `returns retry-after header when delivery-attempt query rate limit is exceeded`() {
        val controller =
            DeliveryAttemptQueryController(
                deliveryAttemptStore = EmptyDeliveryAttemptStore,
                deliveryAttemptQueryAuthorizationService =
                    DeliveryAttemptQueryAuthorizationService(
                        deliveryApiProperties = DeliveryApiProperties(),
                    ),
                deliveryAttemptQueryRateLimitService =
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
                    ),
            )
        val mockMvc =
            MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(DeliveryAttemptQueryApiExceptionHandler())
                .build()

        mockMvc.get("/api/v1/delivery-attempts") {
            header("X-Sentinel-Tenant-Id", "tenant-alpha")
        }.andExpect {
            status { isOk() }
        }

        val response =
            mockMvc.get("/api/v1/delivery-attempts") {
                header("X-Sentinel-Tenant-Id", "tenant-alpha")
            }.andExpect {
                status { isTooManyRequests() }
                header { exists("Retry-After") }
            }.andReturn().response

        val retryAfter = response.getHeader("Retry-After")
        assertThat(retryAfter).isNotBlank()
        assertThat(retryAfter!!.toLong()).isGreaterThan(0L)
        assertThat(response.contentAsString).contains("retryAfterSeconds")
    }

    private object EmptyDeliveryAttemptStore : DeliveryAttemptStore {
        override fun record(attempt: DeliveryAttemptWrite) {
        }

        override fun findRecent(query: DeliveryAttemptQuery): List<DeliveryAttemptRecord> = emptyList()
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
}
