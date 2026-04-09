package io.github.seongjunkim36.sentinel.delivery

import io.github.seongjunkim36.sentinel.shared.AnalysisDetail
import io.github.seongjunkim36.sentinel.shared.AnalysisResult
import io.github.seongjunkim36.sentinel.shared.LlmMetadata
import io.github.seongjunkim36.sentinel.shared.RoutingDecision
import io.github.seongjunkim36.sentinel.shared.Severity
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class TelegramOutputPluginTests {
    @Test
    fun `sends telegram message when configured`() {
        val restClientBuilder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(restClientBuilder).build()
        val plugin =
            TelegramOutputPlugin(
                restClientBuilder = restClientBuilder,
                deliveryProperties =
                    DeliveryProperties(
                        defaultChannels = listOf("telegram"),
                        telegram =
                            TelegramDeliveryProperties(
                                apiBaseUrl = "https://api.telegram.test",
                                botToken = "bot-token",
                                chatId = "chat-1",
                            ),
                    ),
            )

        server.expect(requestTo("https://api.telegram.test/botbot-token/sendMessage"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json("""{"chat_id":"chat-1"}""", false))
            .andRespond(withSuccess("""{"ok":true,"result":{"message_id":42}}""", MediaType.APPLICATION_JSON))

        val result = plugin.send(sampleResult())

        assertThat(result.success).isTrue()
        assertThat(result.externalId).isEqualTo("42")
        server.verify()
    }

    @Test
    fun `returns failure when telegram is not configured`() {
        val plugin =
            TelegramOutputPlugin(
                restClientBuilder = RestClient.builder(),
                deliveryProperties = DeliveryProperties(defaultChannels = listOf("telegram")),
            )

        val result = plugin.send(sampleResult())

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("not configured")
    }

    private fun sampleResult(): AnalysisResult =
        AnalysisResult(
            eventId = UUID.randomUUID(),
            tenantId = "tenant-alpha",
            category = "error",
            severity = Severity.HIGH,
            confidence = 0.78,
            summary = "Checkout timeout requires investigation",
            detail = AnalysisDetail(analysis = "Payment flow is degraded", actionItems = listOf("Inspect database latency")),
            llmMetadata = LlmMetadata(model = "stub", promptVersion = "v1"),
            routing = RoutingDecision(channels = listOf("telegram")),
        )
}
