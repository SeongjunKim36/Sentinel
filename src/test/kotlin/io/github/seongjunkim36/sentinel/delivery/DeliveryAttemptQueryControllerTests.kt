package io.github.seongjunkim36.sentinel.delivery

import java.time.Instant
import java.time.Duration
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest

class DeliveryAttemptQueryControllerTests {
    @Test
    fun `delegates normalized query parameters to delivery attempt store and returns page contract`() {
        val store = RecordingStore()
        val controller = deliveryAttemptController(store)
        val eventId = store.firstEventId

        val response =
            controller.findRecent(
                eventId = eventId,
                tenantId = " tenant-alpha ",
                channel = " telegram ",
                success = true,
                limit = 25,
                cursor = null,
                tenantScopeHeader = " tenant-alpha ",
                httpServletRequest = MockHttpServletRequest(),
            )

        assertThat(store.lastQuery).isEqualTo(
            DeliveryAttemptQuery(
                eventId = eventId,
                tenantId = "tenant-alpha",
                channel = "telegram",
                success = true,
                limit = 26,
                cursor = null,
            ),
        )
        assertThat(response.items).hasSize(1)
        assertThat(response.page.limit).isEqualTo(25)
        assertThat(response.page.hasMore).isFalse()
        assertThat(response.page.nextCursor).isNull()
        assertThat(response.items.single().eventId).isEqualTo(eventId)
    }

    @Test
    fun `returns hasMore with cursor and fetches next page`() {
        val store = RecordingStore()
        val controller = deliveryAttemptController(store)

        val first =
            controller.findRecent(
                eventId = null,
                tenantId = "tenant-alpha",
                channel = null,
                success = null,
                limit = 1,
                cursor = null,
                tenantScopeHeader = "tenant-alpha",
                httpServletRequest = MockHttpServletRequest(),
            )

        assertThat(first.items).hasSize(1)
        assertThat(first.items.single().tenantId).isEqualTo("tenant-alpha")
        assertThat(first.page.hasMore).isTrue()
        assertThat(first.page.nextCursor).isNotBlank()
        assertThat(store.lastQuery!!.tenantId).isEqualTo("tenant-alpha")

        val second =
            controller.findRecent(
                eventId = null,
                tenantId = "tenant-alpha",
                channel = null,
                success = null,
                limit = 1,
                cursor = first.page.nextCursor,
                tenantScopeHeader = "tenant-alpha",
                httpServletRequest = MockHttpServletRequest(),
            )

        assertThat(second.items).hasSize(1)
        assertThat(second.page.hasMore).isFalse()
        assertThat(second.page.nextCursor).isNull()
        assertThat(store.lastQuery!!.cursor).isNotNull()
    }

    @Test
    fun `rejects invalid cursor`() {
        val store = RecordingStore()
        val controller = deliveryAttemptController(store)

        val exception =
            kotlin.runCatching {
                controller.findRecent(
                    eventId = null,
                    tenantId = null,
                    channel = null,
                    success = null,
                    limit = 50,
                    cursor = "invalid-cursor",
                    tenantScopeHeader = "tenant-alpha",
                    httpServletRequest = MockHttpServletRequest(),
                )
            }.exceptionOrNull()

        assertThat(exception).isInstanceOf(DeliveryAttemptQueryValidationException::class.java)
        assertThat((exception as DeliveryAttemptQueryValidationException).reason)
            .isEqualTo(DeliveryAttemptQueryValidationReason.CURSOR_INVALID)
    }

    @Test
    fun `rejects query tenant filter when it mismatches tenant scope header`() {
        val store = RecordingStore()
        val controller = deliveryAttemptController(store)

        val exception =
            kotlin.runCatching {
                controller.findRecent(
                    eventId = null,
                    tenantId = "tenant-beta",
                    channel = null,
                    success = null,
                    limit = 50,
                    cursor = null,
                    tenantScopeHeader = "tenant-alpha",
                    httpServletRequest = MockHttpServletRequest(),
                )
            }.exceptionOrNull()

        assertThat(exception).isInstanceOf(DeliveryAttemptQueryValidationException::class.java)
        assertThat((exception as DeliveryAttemptQueryValidationException).reason)
            .isEqualTo(DeliveryAttemptQueryValidationReason.TENANT_SCOPE_MISMATCH)
    }

