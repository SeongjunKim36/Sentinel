package io.github.seongjunkim36.sentinel.classification

import io.github.seongjunkim36.sentinel.SentinelApplication
import io.github.seongjunkim36.sentinel.SentinelTopics
import io.github.seongjunkim36.sentinel.ingestion.application.RawEventPublisher
import io.github.seongjunkim36.sentinel.shared.ClassifiedEvent
import io.github.seongjunkim36.sentinel.shared.Event
import io.github.seongjunkim36.sentinel.shared.EventMetadata
import java.time.Duration
import java.time.Instant
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration
import org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.kafka.test.utils.KafkaTestUtils

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [ClassificationKafkaIntegrationTests.TestApplication::class],
)
@EmbeddedKafka(
    partitions = 1,
    topics = [SentinelTopics.RAW_EVENTS, SentinelTopics.CLASSIFIED_EVENTS],
    bootstrapServersProperty = "spring.kafka.bootstrap-servers",
)
class ClassificationKafkaIntegrationTests {
    @Autowired
    lateinit var embeddedKafkaBroker: EmbeddedKafkaBroker

    @Autowired
    lateinit var rawEventPublisher: RawEventPublisher

    @Test
    fun `consumes raw events and publishes classified events`() {
        val consumerProps = KafkaTestUtils.consumerProps(embeddedKafkaBroker, "classified-events-test", false)
        consumerProps[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "latest"
        val consumer =
            DefaultKafkaConsumerFactory(
                consumerProps,
                StringDeserializer(),
                JacksonJsonDeserializer(ClassifiedEvent::class.java)
                    .trustedPackages("io.github.seongjunkim36.sentinel")
                    .ignoreTypeHeaders(),
            ).createConsumer()

        consumer.use {
            embeddedKafkaBroker.consumeFromAnEmbeddedTopic(it, true, SentinelTopics.CLASSIFIED_EVENTS)

            rawEventPublisher.publish(
                Event(
                    sourceType = "sentry",
                    sourceId = "evt-456",
                    tenantId = "tenant-alpha",
                    payload = mapOf("message" to "Database timeout in checkout flow"),
                    metadata = EventMetadata(sourceVersion = "v1"),
                ),
            )

            val classifiedEvent =
                waitForClassifiedEvent(
                    consumer = it,
                    sourceId = "evt-456",
                    timeout = Duration.ofSeconds(10),
                )

            assertThat(classifiedEvent.category).isEqualTo("error")
            assertThat(classifiedEvent.analyzable).isTrue()
            assertThat(classifiedEvent.filtered).isFalse()
            assertThat(classifiedEvent.event.sourceId).isEqualTo("evt-456")
            assertThat(classifiedEvent.event.tenantId).isEqualTo("tenant-alpha")
        }
    }

    @Test
    fun `suppresses duplicate raw events with the same source id`() {
        val consumerProps = KafkaTestUtils.consumerProps(embeddedKafkaBroker, "classified-events-dedup-test", false)
        consumerProps[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "latest"
        val consumer =
            DefaultKafkaConsumerFactory(
                consumerProps,
                StringDeserializer(),
                JacksonJsonDeserializer(ClassifiedEvent::class.java)
                    .trustedPackages("io.github.seongjunkim36.sentinel")
                    .ignoreTypeHeaders(),
            ).createConsumer()

        consumer.use {
            embeddedKafkaBroker.consumeFromAnEmbeddedTopic(it, true, SentinelTopics.CLASSIFIED_EVENTS)

            val duplicateEvent =
                Event(
                    sourceType = "sentry",
                    sourceId = "evt-duplicate-001",
                    tenantId = "tenant-alpha",
                    payload = mapOf("message" to "Database timeout in checkout flow"),
                    metadata = EventMetadata(sourceVersion = "v1"),
                )

            rawEventPublisher.publish(duplicateEvent)
            rawEventPublisher.publish(
                Event(
                    sourceType = "sentry",
                    sourceId = "evt-duplicate-001",
                    tenantId = "tenant-alpha",
                    payload = mapOf("message" to "Database timeout in checkout flow"),
                    metadata = EventMetadata(sourceVersion = "v1"),
                ),
            )

            val firstClassifiedEvent =
                waitForClassifiedEvent(
                    consumer = it,
                    sourceId = "evt-duplicate-001",
                    timeout = Duration.ofSeconds(10),
                )

            assertThat(firstClassifiedEvent.event.sourceId).isEqualTo("evt-duplicate-001")
            assertThat(firstClassifiedEvent.filtered).isFalse()
            assertThat(
                countClassifiedEventsForSourceId(
                    consumer = it,
                    sourceId = "evt-duplicate-001",
                    pollWindow = Duration.ofSeconds(2),
                ),
            ).isZero()
        }
    }

    private fun waitForClassifiedEvent(
        consumer: Consumer<String, ClassifiedEvent>,
        sourceId: String,
        timeout: Duration,
    ): ClassifiedEvent {
        val deadline = Instant.now().plus(timeout)
        var found: ClassifiedEvent? = null

        while (Instant.now().isBefore(deadline)) {
            val records = consumer.poll(Duration.ofMillis(250))
            val matchingRecords =
                records.records(SentinelTopics.CLASSIFIED_EVENTS)
                    .map { it.value() }
                    .filter { it.event.sourceId == sourceId }

            if (matchingRecords.size > 1 || (found != null && matchingRecords.isNotEmpty())) {
                throw IllegalStateException("More than one classified record found for sourceId=$sourceId")
            }

            if (matchingRecords.isNotEmpty()) {
                found = matchingRecords.first()
                break
            }
        }

        return found ?: throw IllegalStateException("No classified record found for sourceId=$sourceId")
    }

    private fun countClassifiedEventsForSourceId(
        consumer: Consumer<String, ClassifiedEvent>,
        sourceId: String,
        pollWindow: Duration,
    ): Int {
        val deadline = Instant.now().plus(pollWindow)
        var count = 0

        while (Instant.now().isBefore(deadline)) {
            val records = consumer.poll(Duration.ofMillis(250))
            count +=
                records.records(SentinelTopics.CLASSIFIED_EVENTS)
                    .count { it.value().event.sourceId == sourceId }
        }

        return count
    }

    @SpringBootApplication(
        scanBasePackageClasses = [SentinelApplication::class],
        exclude = [
            DataSourceAutoConfiguration::class,
            HibernateJpaAutoConfiguration::class,
            DataRedisAutoConfiguration::class,
            DataRedisRepositoriesAutoConfiguration::class,
        ],
    )
    class TestApplication
}
