package io.github.seongjunkim36.sentinel.delivery

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate

@Configuration(proxyBeanMethods = false)
class DeliveryAttemptStoreConfiguration {
    @Bean
    @ConditionalOnBean(JdbcTemplate::class)
    fun jdbcDeliveryAttemptStore(jdbcTemplate: JdbcTemplate): DeliveryAttemptStore = JdbcDeliveryAttemptStore(jdbcTemplate)

    @Bean
    @ConditionalOnMissingBean(DeliveryAttemptStore::class)
    fun noOpDeliveryAttemptStore(): DeliveryAttemptStore = NoOpDeliveryAttemptStore()
}
