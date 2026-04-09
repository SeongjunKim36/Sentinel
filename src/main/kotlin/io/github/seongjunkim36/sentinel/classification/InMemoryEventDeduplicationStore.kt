package io.github.seongjunkim36.sentinel.classification

import io.github.seongjunkim36.sentinel.shared.Event
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class InMemoryEventDeduplicationStore(
    private val classificationProperties: ClassificationProperties,
) : EventDeduplicationStore {
    private val deduplicationKeys = ConcurrentHashMap<String, Instant>()

    override fun markIfFirstSeen(event: Event): Boolean {
        if (!classificationProperties.deduplication.enabled) {
            return true
        }

        val now = Instant.now()
        val deduplicationKey = deduplicationKeyFor(event)
        val expiresAt = now.plus(classificationProperties.deduplication.ttl)

        deduplicationKeys.entries.removeIf { (_, entryExpiry) -> entryExpiry.isBefore(now) }

        var isFirstSeen = false
        deduplicationKeys.compute(deduplicationKey) { _, previousExpiry ->
            if (previousExpiry == null || previousExpiry.isBefore(now)) {
                isFirstSeen = true
                expiresAt
            } else {
                previousExpiry
            }
        }

        return isFirstSeen
    }

    private fun deduplicationKeyFor(event: Event): String =
        buildString {
            append(classificationProperties.deduplication.keyPrefix)
            append(':')
            append(event.tenantId)
            append(':')
            append(event.sourceType)
            append(':')
            append(event.sourceId)
        }
}
