package io.github.seongjunkim36.sentinel.delivery

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "sentinel.delivery.api")
data class DeliveryApiProperties(
    val queryAuthorization: DeliveryAttemptQueryAuthorizationProperties = DeliveryAttemptQueryAuthorizationProperties(),
)

data class DeliveryAttemptQueryAuthorizationProperties(
    val enabled: Boolean = false,
    val headerName: String = "X-Sentinel-Query-Token",
    val token: String = "",
)
