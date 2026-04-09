package io.github.seongjunkim36.sentinel.delivery

import io.github.seongjunkim36.sentinel.shared.AnalysisResult
import io.github.seongjunkim36.sentinel.shared.DeliveryResult
import io.github.seongjunkim36.sentinel.shared.OutputPlugin
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class SlackOutputPlugin : OutputPlugin {
    private val logger = LoggerFactory.getLogger(javaClass)

    override val type: String = "slack"

    override fun send(result: AnalysisResult): DeliveryResult {
        logger.info(
            "Slack delivery placeholder invoked: eventId={}, severity={}, summary={}",
            result.eventId,
            result.severity,
            result.summary,
        )
        return DeliveryResult(success = true, message = "Slack bootstrap delivery placeholder")
    }

    override fun sendBatch(results: List<AnalysisResult>): DeliveryResult {
        logger.info("Slack batch placeholder invoked: size={}", results.size)
        return DeliveryResult(success = true, message = "Slack bootstrap batch placeholder")
    }
}