    @Test
    fun `rejects blank tenant scope header`() {
        val store = RecordingStore()
        val controller = deliveryAttemptController(store)

        val exception =
            kotlin.runCatching {
                controller.findRecent(
                    eventId = null,
                    tenantId = null,
                    channel = null,
                    success = null,
                    limit = 50,
                    cursor = null,
                    tenantScopeHeader = "   ",
                    httpServletRequest = MockHttpServletRequest(),
                )
            }.exceptionOrNull()

        assertThat(exception).isInstanceOf(DeliveryAttemptQueryValidationException::class.java)
        assertThat((exception as DeliveryAttemptQueryValidationException).reason)
            .isEqualTo(DeliveryAttemptQueryValidationReason.TENANT_SCOPE_REQUIRED)
    }

    @Test
    fun `rejects delivery-attempt query limit when it is below the supported range`() {
        val store = RecordingStore()
        val controller = deliveryAttemptController(store)

        val exception =
            kotlin.runCatching {
                controller.findRecent(
                    eventId = null,
                    tenantId = null,
                    channel = null,
                    success = null,
                    limit = 0,
                    cursor = null,
                    tenantScopeHeader = "tenant-alpha",
                    httpServletRequest = MockHttpServletRequest(),
                )
            }.exceptionOrNull()

        assertThat(exception).isInstanceOf(DeliveryAttemptQueryValidationException::class.java)
        assertThat((exception as DeliveryAttemptQueryValidationException).reason)
            .isEqualTo(DeliveryAttemptQueryValidationReason.LIMIT_OUT_OF_RANGE)
    }

    @Test
    fun `rejects delivery-attempt query limit when it exceeds the supported range`() {
        val store = RecordingStore()
        val controller = deliveryAttemptController(store)

        val exception =
            kotlin.runCatching {
                controller.findRecent(
                    eventId = null,
                    tenantId = null,
                    channel = null,
                    success = null,
                    limit = 201,
                    cursor = null,
                    tenantScopeHeader = "tenant-alpha",
                    httpServletRequest = MockHttpServletRequest(),
                )
            }.exceptionOrNull()

        assertThat(exception).isInstanceOf(DeliveryAttemptQueryValidationException::class.java)
        assertThat((exception as DeliveryAttemptQueryValidationException).reason)
            .isEqualTo(DeliveryAttemptQueryValidationReason.LIMIT_OUT_OF_RANGE)
    }

    @Test
    fun `rejects delivery-attempt query when authorization is enabled and token is missing`() {
        val store = RecordingStore()
        val controller =
            deliveryAttemptController(
                store = store,
                apiProperties =
                    DeliveryApiProperties(
                        queryAuthorization =
                            DeliveryAttemptQueryAuthorizationProperties(
                                enabled = true,
                                token = "query-secret",
                            ),
                    ),
            )

        val exception =
            kotlin.runCatching {
                controller.findRecent(
                    eventId = null,
                    tenantId = null,
                    channel = null,
                    success = null,
                    limit = 50,
                    cursor = null,
                    tenantScopeHeader = "tenant-alpha",
                    httpServletRequest = MockHttpServletRequest(),
                )
            }.exceptionOrNull()

        assertThat(exception).isInstanceOf(DeliveryAttemptQueryUnauthorizedException::class.java)
    }

    @Test
    fun `accepts delivery-attempt query when authorization token is valid`() {
        val store = RecordingStore()
        val controller =
            deliveryAttemptController(
                store = store,
                apiProperties =
                    DeliveryApiProperties(
                        queryAuthorization =
                            DeliveryAttemptQueryAuthorizationProperties(
                                enabled = true,
                                token = "query-secret",
                            ),
                    ),
            )
        val request =
            MockHttpServletRequest().apply {
                addHeader("X-Sentinel-Query-Token", "query-secret")
            }

        val response =
            controller.findRecent(
                eventId = null,
                tenantId = null,
                channel = null,
                success = null,
                limit = 1,
                cursor = null,
                tenantScopeHeader = "tenant-alpha",
                httpServletRequest = request,
            )

        assertThat(response.items).hasSize(1)
        assertThat(response.items.single().tenantId).isEqualTo("tenant-alpha")
    }

    @Test
    fun `rejects delivery-attempt query when rate limit is exceeded within the same tenant window`() {
        val store = RecordingStore()
        val controller =
            deliveryAttemptController(
                store = store,
                apiProperties =
                    DeliveryApiProperties(
                        queryRateLimit =
                            DeliveryAttemptQueryRateLimitProperties(
                                enabled = true,
                                maxRequests = 1,
                            ),
                    ),
            )
        val request = MockHttpServletRequest()

        controller.findRecent(
            eventId = null,
            tenantId = null,
            channel = null,
            success = null,
            limit = 1,
            cursor = null,
            tenantScopeHeader = "tenant-alpha",
            httpServletRequest = request,
        )

        val exception =
            kotlin.runCatching {
                controller.findRecent(
                    eventId = null,
                    tenantId = null,
                    channel = null,
                    success = null,
                    limit = 1,
                    cursor = null,
                    tenantScopeHeader = "tenant-alpha",
                    httpServletRequest = request,
                )
            }.exceptionOrNull()

        assertThat(exception).isInstanceOf(DeliveryAttemptQueryRateLimitExceededException::class.java)
    }

