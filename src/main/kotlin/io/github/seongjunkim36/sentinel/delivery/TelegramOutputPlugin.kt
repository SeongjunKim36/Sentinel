package io.github.seongjunkim36.sentinel.delivery

import com.fasterxml.jackson.annotation.JsonProperty
import io.github.seongjunkim36.sentinel.shared.AnalysisResult
import io.github.seongjunkim36.sentinel.shared.DeliveryResult
import io.github.seongjunkim36.sentinel.shared.OutputPlugin
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

@Component
class TelegramOutputPlugin(
    @Qualifier("sentinelRestClientBuilder") restClientBuilder: RestClient.Builder,
    private val deliveryProperties: DeliveryProperties,
) : OutputPlugin {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val restClient = restClientBuilder.build()

    override val type: String = "telegram"

    override fun send(result: AnalysisResult): DeliveryResult {
        val telegram = deliveryProperties.telegram

        if (telegram.botToken.isBlank() || telegram.chatId.isBlank()) {
            logger.warn("Telegram delivery skipped because bot token or chat id is missing")
            return DeliveryResult(success = false, message = "Telegram delivery is not configured")
        }

        return try {
            val response =
                restClient.post()
                    .uri("${telegram.apiBaseUrl}/bot${telegram.botToken}/sendMessage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(
                        TelegramSendMessageRequest(
                            chatId = telegram.chatId,
                            text = formatMessage(result),
                        ),
                    ).retrieve()
                    .body(TelegramSendMessageResponse::class.java)

            logger.info(
                "Telegram delivery sent: eventId={}, severity={}, chatId={}",
                result.eventId,
                result.severity,
                telegram.chatId,
            )

            DeliveryResult(
                success = response?.ok == true,
                externalId = response?.result?.messageId?.toString(),
                message = "Telegram delivery completed",
            )
        } catch (exception: RestClientException) {
            logger.error("Telegram delivery failed for eventId={}", result.eventId, exception)
            DeliveryResult(success = false, message = exception.message)
        }
    }

    override fun sendBatch(results: List<AnalysisResult>): DeliveryResult {
        var lastResult = DeliveryResult(success = true, message = "No Telegram messages to send")

        results.forEach { result ->
            lastResult = send(result)
        }

        return lastResult
    }

    private fun formatMessage(result: AnalysisResult): String =
        buildString {
            append("[Sentinel] ")
            append(result.severity)
            append(" incident")
            appendLine()
            append("Category: ")
            append(result.category)
            appendLine()
            append("Summary: ")
            append(result.summary)
            appendLine()
            append("Confidence: ")
            append(String.format("%.2f", result.confidence))
            if (result.detail.actionItems.isNotEmpty()) {
                appendLine()
                append("Actions: ")
                append(result.detail.actionItems.joinToString("; "))
            }
        }
}

data class TelegramSendMessageRequest(
    @JsonProperty("chat_id")
    val chatId: String,
    val text: String,
)

data class TelegramSendMessageResponse(
    val ok: Boolean,
    val result: TelegramMessageResult? = null,
)

data class TelegramMessageResult(
    @JsonProperty("message_id")
    val messageId: Long,
)
