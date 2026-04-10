package io.github.seongjunkim36.sentinel.deadletter

import io.github.seongjunkim36.sentinel.shared.AnalysisResult

interface DeadLetterRecorder {
    fun recordDeliveryFailure(
        result: AnalysisResult,
        channel: String,
        reason: String,
    )
}