    @Test
    fun `applies delivery-attempt query rate limit independently per tenant`() {
        val store = RecordingStore()
        val controller =
            deliveryAttemptController(
                store = store,
                apiProperties =
                    DeliveryApiProperties(
                        queryRateLimit =
                            DeliveryAttemptQueryRateLimitProperties(
                                enabled = true,
                                maxRequests = 1,
                            ),
                    ),
            )
        val request = MockHttpServletRequest()

        val alphaResponse =
            controller.findRecent(
                eventId = null,
                tenantId = null,
                channel = null,
                success = null,
                limit = 1,
                cursor = null,
                tenantScopeHeader = "tenant-alpha",
                httpServletRequest = request,
            )
        val betaResponse =
            controller.findRecent(
                eventId = null,
                tenantId = null,
                channel = null,
                success = null,
                limit = 1,
                cursor = null,
                tenantScopeHeader = "tenant-beta",
                httpServletRequest = request,
            )

        assertThat(alphaResponse.items).hasSize(1)
        assertThat(alphaResponse.items.single().tenantId).isEqualTo("tenant-alpha")
        assertThat(betaResponse.items).hasSize(1)
        assertThat(betaResponse.items.single().tenantId).isEqualTo("tenant-beta")
    }

    private fun deliveryAttemptController(
        store: RecordingStore,
        apiProperties: DeliveryApiProperties = DeliveryApiProperties(),
    ): DeliveryAttemptQueryController =
        DeliveryAttemptQueryController(
            deliveryAttemptStore = store,
            deliveryAttemptQueryAuthorizationService =
                DeliveryAttemptQueryAuthorizationService(
                    deliveryApiProperties = apiProperties,
                ),
            deliveryAttemptQueryRateLimitService =
                DeliveryAttemptQueryRateLimitService(
                    deliveryApiProperties = apiProperties,
                    distributedRateLimiter = NoOpDistributedRateLimiter,
                ),
        )

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

    private class RecordingStore : DeliveryAttemptStore {
        val firstEventId = UUID.randomUUID()
        private val secondEventId = UUID.randomUUID()
        private val thirdEventId = UUID.randomUUID()

        private val records =
            listOf(
                DeliveryAttemptRecord(
                    id = 3L,
                    analysisResultId = UUID.randomUUID(),
                    eventId = thirdEventId,
                    tenantId = "tenant-beta",
                    channel = "slack",
                    success = true,
                    externalId = null,
                    message = "other-tenant-newest",
                    attemptedAt = Instant.parse("2026-04-01T00:15:00Z"),
                ),
                DeliveryAttemptRecord(
                    id = 2L,
                    analysisResultId = UUID.randomUUID(),
                    eventId = firstEventId,
                    tenantId = "tenant-alpha",
                    channel = "telegram",
                    success = true,
                    externalId = null,
                    message = "newest",
                    attemptedAt = Instant.parse("2026-04-01T00:10:00Z"),
                ),
                DeliveryAttemptRecord(
                    id = 1L,
                    analysisResultId = UUID.randomUUID(),
                    eventId = secondEventId,
                    tenantId = "tenant-alpha",
                    channel = "telegram",
                    success = false,
                    externalId = null,
                    message = "older",
                    attemptedAt = Instant.parse("2026-04-01T00:05:00Z"),
                ),
            )

        var lastQuery: DeliveryAttemptQuery? = null

        override fun record(attempt: DeliveryAttemptWrite) {
        }

        override fun findRecent(query: DeliveryAttemptQuery): List<DeliveryAttemptRecord> {
            lastQuery = query

            val filtered =
                records
                    .asSequence()
                    .filter { record -> query.eventId == null || record.eventId == query.eventId }
                    .filter { record -> query.tenantId == null || record.tenantId == query.tenantId }
                    .filter { record -> query.channel == null || record.channel == query.channel }
                    .filter { record -> query.success == null || record.success == query.success }
                    .let { sequence ->
                        query.cursor?.let { cursor ->
                            sequence.filter { record ->
                                record.attemptedAt.isBefore(cursor.attemptedAt) ||
                                    (record.attemptedAt == cursor.attemptedAt && record.id < cursor.id)
                            }
                        } ?: sequence
                    }.toList()

            return filtered.take(query.limit.coerceAtLeast(1))
        }
    }
}
