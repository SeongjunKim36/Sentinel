package io.github.seongjunkim36.sentinel.delivery

import io.github.seongjunkim36.sentinel.shared.AnalysisResult
import io.github.seongjunkim36.sentinel.shared.DeliveryResult
import io.github.seongjunkim36.sentinel.shared.OutputPlugin
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DeliveryPluginReadinessServiceTests {
    @Test
    fun `marks ready when required default channel is registered and configured`() {
        val service =
            DeliveryPluginReadinessService(
                deliveryProperties =
                    DeliveryProperties(
                        defaultChannels = listOf("slack"),
                        slack =
                            SlackDeliveryProperties(
                                botToken = "xoxb-token",
                                defaultChannel = "C123",
                            ),
                    ),
                outputPluginRegistry = OutputPluginRegistry(listOf(TestOutputPlugin("slack"))),
            )

        val snapshot = service.snapshot()

        assertThat(snapshot.ready).isTrue()
        assertThat(snapshot.requiredChannels).containsExactly("slack")
        val slack = snapshot.checks.first { it.channel == "slack" }
        assertThat(slack.required).isTrue()
        assertThat(slack.registered).isTrue()
        assertThat(slack.configured).isTrue()
        assertThat(slack.ready).isTrue()
    }

    @Test
    fun `marks down when required channel is not configured`() {
        val service =
            DeliveryPluginReadinessService(
                deliveryProperties =
                    DeliveryProperties(
                        defaultChannels = listOf("telegram"),
                        telegram =
                            TelegramDeliveryProperties(
                                botToken = "",
                                chatId = "",
                            ),
                    ),
                outputPluginRegistry = OutputPluginRegistry(listOf(TestOutputPlugin("telegram"))),
            )

        val snapshot = service.snapshot()

        assertThat(snapshot.ready).isFalse()
        val telegram = snapshot.checks.first { it.channel == "telegram" }
        assertThat(telegram.required).isTrue()
        assertThat(telegram.registered).isTrue()
        assertThat(telegram.configured).isFalse()
        assertThat(telegram.ready).isFalse()
    }

    @Test
    fun `marks down when required channel plugin is missing`() {
        val service =
            DeliveryPluginReadinessService(
                deliveryProperties = DeliveryProperties(defaultChannels = listOf("slack")),
                outputPluginRegistry = OutputPluginRegistry(emptyList()),
            )

        val snapshot = service.snapshot()

        assertThat(snapshot.ready).isFalse()
        val slack = snapshot.checks.first { it.channel == "slack" }
        assertThat(slack.required).isTrue()
        assertThat(slack.registered).isFalse()
        assertThat(slack.ready).isFalse()
    }

    @Test
    fun `returns ready when no required default channels are configured`() {
        val service =
            DeliveryPluginReadinessService(
                deliveryProperties = DeliveryProperties(defaultChannels = emptyList()),
                outputPluginRegistry = OutputPluginRegistry(emptyList()),
            )

        val snapshot = service.snapshot()

        assertThat(snapshot.ready).isTrue()
        assertThat(snapshot.requiredChannels).isEmpty()
    }

    private class TestOutputPlugin(
        override val type: String,
    ) : OutputPlugin {
        override fun send(result: AnalysisResult): DeliveryResult = DeliveryResult(success = true)

        override fun sendBatch(results: List<AnalysisResult>): DeliveryResult = DeliveryResult(success = true)
    }
}
