package io.github.seongjunkim36.sentinel

import io.github.seongjunkim36.sentinel.shared.ClassifiedEvent
import io.github.seongjunkim36.sentinel.shared.Event
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaAdmin
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer
import org.springframework.kafka.support.serializer.JacksonJsonSerializer
import tools.jackson.databind.json.JsonMapper

@EnableKafka
@Configuration(proxyBeanMethods = false)
class SentinelKafkaConfiguration {
    @Bean
    fun eventProducerFactory(
        @Value("\${spring.kafka.bootstrap-servers}") bootstrapServers: String,
        jsonMapper: JsonMapper,
    ): ProducerFactory<String, Event> =
        jsonProducerFactory(
            bootstrapServers = bootstrapServers,
            valueSerializer = JacksonJsonSerializer<Event>(jsonMapper).noTypeInfo(),
        )

    @Bean
    fun classifiedEventProducerFactory(
        @Value("\${spring.kafka.bootstrap-servers}") bootstrapServers: String,
        jsonMapper: JsonMapper,
    ): ProducerFactory<String, ClassifiedEvent> =
        jsonProducerFactory(
            bootstrapServers = bootstrapServers,
            valueSerializer = JacksonJsonSerializer<ClassifiedEvent>(jsonMapper).noTypeInfo(),
        )

    @Bean
    fun eventConsumerFactory(
        @Value("\${spring.kafka.bootstrap-servers}") bootstrapServers: String,
        jsonMapper: JsonMapper,
    ): ConsumerFactory<String, Event> =
        DefaultKafkaConsumerFactory(
            mutableMapOf<String, Any>(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ),
            StringDeserializer(),
            JacksonJsonDeserializer(Event::class.java, jsonMapper)
                .trustedPackages("io.github.seongjunkim36.sentinel")
                .ignoreTypeHeaders(),
        )

    @Bean
    fun eventKafkaTemplate(
        eventProducerFactory: ProducerFactory<String, Event>,
    ): KafkaTemplate<String, Event> = KafkaTemplate(eventProducerFactory)

    @Bean
    fun classifiedEventKafkaTemplate(
        classifiedEventProducerFactory: ProducerFactory<String, ClassifiedEvent>,
    ): KafkaTemplate<String, ClassifiedEvent> = KafkaTemplate(classifiedEventProducerFactory)

    @Bean
    fun eventKafkaListenerContainerFactory(
        eventConsumerFactory: ConsumerFactory<String, Event>,
    ): ConcurrentKafkaListenerContainerFactory<String, Event> =
        ConcurrentKafkaListenerContainerFactory<String, Event>().apply {
            setConsumerFactory(eventConsumerFactory)
        }

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

    private fun <T : Any> jsonProducerFactory(
        bootstrapServers: String,
        valueSerializer: JacksonJsonSerializer<T>,
    ): ProducerFactory<String, T> =
        DefaultKafkaProducerFactory(
            mutableMapOf<String, Any>(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ),
            StringSerializer(),
            valueSerializer,
        )
}
