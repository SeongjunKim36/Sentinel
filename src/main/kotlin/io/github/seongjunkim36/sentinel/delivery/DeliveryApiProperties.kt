package io.github.seongjunkim36.sentinel.delivery

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "sentinel.delivery.api")
data class DeliveryApiProperties(
    val queryAuthorization: DeliveryAttemptQueryAuthorizationProperties = DeliveryAttemptQueryAuthorizationProperties(),
    val queryRateLimit: DeliveryAttemptQueryRateLimitProperties = DeliveryAttemptQueryRateLimitProperties(),
)

data class DeliveryAttemptQueryAuthorizationProperties(
    val enabled: Boolean = false,
    val headerName: String = "X-Sentinel-Query-Token",
    val token: String = "",
)

data class DeliveryAttemptQueryRateLimitProperties(
    val enabled: Boolean = false,
    val maxRequests: Int = 60,
    val window: Duration = Duration.ofMinutes(1),
    val distributed: DeliveryAttemptQueryDistributedRateLimitProperties = DeliveryAttemptQueryDistributedRateLimitProperties(),
)

data class DeliveryAttemptQueryDistributedRateLimitProperties(
    val enabled: Boolean = false,
    val keyPrefix: String = "sentinel:delivery:query-rate-limit",
    val failOpen: Boolean = true,
)
