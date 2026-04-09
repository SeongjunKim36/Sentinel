package io.github.seongjunkim36.sentinel.analysis

import io.github.seongjunkim36.sentinel.SentinelTopics
import io.github.seongjunkim36.sentinel.shared.ClassifiedEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class ClassifiedEventAnalysisConsumer(
    private val analysisService: AnalysisService,
    private val analysisResultPublisher: AnalysisResultPublisher,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        id = "classified-event-analysis-consumer",
        topics = [SentinelTopics.CLASSIFIED_EVENTS],
        groupId = "sentinel-analysis-v1",
        containerFactory = "classifiedEventKafkaListenerContainerFactory",
    )
    fun consume(classifiedEvent: ClassifiedEvent) {
        if (classifiedEvent.filtered || !classifiedEvent.analyzable) {
            logger.info(
                "Skipped analysis publication: eventId={}, tenantId={}, filtered={}, analyzable={}, filterReason={}",
                classifiedEvent.event.id,
                classifiedEvent.event.tenantId,
                classifiedEvent.filtered,
                classifiedEvent.analyzable,
                classifiedEvent.filterReason,
            )
            return
        }

        val analysisResult = analysisService.analyze(classifiedEvent)
        analysisResultPublisher.publish(analysisResult)
    }
}
