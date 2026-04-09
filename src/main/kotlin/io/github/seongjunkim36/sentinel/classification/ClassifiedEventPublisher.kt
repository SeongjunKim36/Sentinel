package io.github.seongjunkim36.sentinel.classification

import io.github.seongjunkim36.sentinel.shared.ClassifiedEvent

interface ClassifiedEventPublisher {
    fun publish(classifiedEvent: ClassifiedEvent)
}
