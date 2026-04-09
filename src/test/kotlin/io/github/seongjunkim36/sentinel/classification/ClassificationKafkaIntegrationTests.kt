package io.github.seongjunkim36.sentinel.classification

import io.github.seongjunkim36.sentinel.SentinelApplication
import io.github.seongjunkim36.sentinel.SentinelTopics
import io.github.seongjunkim36.sentinel.ingestion.application.RawEventPublisher
import io.github.seongjunkim36.sentinel.shared.ClassifiedEvent
import io.github.seongjunkim36.sentinel.shared.Event
import io.github.seongjunkim36.sentinel.shared.EventMetadata
import java.time.Duration
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
        val consumer =
            DefaultKafkaConsumerFactory(
                consumerProps,
                StringDeserializer(),
                JacksonJsonDeserializer(ClassifiedEvent::class.java)
                    .trustedPackages("io.github.seongjunkim36.sentinel")
                    .ignoreTypeHeaders(),
            ).createConsumer()

        consumer.use {
            embeddedKafkaBroker.consumeFromAnEmbeddedTopic(it, SentinelTopics.CLASSIFIED_EVENTS)

            rawEventPublisher.publish(
                Event(
                    sourceType = "sentry",
                    sourceId = "evt-456",
                    tenantId = "tenant-alpha",
                    payload = mapOf("message" to "Database timeout in checkout flow"),
                    metadata = EventMetadata(sourceVersion = "v1"),
                ),
            )

            val record = KafkaTestUtils.getSingleRecord(it, SentinelTopics.CLASSIFIED_EVENTS, Duration.ofSeconds(10))

            assertThat(record.key()).isEqualTo("tenant-alpha")
            assertThat(record.value().category).isEqualTo("error")
            assertThat(record.value().analyzable).isTrue()
            assertThat(record.value().filtered).isFalse()
            assertThat(record.value().event.sourceId).isEqualTo("evt-456")
            assertThat(record.value().event.tenantId).isEqualTo("tenant-alpha")
        }
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
