package io.github.seongjunkim36.sentinel.shared

enum class Severity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    INFO,
}

enum class RoutingPriority {
    IMMEDIATE,
    BATCHED,
    DIGEST,
}
