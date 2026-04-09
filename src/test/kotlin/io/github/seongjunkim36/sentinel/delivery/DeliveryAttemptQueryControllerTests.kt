package io.github.seongjunkim36.sentinel.delivery

import java.time.Instant
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DeliveryAttemptQueryControllerTests {
    @Test
    fun `delegates query parameters to delivery attempt store`() {
        val store = RecordingStore()
        val controller = DeliveryAttemptQueryController(deliveryAttemptStore = store)
        val eventId = UUID.randomUUID()

        val records =
            controller.findRecent(
                eventId = eventId,
                tenantId = "tenant-alpha",
                channel = "telegram",
                success = false,
                limit = 25,
            )

        assertThat(store.lastQuery).isEqualTo(
            DeliveryAttemptQuery(
                eventId = eventId,
                tenantId = "tenant-alpha",
                channel = "telegram",
                success = false,
                limit = 25,
            ),
        )
        assertThat(records).hasSize(1)
        assertThat(records.single().eventId).isEqualTo(eventId)
    }

    private class RecordingStore : DeliveryAttemptStore {
        var lastQuery: DeliveryAttemptQuery? = null

        override fun record(attempt: DeliveryAttemptWrite) {
        }

        override fun findRecent(query: DeliveryAttemptQuery): List<DeliveryAttemptRecord> {
            lastQuery = query

            return listOf(
                DeliveryAttemptRecord(
                    id = 1L,
                    analysisResultId = UUID.randomUUID(),
                    eventId = query.eventId ?: UUID.randomUUID(),
                    tenantId = query.tenantId ?: "tenant-alpha",
                    channel = query.channel ?: "telegram",
                    success = query.success ?: true,
                    externalId = null,
                    message = "sample",
                    attemptedAt = Instant.now(),
                ),
            )
        }
    }
}
