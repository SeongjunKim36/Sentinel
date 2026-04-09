package io.github.seongjunkim36.sentinel.analysis

import io.github.seongjunkim36.sentinel.SentinelApplication
import io.github.seongjunkim36.sentinel.SentinelTopics
import io.github.seongjunkim36.sentinel.classification.ClassifiedEventPublisher
import io.github.seongjunkim36.sentinel.shared.AnalysisResult
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
    classes = [AnalysisKafkaIntegrationTests.TestApplication::class],
)
@EmbeddedKafka(
    partitions = 1,
    topics = [SentinelTopics.CLASSIFIED_EVENTS, SentinelTopics.ANALYSIS_RESULTS],
    bootstrapServersProperty = "spring.kafka.bootstrap-servers",
)
class AnalysisKafkaIntegrationTests {
    @Autowired
    lateinit var embeddedKafkaBroker: EmbeddedKafkaBroker

    @Autowired
    lateinit var classifiedEventPublisher: ClassifiedEventPublisher

    @Test
    fun `consumes classified events and publishes analysis results`() {
        val consumerProps = KafkaTestUtils.consumerProps(embeddedKafkaBroker, "analysis-results-test", false)
        val consumer =
            DefaultKafkaConsumerFactory(
                consumerProps,
                StringDeserializer(),
                JacksonJsonDeserializer(AnalysisResult::class.java)
                    .trustedPackages("io.github.seongjunkim36.sentinel")
                    .ignoreTypeHeaders(),
            ).createConsumer()

        consumer.use {
            embeddedKafkaBroker.consumeFromAnEmbeddedTopic(it, SentinelTopics.ANALYSIS_RESULTS)

            classifiedEventPublisher.publish(
                ClassifiedEvent(
                    event =
                        Event(
                            sourceType = "sentry",
                            sourceId = "evt-901",
                            tenantId = "tenant-alpha",
                            payload = mapOf("message" to "Checkout timeout caused payment failures"),
                            metadata = EventMetadata(sourceVersion = "v1"),
                        ),
                    category = "error",
                    analyzable = true,
                    filtered = false,
                    tags = setOf("source:sentry", "category:error"),
                ),
            )

            val record = KafkaTestUtils.getSingleRecord(it, SentinelTopics.ANALYSIS_RESULTS, Duration.ofSeconds(10))

            assertThat(record.key()).isEqualTo("tenant-alpha")
            assertThat(record.value().category).isEqualTo("error")
            assertThat(record.value().tenantId).isEqualTo("tenant-alpha")
            assertThat(record.value().summary).contains("incident")
            assertThat(record.value().llmMetadata.model).isEqualTo("bootstrap-heuristic-llm")
            assertThat(record.value().detail.analysis).contains("Checkout timeout caused payment failures")
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
