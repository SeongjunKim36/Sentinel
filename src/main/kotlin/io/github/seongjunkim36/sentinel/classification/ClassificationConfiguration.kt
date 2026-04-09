package io.github.seongjunkim36.sentinel.classification

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.StringRedisTemplate

@Configuration(proxyBeanMethods = false)
class ClassificationConfiguration {
    @Bean
    @ConditionalOnBean(StringRedisTemplate::class)
    fun redisEventDeduplicationStore(
        stringRedisTemplate: StringRedisTemplate,
        classificationProperties: ClassificationProperties,
    ): EventDeduplicationStore = RedisEventDeduplicationStore(stringRedisTemplate, classificationProperties)

    @Bean
    @ConditionalOnMissingBean(EventDeduplicationStore::class)
    fun inMemoryEventDeduplicationStore(
        classificationProperties: ClassificationProperties,
    ): EventDeduplicationStore = InMemoryEventDeduplicationStore(classificationProperties)
}
