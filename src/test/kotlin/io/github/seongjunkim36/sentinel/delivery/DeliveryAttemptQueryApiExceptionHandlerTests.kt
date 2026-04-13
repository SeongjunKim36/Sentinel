package io.github.seongjunkim36.sentinel.delivery

import java.time.Duration
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class DeliveryAttemptQueryApiExceptionHandlerTests {
    @Test
    fun `returns stabilized 429 contract when delivery-attempt query rate limit is exceeded`() {
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
                header { string("Cache-Control", "no-store") }
                jsonPath("$.title") { value("Too Many Requests") }
                jsonPath("$.type") { value("urn:sentinel:error:delivery-attempt-query-rate-limited") }
                jsonPath("$.scope") { value("delivery-attempt-query") }
                jsonPath("$.errorCode") { value("DELIVERY_ATTEMPT_QUERY_RATE_LIMITED") }
                jsonPath("$.retryAfterSeconds") { exists() }
            }.andReturn().response

        val retryAfter = response.getHeader("Retry-After")
        val retryAfterSecondsInBody =
            "\"retryAfterSeconds\"\\s*:\\s*(\\d+)".toRegex()
                .find(response.contentAsString)
                ?.groupValues
                ?.get(1)
                ?.toLong()

        assertThat(retryAfter).isNotBlank()
        assertThat(retryAfter!!.toLong()).isGreaterThan(0L)
        assertThat(retryAfterSecondsInBody).isEqualTo(retryAfter.toLong())
    }

    @Test
    fun `returns stabilized 503 contract when distributed limiter is unavailable with fail-closed mode`() {
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
                                        distributed =
                                            DeliveryAttemptQueryDistributedRateLimitProperties(
                                                enabled = true,
                                                failOpen = false,
                                            ),
                                    ),
                            ),
                        distributedRateLimiter = FailingDistributedRateLimiter,
                    ),
            )
        val mockMvc =
            MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(DeliveryAttemptQueryApiExceptionHandler())
                .build()

        val response =
            mockMvc.get("/api/v1/delivery-attempts") {
                header("X-Sentinel-Tenant-Id", "tenant-alpha")
            }.andExpect {
                status { isServiceUnavailable() }
                header { doesNotExist("Retry-After") }
                header { string("Cache-Control", "no-store") }
                jsonPath("$.title") { value("Service Unavailable") }
                jsonPath("$.type") { value("urn:sentinel:error:delivery-attempt-query-rate-limit-unavailable") }
                jsonPath("$.scope") { value("delivery-attempt-query") }
                jsonPath("$.errorCode") { value("DELIVERY_ATTEMPT_QUERY_RATE_LIMIT_UNAVAILABLE") }
            }.andReturn().response

        assertThat(response.contentAsString).contains("Distributed delivery-attempt query rate limiter is unavailable.")
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

    private object FailingDistributedRateLimiter : DeliveryAttemptQueryDistributedRateLimiter {
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
