package io.github.seongjunkim36.sentinel.delivery

import org.slf4j.LoggerFactory

class NoOpDeliveryAttemptStore : DeliveryAttemptStore {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun record(attempt: DeliveryAttemptWrite) {
        logger.debug(
            "Skipping delivery attempt persistence because no JDBC store is available: eventId={}, channel={}, success={}",
            attempt.eventId,
            attempt.channel,
            attempt.success,
        )
    }

    override fun findRecent(query: DeliveryAttemptQuery): List<DeliveryAttemptRecord> = emptyList()
}
