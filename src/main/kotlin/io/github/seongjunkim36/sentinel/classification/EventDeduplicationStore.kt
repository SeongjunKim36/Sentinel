package io.github.seongjunkim36.sentinel.classification

import io.github.seongjunkim36.sentinel.shared.Event

interface EventDeduplicationStore {
    fun markIfFirstSeen(event: Event): Boolean
}
