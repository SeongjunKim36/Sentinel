package io.github.seongjunkim36.sentinel

import io.github.seongjunkim36.sentinel.shared.AnalysisResult
import io.github.seongjunkim36.sentinel.shared.ClassifiedEvent
import io.github.seongjunkim36.sentinel.shared.DeadLetterEvent
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
    fun analysisResultProducerFactory(
        @Value("\${spring.kafka.bootstrap-servers}") bootstrapServers: String,
        jsonMapper: JsonMapper,
    ): ProducerFactory<String, AnalysisResult> =
        jsonProducerFactory(
            bootstrapServers = bootstrapServers,
            valueSerializer = JacksonJsonSerializer<AnalysisResult>(jsonMapper).noTypeInfo(),
        )

    @Bean
    fun routedResultProducerFactory(
        @Value("\${spring.kafka.bootstrap-servers}") bootstrapServers: String,
        jsonMapper: JsonMapper,
    ): ProducerFactory<String, AnalysisResult> =
        jsonProducerFactory(
            bootstrapServers = bootstrapServers,
            valueSerializer = JacksonJsonSerializer<AnalysisResult>(jsonMapper).noTypeInfo(),
        )

    @Bean
    fun deadLetterEventProducerFactory(
        @Value("\${spring.kafka.bootstrap-servers}") bootstrapServers: String,
        jsonMapper: JsonMapper,
    ): ProducerFactory<String, DeadLetterEvent> =
        jsonProducerFactory(
            bootstrapServers = bootstrapServers,
            valueSerializer = JacksonJsonSerializer<DeadLetterEvent>(jsonMapper).noTypeInfo(),
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
    fun classifiedEventConsumerFactory(
        @Value("\${spring.kafka.bootstrap-servers}") bootstrapServers: String,
        jsonMapper: JsonMapper,
    ): ConsumerFactory<String, ClassifiedEvent> =
        jsonConsumerFactory(
            bootstrapServers = bootstrapServers,
            valueDeserializer = JacksonJsonDeserializer(ClassifiedEvent::class.java, jsonMapper)
                .trustedPackages("io.github.seongjunkim36.sentinel")
                .ignoreTypeHeaders(),
        )

    @Bean
    fun analysisResultConsumerFactory(
        @Value("\${spring.kafka.bootstrap-servers}") bootstrapServers: String,
        jsonMapper: JsonMapper,
    ): ConsumerFactory<String, AnalysisResult> =
        jsonConsumerFactory(
            bootstrapServers = bootstrapServers,
            valueDeserializer = JacksonJsonDeserializer(AnalysisResult::class.java, jsonMapper)
                .trustedPackages("io.github.seongjunkim36.sentinel")
                .ignoreTypeHeaders(),
        )

    @Bean
    fun eventKafkaTemplate(
        eventProducerFactory: ProducerFactory<String, Event>,
    ): KafkaTemplate<String, Event> = observedKafkaTemplate(eventProducerFactory)

    @Bean
    fun classifiedEventKafkaTemplate(
        classifiedEventProducerFactory: ProducerFactory<String, ClassifiedEvent>,
    ): KafkaTemplate<String, ClassifiedEvent> = observedKafkaTemplate(classifiedEventProducerFactory)

    @Bean
    fun analysisResultKafkaTemplate(
        analysisResultProducerFactory: ProducerFactory<String, AnalysisResult>,
    ): KafkaTemplate<String, AnalysisResult> = observedKafkaTemplate(analysisResultProducerFactory)

    @Bean
    fun routedResultKafkaTemplate(
        routedResultProducerFactory: ProducerFactory<String, AnalysisResult>,
    ): KafkaTemplate<String, AnalysisResult> = observedKafkaTemplate(routedResultProducerFactory)

    @Bean
    fun deadLetterEventKafkaTemplate(
        deadLetterEventProducerFactory: ProducerFactory<String, DeadLetterEvent>,
    ): KafkaTemplate<String, DeadLetterEvent> = observedKafkaTemplate(deadLetterEventProducerFactory)

    @Bean
    fun eventKafkaListenerContainerFactory(
        eventConsumerFactory: ConsumerFactory<String, Event>,
    ): ConcurrentKafkaListenerContainerFactory<String, Event> =
        jsonKafkaListenerContainerFactory(eventConsumerFactory)

    @Bean
    fun classifiedEventKafkaListenerContainerFactory(
        classifiedEventConsumerFactory: ConsumerFactory<String, ClassifiedEvent>,
    ): ConcurrentKafkaListenerContainerFactory<String, ClassifiedEvent> =
        jsonKafkaListenerContainerFactory(classifiedEventConsumerFactory)

    @Bean
    fun analysisResultKafkaListenerContainerFactory(
        analysisResultConsumerFactory: ConsumerFactory<String, AnalysisResult>,
    ): ConcurrentKafkaListenerContainerFactory<String, AnalysisResult> =
        jsonKafkaListenerContainerFactory(analysisResultConsumerFactory)

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

    private fun <T : Any> jsonConsumerFactory(
        bootstrapServers: String,
        valueDeserializer: JacksonJsonDeserializer<T>,
    ): ConsumerFactory<String, T> =
        DefaultKafkaConsumerFactory(
            mutableMapOf<String, Any>(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ),
            StringDeserializer(),
            valueDeserializer,
        )

    private fun <T : Any> jsonKafkaListenerContainerFactory(
        consumerFactory: ConsumerFactory<String, T>,
    ): ConcurrentKafkaListenerContainerFactory<String, T> =
        ConcurrentKafkaListenerContainerFactory<String, T>().apply {
            setConsumerFactory(consumerFactory)
            containerProperties.isObservationEnabled = true
        }

    private fun <T : Any> observedKafkaTemplate(
        producerFactory: ProducerFactory<String, T>,
    ): KafkaTemplate<String, T> =
        KafkaTemplate(producerFactory).apply {
            setObservationEnabled(true)
        }
}
