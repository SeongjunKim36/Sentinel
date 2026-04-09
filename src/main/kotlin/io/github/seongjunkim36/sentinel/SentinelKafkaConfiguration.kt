package io.github.seongjunkim36.sentinel

import io.github.seongjunkim36.sentinel.shared.Event
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaAdmin
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.support.serializer.JacksonJsonSerializer

@Configuration(proxyBeanMethods = false)
class SentinelKafkaConfiguration {
    @Bean
    fun eventProducerFactory(
        @Value("\${spring.kafka.bootstrap-servers}") bootstrapServers: String,
    ): ProducerFactory<String, Event> =
        DefaultKafkaProducerFactory(
            mutableMapOf<String, Any>(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ),
            StringSerializer(),
            JacksonJsonSerializer<Event>().noTypeInfo(),
        )

    @Bean
    fun eventKafkaTemplate(
        eventProducerFactory: ProducerFactory<String, Event>,
    ): KafkaTemplate<String, Event> = KafkaTemplate(eventProducerFactory)

    @Bean
    fun sentinelTopics(): KafkaAdmin.NewTopics =
        KafkaAdmin.NewTopics(
            topic(SentinelTopics.RAW_EVENTS, partitions = 12),
            topic(SentinelTopics.CLASSIFIED_EVENTS, partitions = 12),
            topic(SentinelTopics.ANALYSIS_RESULTS, partitions = 12),
            topic(SentinelTopics.ROUTED_RESULTS, partitions = 12),
            topic(SentinelTopics.DEAD_LETTER, partitions = 3),
        )

    private fun topic(name: String, partitions: Int): NewTopic =
        TopicBuilder.name(name)
            .partitions(partitions)
            .replicas(1)
            .build()
}
