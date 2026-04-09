package io.github.seongjunkim36.sentinel.shared

import java.time.Instant

data class ClassifiedEvent(
    val event: Event,
    val category: String,
    val analyzable: Boolean,
    val filtered: Boolean,
    val filterReason: String? = null,
    val tags: Set<String> = emptySet(),
    val classifiedAt: Instant = Instant.now(),
)
