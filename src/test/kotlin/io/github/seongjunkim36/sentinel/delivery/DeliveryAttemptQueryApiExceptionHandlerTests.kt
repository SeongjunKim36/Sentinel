package io.github.seongjunkim36.sentinel.delivery

import java.time.Duration
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class DeliveryAttemptQueryApiExceptionHandlerTests {
    @Test
    fun `returns stabilized 401 contract when delivery-attempt query authorization header is missing`() {
        val mockMvc =
            deliveryAttemptQueryMockMvc(
                apiProperties =
                    DeliveryApiProperties(
                        queryAuthorization =
                            DeliveryAttemptQueryAuthorizationProperties(
                                enabled = true,
                                token = "query-secret",
                            ),
                    ),
            )

        mockMvc.get("/api/v1/delivery-attempts") {
            header("X-Sentinel-Tenant-Id", "tenant-alpha")
        }.andExpect {
            status { isUnauthorized() }
            header { string("Cache-Control", "no-store") }
            jsonPath("$.title") { value("Unauthorized") }
            jsonPath("$.type") { value("urn:sentinel:error:delivery-attempt-query-unauthorized") }
            jsonPath("$.scope") { value("delivery-attempt-query") }
            jsonPath("$.errorCode") { value("DELIVERY_ATTEMPT_QUERY_UNAUTHORIZED") }
            jsonPath("$.detail") { value("Missing delivery-attempt query authorization header") }
        }
    }

    @Test
    fun `returns stabilized 401 contract when delivery-attempt query authorization token is invalid`() {
        val mockMvc =
            deliveryAttemptQueryMockMvc(
                apiProperties =
                    DeliveryApiProperties(
                        queryAuthorization =
                            DeliveryAttemptQueryAuthorizationProperties(
                                enabled = true,
                                token = "query-secret",
                            ),
                    ),
            )

        mockMvc.get("/api/v1/delivery-attempts") {
            header("X-Sentinel-Tenant-Id", "tenant-alpha")
            header("X-Sentinel-Query-Token", "wrong-token")
        }.andExpect {
            status { isUnauthorized() }
            header { string("Cache-Control", "no-store") }
            jsonPath("$.title") { value("Unauthorized") }
            jsonPath("$.type") { value("urn:sentinel:error:delivery-attempt-query-unauthorized") }
            jsonPath("$.scope") { value("delivery-attempt-query") }
            jsonPath("$.errorCode") { value("DELIVERY_ATTEMPT_QUERY_UNAUTHORIZED") }
            jsonPath("$.detail") { value("Invalid delivery-attempt query authorization token") }
        }
    }

    @Test
    fun `returns stabilized 400 contract when tenant scope header is missing`() {
        val mockMvc = deliveryAttemptQueryMockMvc()

        mockMvc.get("/api/v1/delivery-attempts").andExpect {
            status { isBadRequest() }
            header { string("Cache-Control", "no-store") }
            jsonPath("$.title") { value("Bad Request") }
            jsonPath("$.type") { value("urn:sentinel:error:delivery-attempt-query-tenant-scope-required") }
            jsonPath("$.scope") { value("delivery-attempt-query") }
            jsonPath("$.errorCode") { value("DELIVERY_ATTEMPT_QUERY_TENANT_SCOPE_REQUIRED") }
            jsonPath("$.detail") { value("X-Sentinel-Tenant-Id header is required") }
        }
    }

    @Test
    fun `returns stabilized 400 contract when tenant scope header is blank`() {
        val mockMvc = deliveryAttemptQueryMockMvc()

        mockMvc.get("/api/v1/delivery-attempts") {
            header("X-Sentinel-Tenant-Id", "   ")
        }.andExpect {
            status { isBadRequest() }
            header { string("Cache-Control", "no-store") }
            jsonPath("$.title") { value("Bad Request") }
            jsonPath("$.type") { value("urn:sentinel:error:delivery-attempt-query-tenant-scope-required") }
            jsonPath("$.scope") { value("delivery-attempt-query") }
            jsonPath("$.errorCode") { value("DELIVERY_ATTEMPT_QUERY_TENANT_SCOPE_REQUIRED") }
            jsonPath("$.detail") { value("X-Sentinel-Tenant-Id header is required") }
        }
    }

    @Test
    fun `returns stabilized 400 contract when tenantId filter mismatches scoped tenant`() {
        val mockMvc = deliveryAttemptQueryMockMvc()

        mockMvc.get("/api/v1/delivery-attempts") {
            header("X-Sentinel-Tenant-Id", "tenant-alpha")
            param("tenantId", "tenant-beta")
        }.andExpect {
            status { isBadRequest() }
            header { string("Cache-Control", "no-store") }
            jsonPath("$.title") { value("Bad Request") }
            jsonPath("$.type") { value("urn:sentinel:error:delivery-attempt-query-tenant-scope-mismatch") }
            jsonPath("$.scope") { value("delivery-attempt-query") }
            jsonPath("$.errorCode") { value("DELIVERY_ATTEMPT_QUERY_TENANT_SCOPE_MISMATCH") }
            jsonPath("$.detail") { value("tenantId filter must match scoped tenant header") }
        }
    }

    @Test
    fun `returns stabilized 400 contract when cursor format is invalid`() {
        val mockMvc = deliveryAttemptQueryMockMvc()

        mockMvc.get("/api/v1/delivery-attempts") {
            header("X-Sentinel-Tenant-Id", "tenant-alpha")
            param("cursor", "invalid-cursor")
        }.andExpect {
            status { isBadRequest() }
            header { string("Cache-Control", "no-store") }
            jsonPath("$.title") { value("Bad Request") }
            jsonPath("$.type") { value("urn:sentinel:error:delivery-attempt-query-cursor-invalid") }
            jsonPath("$.scope") { value("delivery-attempt-query") }
            jsonPath("$.errorCode") { value("DELIVERY_ATTEMPT_QUERY_CURSOR_INVALID") }
            jsonPath("$.detail") { value("Invalid cursor format") }
        }
    }

    @Test
    fun `returns stabilized 400 contract when limit is outside supported range`() {
        val mockMvc = deliveryAttemptQueryMockMvc()

        mockMvc.get("/api/v1/delivery-attempts") {
            header("X-Sentinel-Tenant-Id", "tenant-alpha")
            param("limit", "201")
        }.andExpect {
            status { isBadRequest() }
            header { string("Cache-Control", "no-store") }
            jsonPath("$.title") { value("Bad Request") }
            jsonPath("$.type") { value("urn:sentinel:error:delivery-attempt-query-limit-out-of-range") }
            jsonPath("$.scope") { value("delivery-attempt-query") }
            jsonPath("$.errorCode") { value("DELIVERY_ATTEMPT_QUERY_LIMIT_OUT_OF_RANGE") }
            jsonPath("$.parameter") { value("limit") }
            jsonPath("$.detail") { value("limit must be between 1 and 200") }
        }
    }

    @Test
    fun `returns stabilized 400 contract when eventId parameter is malformed`() {
        val mockMvc = deliveryAttemptQueryMockMvc()

        mockMvc.get("/api/v1/delivery-attempts") {
            header("X-Sentinel-Tenant-Id", "tenant-alpha")
            param("eventId", "not-a-uuid")
        }.andExpect {
            status { isBadRequest() }
            header { string("Cache-Control", "no-store") }
            jsonPath("$.title") { value("Bad Request") }
            jsonPath("$.type") { value("urn:sentinel:error:delivery-attempt-query-parameter-invalid") }
            jsonPath("$.scope") { value("delivery-attempt-query") }
            jsonPath("$.errorCode") { value("DELIVERY_ATTEMPT_QUERY_PARAMETER_INVALID") }
            jsonPath("$.parameter") { value("eventId") }
            jsonPath("$.detail") { value("eventId must be a valid UUID") }
        }
    }

    @Test
    fun `returns stabilized 400 contract when success parameter is malformed`() {
        val mockMvc = deliveryAttemptQueryMockMvc()

        mockMvc.get("/api/v1/delivery-attempts") {
            header("X-Sentinel-Tenant-Id", "tenant-alpha")
            param("success", "not-a-boolean")
        }.andExpect {
            status { isBadRequest() }
            header { string("Cache-Control", "no-store") }
            jsonPath("$.title") { value("Bad Request") }
            jsonPath("$.type") { value("urn:sentinel:error:delivery-attempt-query-parameter-invalid") }
            jsonPath("$.scope") { value("delivery-attempt-query") }
            jsonPath("$.errorCode") { value("DELIVERY_ATTEMPT_QUERY_PARAMETER_INVALID") }
            jsonPath("$.parameter") { value("success") }
            jsonPath("$.detail") { value("success must be true or false") }
        }
    }

    @Test
    fun `returns stabilized 400 contract when limit parameter is not numeric`() {
        val mockMvc = deliveryAttemptQueryMockMvc()

        mockMvc.get("/api/v1/delivery-attempts") {
            header("X-Sentinel-Tenant-Id", "tenant-alpha")
            param("limit", "not-a-number")
        }.andExpect {
            status { isBadRequest() }
            header { string("Cache-Control", "no-store") }
            jsonPath("$.title") { value("Bad Request") }
            jsonPath("$.type") { value("urn:sentinel:error:delivery-attempt-query-parameter-invalid") }
            jsonPath("$.scope") { value("delivery-attempt-query") }
            jsonPath("$.errorCode") { value("DELIVERY_ATTEMPT_QUERY_PARAMETER_INVALID") }
            jsonPath("$.parameter") { value("limit") }
            jsonPath("$.detail") { value("limit must be a valid integer") }
        }
    }

    @Test
    fun `returns stabilized 429 contract when delivery-attempt query rate limit is exceeded`() {
        val mockMvc =
            deliveryAttemptQueryMockMvc(
                apiProperties =
                    DeliveryApiProperties(
                        queryRateLimit =
                            DeliveryAttemptQueryRateLimitProperties(
                                enabled = true,
                                maxRequests = 1,
                                window = Duration.ofMinutes(1),
                            ),
                    ),
            )

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
        val mockMvc =
            deliveryAttemptQueryMockMvc(
                apiProperties =
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
            )

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

    private fun deliveryAttemptQueryMockMvc(
        apiProperties: DeliveryApiProperties = DeliveryApiProperties(),
        distributedRateLimiter: DeliveryAttemptQueryDistributedRateLimiter = NoOpDistributedRateLimiter,
    ): MockMvc {
        val controller =
            DeliveryAttemptQueryController(
                deliveryAttemptStore = EmptyDeliveryAttemptStore,
                deliveryAttemptQueryAuthorizationService =
                    DeliveryAttemptQueryAuthorizationService(
                        deliveryApiProperties = apiProperties,
                    ),
                deliveryAttemptQueryRateLimitService =
                    DeliveryAttemptQueryRateLimitService(
                        deliveryApiProperties = apiProperties,
                        distributedRateLimiter = distributedRateLimiter,
                    ),
            )

        return MockMvcBuilders
            .standaloneSetup(controller)
            .setControllerAdvice(DeliveryAttemptQueryApiExceptionHandler())
            .build()
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
