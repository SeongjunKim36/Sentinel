package io.github.seongjunkim36.sentinel.delivery

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.web.client.RestClient

@Configuration(proxyBeanMethods = false)
class DeliveryHttpConfiguration {
    @Bean
    @Qualifier("sentinelRestClientBuilder")
    @Primary
    fun sentinelRestClientBuilder(): RestClient.Builder = RestClient.builder()
}
