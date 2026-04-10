package io.github.seongjunkim36.sentinel.delivery

import io.github.seongjunkim36.sentinel.SentinelApplication
import io.github.seongjunkim36.sentinel.SentinelTopics
import io.github.seongjunkim36.sentinel.shared.DeadLetterEvent
import io.github.seongjunkim36.sentinel.shared.DeadLetterPayloadType
import java.nio.file.Files
import java.nio.file.Path
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
import org.springframework.http.MediaType
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    classes = [WebhookDeliveryFailureDeadLetterIntegrationTests.TestApplication::class],
    properties = [
        "sentinel.delivery.default-channels=slack",
    ],
)
@EmbeddedKafka(
    partitions = 1,
    topics = [
        SentinelTopics.RAW_EVENTS,
        SentinelTopics.CLASSIFIED_EVENTS,
        SentinelTopics.ANALYSIS_RESULTS,
        SentinelTopics.ROUTED_RESULTS,
        SentinelTopics.DEAD_LETTER,
    ],
    bootstrapServersProperty = "spring.kafka.bootstrap-servers",
)
class WebhookDeliveryFailureDeadLetterIntegrationTests {
    @Autowired
    lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    lateinit var embeddedKafkaBroker: EmbeddedKafkaBroker

    @Test
    fun `publishes dead-letter event when delivery channel is not configured`() {
        val consumerProps = KafkaTestUtils.consumerProps(embeddedKafkaBroker, "dead-letter-test", false)
        val consumer =
            DefaultKafkaConsumerFactory(
                consumerProps,
                StringDeserializer(),
                JacksonJsonDeserializer(DeadLetterEvent::class.java)
                    .trustedPackages("io.github.seongjunkim36.sentinel")
                    .ignoreTypeHeaders(),
            ).createConsumer()

        consumer.use {
            embeddedKafkaBroker.consumeFromAnEmbeddedTopic(it, SentinelTopics.DEAD_LETTER)

            val mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build()
            val payload = Files.readString(Path.of("samples/sentry/checkout-timeout.json"))

            mockMvc.post("/api/v1/webhooks/sentry") {
                contentType = MediaType.APPLICATION_JSON
                header("X-Sentinel-Tenant-Id", "tenant-alpha")
                header("sentry-hook-version", "v1")
                content = payload
            }
                .andExpect {
                    status { isAccepted() }
                }

            val record = KafkaTestUtils.getSingleRecord(it, SentinelTopics.DEAD_LETTER, Duration.ofSeconds(10))

            assertThat(record.key()).isEqualTo("tenant-alpha")
            assertThat(record.value().sourceStage).isEqualTo("delivery")
            assertThat(record.value().sourceTopic).isEqualTo(SentinelTopics.ROUTED_RESULTS)
            assertThat(record.value().tenantId).isEqualTo("tenant-alpha")
            assertThat(record.value().channel).isEqualTo("slack")
            assertThat(record.value().payloadType).isEqualTo(DeadLetterPayloadType.ANALYSIS_RESULT)
            assertThat(record.value().reason).contains("not configured")
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
