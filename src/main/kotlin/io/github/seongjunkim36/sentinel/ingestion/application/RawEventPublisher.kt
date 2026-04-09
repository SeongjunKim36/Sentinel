package io.github.seongjunkim36.sentinel.ingestion.application

import io.github.seongjunkim36.sentinel.shared.Event

interface RawEventPublisher {
    fun publish(event: Event)
}
