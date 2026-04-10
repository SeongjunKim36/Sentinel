package io.github.seongjunkim36.sentinel.deadletter

import io.github.seongjunkim36.sentinel.shared.AnalysisResult

interface DeadLetterReplayPublisher {
    fun publishAnalysisResult(result: AnalysisResult)
}
