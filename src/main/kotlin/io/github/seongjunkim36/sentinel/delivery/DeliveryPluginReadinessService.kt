package io.github.seongjunkim36.sentinel.delivery

import org.springframework.stereotype.Service

@Service
class DeliveryPluginReadinessService(
    private val deliveryProperties: DeliveryProperties,
    private val outputPluginRegistry: OutputPluginRegistry,
) {
    fun snapshot(): DeliveryReadinessSnapshot {
        val requiredChannels = normalizeChannels(deliveryProperties.defaultChannels)
        val channelsToCheck =
            (
                requiredChannels +
                    listOf("slack", "telegram") +
                    outputPluginRegistry.registeredTypes().toList()
            ).distinct()

        val checks =
            channelsToCheck.map { channel ->
                val required = requiredChannels.contains(channel)
                val registered = outputPluginRegistry.find(channel) != null
                val configuration = configurationFor(channel)
                DeliveryChannelReadiness(
                    channel = channel,
                    required = required,
                    registered = registered,
                    configured = configuration.configured,
                    ready = registered && configuration.configured,
                    reason = configuration.reason,
                )
            }

        val ready =
            if (requiredChannels.isEmpty()) {
                true
            } else {
                checks
                    .filter { it.required }
                    .all { it.ready }
            }

        return DeliveryReadinessSnapshot(
            ready = ready,
            requiredChannels = requiredChannels,
            checks = checks,
        )
    }

    private fun configurationFor(channel: String): DeliveryChannelConfigurationStatus =
        when (channel) {
            "slack" -> {
                val slack = deliveryProperties.slack
                val configured = slack.botToken.isNotBlank() && slack.defaultChannel.isNotBlank()
                DeliveryChannelConfigurationStatus(
                    configured = configured,
                    reason = if (configured) null else "Slack bot token or default channel is missing",
                )
            }

            "telegram" -> {
                val telegram = deliveryProperties.telegram
                val configured = telegram.botToken.isNotBlank() && telegram.chatId.isNotBlank()
                DeliveryChannelConfigurationStatus(
                    configured = configured,
                    reason = if (configured) null else "Telegram bot token or chat id is missing",
                )
            }

            else ->
                DeliveryChannelConfigurationStatus(
                    configured = true,
                    reason = "No built-in configuration check for channel '$channel'",
                )
        }

    private fun normalizeChannels(channels: List<String>): List<String> =
        channels
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
}

data class DeliveryReadinessSnapshot(
    val ready: Boolean,
    val requiredChannels: List<String>,
    val checks: List<DeliveryChannelReadiness>,
)

data class DeliveryChannelReadiness(
    val channel: String,
    val required: Boolean,
    val registered: Boolean,
    val configured: Boolean,
    val ready: Boolean,
    val reason: String?,
)

private data class DeliveryChannelConfigurationStatus(
    val configured: Boolean,
    val reason: String?,
)
