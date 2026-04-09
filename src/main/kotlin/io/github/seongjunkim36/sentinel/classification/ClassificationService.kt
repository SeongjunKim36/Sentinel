package io.github.seongjunkim36.sentinel.classification

import io.github.seongjunkim36.sentinel.shared.Event
import org.springframework.stereotype.Service

@Service
class ClassificationService {
    fun classify(event: Event): ClassifiedEvent =
        ClassifiedEvent(
            event = event,
            category = "error",
            filtered = false,
        )
}

data class ClassifiedEvent(
    val event: Event,
    val category: String,
    val filtered: Boolean,
)
