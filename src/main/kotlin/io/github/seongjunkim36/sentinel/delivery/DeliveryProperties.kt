package io.github.seongjunkim36.sentinel.delivery

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "sentinel.delivery")
data class DeliveryProperties(
    val defaultChannels: List<String> = listOf("slack"),
    val telegram: TelegramDeliveryProperties = TelegramDeliveryProperties(),
)

data class TelegramDeliveryProperties(
    val apiBaseUrl: String = "https://api.telegram.org",
    val botToken: String = "",
    val chatId: String = "",
)
