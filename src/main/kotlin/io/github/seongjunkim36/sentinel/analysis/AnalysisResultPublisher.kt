package io.github.seongjunkim36.sentinel.analysis

import io.github.seongjunkim36.sentinel.shared.AnalysisResult

interface AnalysisResultPublisher {
    fun publish(result: AnalysisResult)
}
