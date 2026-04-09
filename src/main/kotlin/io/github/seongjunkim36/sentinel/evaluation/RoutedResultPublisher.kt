package io.github.seongjunkim36.sentinel.evaluation

import io.github.seongjunkim36.sentinel.shared.AnalysisResult

interface RoutedResultPublisher {
    fun publish(result: AnalysisResult)
}
