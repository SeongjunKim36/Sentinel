package io.github.seongjunkim36.sentinel.deadletter

import io.github.seongjunkim36.sentinel.shared.DeadLetterEvent

interface DeadLetterPublisher {
    fun publish(event: DeadLetterEvent)
}
