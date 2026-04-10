package io.github.seongjunkim36.sentinel.deadletter

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate

@Configuration(proxyBeanMethods = false)
class DeadLetterStoreConfiguration {
    @Bean
    @ConditionalOnBean(JdbcTemplate::class)
    fun jdbcDeadLetterStore(jdbcTemplate: JdbcTemplate): DeadLetterStore = JdbcDeadLetterStore(jdbcTemplate)

    @Bean
    @ConditionalOnMissingBean(DeadLetterStore::class)
    fun noOpDeadLetterStore(): DeadLetterStore = NoOpDeadLetterStore()
}
