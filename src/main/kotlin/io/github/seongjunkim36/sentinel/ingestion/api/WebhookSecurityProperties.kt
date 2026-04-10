package io.github.seongjunkim36.sentinel.ingestion.api

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "sentinel.ingestion.webhook")
data class WebhookSecurityProperties(
    val sentry: SentryWebhookSecurityProperties = SentryWebhookSecurityProperties(),
)

data class SentryWebhookSecurityProperties(
    val signatureValidationEnabled: Boolean = false,
    val secret: String = "",
    val maxTimestampSkew: Duration = Duration.ofMinutes(5),
)
