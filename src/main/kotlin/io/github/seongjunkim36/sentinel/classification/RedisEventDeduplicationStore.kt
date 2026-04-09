package io.github.seongjunkim36.sentinel.classification

import io.github.seongjunkim36.sentinel.shared.Event
import org.slf4j.LoggerFactory
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.StringRedisTemplate

class RedisEventDeduplicationStore(
    private val stringRedisTemplate: StringRedisTemplate,
    private val classificationProperties: ClassificationProperties,
) : EventDeduplicationStore {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun markIfFirstSeen(event: Event): Boolean {
        if (!classificationProperties.deduplication.enabled) {
            return true
        }

        return try {
            stringRedisTemplate.opsForValue().setIfAbsent(
                deduplicationKeyFor(event),
                event.id.toString(),
                classificationProperties.deduplication.ttl,
            ) == true
        } catch (exception: RedisConnectionFailureException) {
            logger.warn(
                "Redis deduplication unavailable, proceeding without suppression: eventId={}, tenantId={}",
                event.id,
                event.tenantId,
            )
            true
        }
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
