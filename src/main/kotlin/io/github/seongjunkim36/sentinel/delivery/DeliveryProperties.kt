package io.github.seongjunkim36.sentinel.delivery

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "sentinel.delivery")
data class DeliveryProperties(
    val defaultChannels: List<String> = listOf("slack"),
    val slack: SlackDeliveryProperties = SlackDeliveryProperties(),
    val telegram: TelegramDeliveryProperties = TelegramDeliveryProperties(),
)

data class SlackDeliveryProperties(
    val apiBaseUrl: String = "https://slack.com/api",
    val botToken: String = "",
    val defaultChannel: String = "",
)

data class TelegramDeliveryProperties(
    val apiBaseUrl: String = "https://api.telegram.org",
    val botToken: String = "",
    val chatId: String = "",
)
