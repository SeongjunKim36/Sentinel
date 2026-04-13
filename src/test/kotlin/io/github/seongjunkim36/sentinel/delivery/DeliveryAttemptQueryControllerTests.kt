package io.github.seongjunkim36.sentinel.delivery

import java.time.Instant
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class DeliveryAttemptQueryControllerTests {
    @Test
    fun `delegates normalized query parameters to delivery attempt store and returns page contract`() {
        val store = RecordingStore()
        val controller = DeliveryAttemptQueryController(deliveryAttemptStore = store)
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
        val controller = DeliveryAttemptQueryController(deliveryAttemptStore = store)

        val first =
            controller.findRecent(
                eventId = null,
                tenantId = "tenant-alpha",
                channel = null,
                success = null,
                limit = 1,
                cursor = null,
                tenantScopeHeader = "tenant-alpha",
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
            )

        assertThat(second.items).hasSize(1)
        assertThat(second.page.hasMore).isFalse()
        assertThat(second.page.nextCursor).isNull()
        assertThat(store.lastQuery!!.cursor).isNotNull()
    }

    @Test
    fun `rejects invalid cursor`() {
        val store = RecordingStore()
        val controller = DeliveryAttemptQueryController(deliveryAttemptStore = store)

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
                )
            }.exceptionOrNull()

        assertThat(exception).isInstanceOf(ResponseStatusException::class.java)
        assertThat((exception as ResponseStatusException).statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `rejects query tenant filter when it mismatches tenant scope header`() {
        val store = RecordingStore()
        val controller = DeliveryAttemptQueryController(deliveryAttemptStore = store)

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
                )
            }.exceptionOrNull()

        assertThat(exception).isInstanceOf(ResponseStatusException::class.java)
        assertThat((exception as ResponseStatusException).statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `rejects blank tenant scope header`() {
        val store = RecordingStore()
        val controller = DeliveryAttemptQueryController(deliveryAttemptStore = store)

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
                )
            }.exceptionOrNull()

        assertThat(exception).isInstanceOf(ResponseStatusException::class.java)
        assertThat((exception as ResponseStatusException).statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
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
