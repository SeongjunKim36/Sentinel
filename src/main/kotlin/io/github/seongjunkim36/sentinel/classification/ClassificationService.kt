package io.github.seongjunkim36.sentinel.classification

import io.github.seongjunkim36.sentinel.shared.Event
import io.github.seongjunkim36.sentinel.shared.ClassifiedEvent
import org.springframework.stereotype.Service

@Service
class ClassificationService(
    private val eventDeduplicationStore: EventDeduplicationStore,
) {
    fun classify(event: Event): ClassifiedEvent {
        val category = categoryFor(event)
        val message = event.payload["message"]?.toString()?.trim().orEmpty()
        val initialFilterReason = filterReasonFor(message)
        val analyzableCandidate = initialFilterReason == null && isAnalyzableCategory(category)
        val deduplicationFilterReason =
            if (analyzableCandidate && !eventDeduplicationStore.markIfFirstSeen(event)) {
                "duplicate-event"
            } else {
                null
            }
        val filterReason = initialFilterReason ?: deduplicationFilterReason

        return ClassifiedEvent(
            event = event,
            category = category,
            analyzable = filterReason == null && isAnalyzableCategory(category),
            filtered = filterReason != null,
            filterReason = filterReason,
            tags =
                buildSet {
                    add("source:${event.sourceType}")
                    add("category:$category")
                    if (deduplicationFilterReason != null) {
                        add("dedup:duplicate")
                    }
                },
        )
    }

    private fun categoryFor(event: Event): String =
        when (event.sourceType.lowercase()) {
            "sentry" -> "error"
            "rss" -> "feed-update"
            else -> "generic"
        }

    private fun isAnalyzableCategory(category: String): Boolean = category in setOf("error", "feed-update")

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
