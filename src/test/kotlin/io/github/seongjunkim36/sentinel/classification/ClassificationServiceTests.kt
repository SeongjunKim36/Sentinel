package io.github.seongjunkim36.sentinel.classification

import io.github.seongjunkim36.sentinel.shared.Event
import io.github.seongjunkim36.sentinel.shared.EventMetadata
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ClassificationServiceTests {
    @Test
    fun `classifies sentry errors as analyzable`() {
        val classificationService = ClassificationService(StubEventDeduplicationStore(firstSeen = true))
        val classifiedEvent = classificationService.classify(event(message = "Database timeout in checkout flow"))

        assertThat(classifiedEvent.category).isEqualTo("error")
        assertThat(classifiedEvent.analyzable).isTrue()
        assertThat(classifiedEvent.filtered).isFalse()
        assertThat(classifiedEvent.filterReason).isNull()
    }

    @Test
    fun `filters healthcheck noise`() {
        val deduplicationStore = StubEventDeduplicationStore(firstSeen = true)
        val classificationService = ClassificationService(deduplicationStore)
        val classifiedEvent = classificationService.classify(event(message = "Healthcheck ping failed temporarily"))

        assertThat(classifiedEvent.analyzable).isFalse()
        assertThat(classifiedEvent.filtered).isTrue()
        assertThat(classifiedEvent.filterReason).isEqualTo("healthcheck-noise")
        assertThat(deduplicationStore.invocationCount).isZero()
    }

    @Test
    fun `filters duplicate analyzable events`() {
        val classificationService = ClassificationService(StubEventDeduplicationStore(firstSeen = false))

        val classifiedEvent = classificationService.classify(event(message = "Database timeout in checkout flow"))

        assertThat(classifiedEvent.analyzable).isFalse()
        assertThat(classifiedEvent.filtered).isTrue()
        assertThat(classifiedEvent.filterReason).isEqualTo("duplicate-event")
        assertThat(classifiedEvent.tags).contains("dedup:duplicate")
    }

    @Test
    fun `classifies rss feed updates as analyzable`() {
        val classificationService = ClassificationService(StubEventDeduplicationStore(firstSeen = true))

        val classifiedEvent =
            classificationService.classify(
                event(
                    sourceType = "rss",
                    sourceId = "https://example.com/post-1",
                    message = "Platform release notes - Improved checkout resilience",
                ),
            )

        assertThat(classifiedEvent.category).isEqualTo("feed-update")
        assertThat(classifiedEvent.analyzable).isTrue()
        assertThat(classifiedEvent.filtered).isFalse()
        assertThat(classifiedEvent.tags).contains("source:rss", "category:feed-update")
    }

    private fun event(
        message: String,
        sourceType: String = "sentry",
        sourceId: String = "evt-123",
    ): Event =
        Event(
            sourceType = sourceType,
            sourceId = sourceId,
            tenantId = "tenant-alpha",
            payload = mapOf("message" to message),
            metadata = EventMetadata(sourceVersion = "v1"),
        )

    private class StubEventDeduplicationStore(
        private val firstSeen: Boolean,
    ) : EventDeduplicationStore {
        var invocationCount: Int = 0
            private set

        override fun markIfFirstSeen(event: Event): Boolean {
            invocationCount += 1
            return firstSeen
        }
    }
}
