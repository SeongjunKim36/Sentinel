package io.github.seongjunkim36.sentinel.delivery

import io.github.seongjunkim36.sentinel.shared.AnalysisResult
import io.github.seongjunkim36.sentinel.shared.DeliveryResult
import io.github.seongjunkim36.sentinel.shared.OutputPlugin
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DeliveryHealthControllerTests {
    @Test
    fun `returns down status when required delivery channel is not ready`() {
        val controller =
            DeliveryHealthController(
                deliveryPluginReadinessService =
                    DeliveryPluginReadinessService(
                        deliveryProperties = DeliveryProperties(defaultChannels = listOf("slack")),
                        outputPluginRegistry = OutputPluginRegistry(emptyList()),
                    ),
            )

        val response = controller.health()

        assertThat(response.status).isEqualTo("DOWN")
        assertThat(response.requiredChannels).containsExactly("slack")
        assertThat(response.checks.first { it.channel == "slack" }.ready).isFalse()
    }

    @Test
    fun `returns up status when required channels are ready`() {
        val controller =
            DeliveryHealthController(
                deliveryPluginReadinessService =
                    DeliveryPluginReadinessService(
                        deliveryProperties =
                            DeliveryProperties(
                                defaultChannels = listOf("telegram"),
                                telegram =
                                    TelegramDeliveryProperties(
                                        botToken = "bot-token",
                                        chatId = "chat-1",
                                    ),
                            ),
                        outputPluginRegistry = OutputPluginRegistry(listOf(TestOutputPlugin("telegram"))),
                    ),
            )

        val response = controller.health()

        assertThat(response.status).isEqualTo("UP")
        assertThat(response.checks.first { it.channel == "telegram" }.ready).isTrue()
    }

    private class TestOutputPlugin(
        override val type: String,
    ) : OutputPlugin {
        override fun send(result: AnalysisResult): DeliveryResult = DeliveryResult(success = true)

        override fun sendBatch(results: List<AnalysisResult>): DeliveryResult = DeliveryResult(success = true)
    }
}
