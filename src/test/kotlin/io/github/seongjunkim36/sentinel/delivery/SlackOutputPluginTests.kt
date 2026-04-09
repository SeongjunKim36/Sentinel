package io.github.seongjunkim36.sentinel.delivery

import io.github.seongjunkim36.sentinel.shared.AnalysisDetail
import io.github.seongjunkim36.sentinel.shared.AnalysisResult
import io.github.seongjunkim36.sentinel.shared.LlmMetadata
import io.github.seongjunkim36.sentinel.shared.RoutingDecision
import io.github.seongjunkim36.sentinel.shared.Severity
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class SlackOutputPluginTests {
    @Test
    fun `sends slack message when configured`() {
        val restClientBuilder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(restClientBuilder).build()
        val plugin =
            SlackOutputPlugin(
                restClientBuilder = restClientBuilder,
                deliveryProperties =
                    DeliveryProperties(
                        defaultChannels = listOf("slack"),
                        slack =
                            SlackDeliveryProperties(
                                apiBaseUrl = "https://slack.test/api",
                                botToken = "xoxb-test-token",
                                defaultChannel = "C123456",
                            ),
                    ),
                deliveryMessageFormatter = DeliveryMessageFormatter(),
            )

        server.expect(requestTo("https://slack.test/api/chat.postMessage"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer xoxb-test-token"))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json("""{"channel":"C123456","mrkdwn":true}""", JsonCompareMode.LENIENT))
            .andRespond(
                withSuccess(
                    """{"ok":true,"channel":"C123456","ts":"1744213370.000100"}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = plugin.send(sampleResult())

        assertThat(result.success).isTrue()
        assertThat(result.externalId).isEqualTo("C123456:1744213370.000100")
        server.verify()
    }

    @Test
    fun `returns failure when slack is not configured`() {
        val plugin =
            SlackOutputPlugin(
                restClientBuilder = RestClient.builder(),
                deliveryProperties = DeliveryProperties(defaultChannels = listOf("slack")),
                deliveryMessageFormatter = DeliveryMessageFormatter(),
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
            routing = RoutingDecision(channels = listOf("slack")),
        )
}
