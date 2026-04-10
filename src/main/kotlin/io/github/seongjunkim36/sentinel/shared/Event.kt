package io.github.seongjunkim36.sentinel.shared

import java.time.Instant
import java.util.UUID

data class Event(
    val id: UUID = UUID.randomUUID(),
    val sourceType: String,
    val sourceId: String,
    val tenantId: String,
    val payload: Map<String, Any?>,
    val metadata: EventMetadata,
)

data class EventMetadata(
    val receivedAt: Instant = Instant.now(),
    val sourceVersion: String,
    val headers: Map<String, String> = emptyMap(),
    val traceId: String? = null,
)
