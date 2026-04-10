package io.github.seongjunkim36.sentinel.ingestion.api

import io.github.seongjunkim36.sentinel.SentinelApplication
import io.github.seongjunkim36.sentinel.SentinelTopics
import io.github.seongjunkim36.sentinel.shared.Event
import java.time.Duration
import org.apache.kafka.clients.consumer.ConsumerRecord
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
import org.springframework.http.MediaType
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.post
import org.springframework.web.context.WebApplicationContext

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    classes = [WebhookKafkaIntegrationTests.TestApplication::class],
)
@EmbeddedKafka(
    partitions = 1,
    topics = [SentinelTopics.RAW_EVENTS],
    bootstrapServersProperty = "spring.kafka.bootstrap-servers",
)
class WebhookKafkaIntegrationTests {
    @Autowired
    lateinit var embeddedKafkaBroker: EmbeddedKafkaBroker

    @Autowired
    lateinit var webApplicationContext: WebApplicationContext

    @Test
    fun `publishes a normalized raw event to Kafka`() {
        val mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build()
        val consumerProps = KafkaTestUtils.consumerProps(embeddedKafkaBroker, "raw-events-test", false)
        val consumer =
            DefaultKafkaConsumerFactory(
                consumerProps,
                StringDeserializer(),
                JacksonJsonDeserializer(Event::class.java)
                    .trustedPackages("io.github.seongjunkim36.sentinel")
                    .ignoreTypeHeaders(),
            ).createConsumer()

        consumer.use {
            embeddedKafkaBroker.consumeFromAnEmbeddedTopic(it, SentinelTopics.RAW_EVENTS)

            mockMvc.post("/api/v1/webhooks/sentry") {
                contentType = MediaType.APPLICATION_JSON
                header("X-Sentinel-Tenant-Id", "tenant-alpha")
                header("sentry-hook-version", "v1")
                content =
                    """
                    {
                      "event_id": "evt-123",
                      "message": "Example sentry event"
                    }
                    """.trimIndent()
            }
                .andExpect {
                    status { isAccepted() }
                    jsonPath("$.accepted") { value(true) }
                    jsonPath("$.sourceType") { value("sentry") }
                    jsonPath("$.tenantId") { value("tenant-alpha") }
                    jsonPath("$.traceId") { isNotEmpty() }
                }

            val record = KafkaTestUtils.getSingleRecord(it, SentinelTopics.RAW_EVENTS, Duration.ofSeconds(10))

            assertThat(record.key()).isEqualTo("tenant-alpha")
            assertThat(record.value().sourceType).isEqualTo("sentry")
            assertThat(record.value().sourceId).isEqualTo("evt-123")
            assertThat(record.value().tenantId).isEqualTo("tenant-alpha")
            assertThat(record.value().metadata.sourceVersion).isEqualTo("v1")
            assertThat(record.value().metadata.traceId).isNotBlank()
            assertThat(record.propagatedTraceId()).isEqualTo(record.value().metadata.traceId)
        }
    }

    private fun ConsumerRecord<String, Event>.propagatedTraceId(): String? {
        headers().lastHeader("x-sentinel-trace-id")?.let { header ->
            return header.value().toString(Charsets.UTF_8)
        }

        val traceparentHeader = headers().lastHeader("traceparent") ?: return null
        val traceparent = traceparentHeader.value().toString(Charsets.UTF_8)
        return traceparent.split("-").getOrNull(1)
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
