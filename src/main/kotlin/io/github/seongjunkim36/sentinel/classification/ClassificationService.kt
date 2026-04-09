package io.github.seongjunkim36.sentinel.classification

import io.github.seongjunkim36.sentinel.shared.Event
import io.github.seongjunkim36.sentinel.shared.ClassifiedEvent
import org.springframework.stereotype.Service

@Service
class ClassificationService {
    fun classify(event: Event): ClassifiedEvent {
        val category = categoryFor(event)
        val message = event.payload["message"]?.toString()?.trim().orEmpty()
        val filterReason = filterReasonFor(message)

        return ClassifiedEvent(
            event = event,
            category = category,
            analyzable = filterReason == null && category == "error",
            filtered = filterReason != null,
            filterReason = filterReason,
            tags = setOf("source:${event.sourceType}", "category:$category"),
        )
    }

    private fun categoryFor(event: Event): String =
        when (event.sourceType.lowercase()) {
            "sentry" -> "error"
            else -> "generic"
        }

    private fun filterReasonFor(message: String): String? {
        if (message.isBlank()) {
            return "missing-message"
        }

        val normalized = message.lowercase()

        return when {
            "healthcheck" in normalized -> "healthcheck-noise"
            "heartbeat" in normalized -> "heartbeat-noise"
            else -> null
        }
    }
}
