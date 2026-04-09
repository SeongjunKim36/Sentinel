package io.github.seongjunkim36.sentinel.delivery

import com.fasterxml.jackson.annotation.JsonProperty
import io.github.seongjunkim36.sentinel.shared.AnalysisResult
import io.github.seongjunkim36.sentinel.shared.DeliveryResult
import io.github.seongjunkim36.sentinel.shared.OutputPlugin
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

@Component
class SlackOutputPlugin(
    @Qualifier("sentinelRestClientBuilder") restClientBuilder: RestClient.Builder,
    private val deliveryProperties: DeliveryProperties,
    private val deliveryMessageFormatter: DeliveryMessageFormatter,
) : OutputPlugin {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val restClient = restClientBuilder.build()

    override val type: String = "slack"

    override fun send(result: AnalysisResult): DeliveryResult {
        val slack = deliveryProperties.slack

        if (slack.botToken.isBlank() || slack.defaultChannel.isBlank()) {
            logger.warn("Slack delivery skipped because bot token or default channel is missing")
            return DeliveryResult(success = false, message = "Slack delivery is not configured")
        }

        return try {
            val response =
                restClient.post()
                    .uri("${slack.apiBaseUrl}/chat.postMessage")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${slack.botToken}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(
                        SlackPostMessageRequest(
                            channel = slack.defaultChannel,
                            text = deliveryMessageFormatter.slackMarkdown(result),
                        ),
                    ).retrieve()
                    .body(SlackPostMessageResponse::class.java)

            if (response?.ok != true) {
                logger.warn(
                    "Slack delivery rejected: eventId={}, error={}",
                    result.eventId,
                    response?.error ?: "unknown-error",
                )
                return DeliveryResult(success = false, message = response?.error ?: "Slack delivery failed")
            }

            logger.info(
                "Slack delivery sent: eventId={}, severity={}, channel={}, ts={}",
                result.eventId,
                result.severity,
                response.channel,
                response.ts,
            )

            DeliveryResult(
                success = true,
                externalId = listOfNotNull(response.channel, response.ts).joinToString(":").ifBlank { null },
                message = "Slack delivery completed",
            )
        } catch (exception: RestClientException) {
            logger.error("Slack delivery failed for eventId={}", result.eventId, exception)
            DeliveryResult(success = false, message = exception.message)
        }
    }

    override fun sendBatch(results: List<AnalysisResult>): DeliveryResult {
        var lastResult = DeliveryResult(success = true, message = "No Slack messages to send")

        results.forEach { result ->
            lastResult = send(result)
        }

        return lastResult
    }
}

data class SlackPostMessageRequest(
    val channel: String,
    val text: String,
    val mrkdwn: Boolean = true,
    @JsonProperty("unfurl_links")
    val unfurlLinks: Boolean = false,
    @JsonProperty("unfurl_media")
    val unfurlMedia: Boolean = false,
)

data class SlackPostMessageResponse(
    val ok: Boolean,
    val channel: String? = null,
    val ts: String? = null,
    val error: String? = null,
)
