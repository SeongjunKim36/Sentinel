package io.github.seongjunkim36.sentinel.classification

import io.github.seongjunkim36.sentinel.shared.Event
import io.github.seongjunkim36.sentinel.shared.EventMetadata
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ClassificationServiceTests {
    private val classificationService = ClassificationService()

    @Test
    fun `classifies sentry errors as analyzable`() {
        val classifiedEvent = classificationService.classify(event(message = "Database timeout in checkout flow"))

        assertThat(classifiedEvent.category).isEqualTo("error")
        assertThat(classifiedEvent.analyzable).isTrue()
        assertThat(classifiedEvent.filtered).isFalse()
        assertThat(classifiedEvent.filterReason).isNull()
    }

    @Test
    fun `filters healthcheck noise`() {
        val classifiedEvent = classificationService.classify(event(message = "Healthcheck ping failed temporarily"))

        assertThat(classifiedEvent.analyzable).isFalse()
        assertThat(classifiedEvent.filtered).isTrue()
        assertThat(classifiedEvent.filterReason).isEqualTo("healthcheck-noise")
    }

    private fun event(message: String): Event =
        Event(
            sourceType = "sentry",
            sourceId = "evt-123",
            tenantId = "tenant-alpha",
            payload = mapOf("message" to message),
            metadata = EventMetadata(sourceVersion = "v1"),
        )
}
