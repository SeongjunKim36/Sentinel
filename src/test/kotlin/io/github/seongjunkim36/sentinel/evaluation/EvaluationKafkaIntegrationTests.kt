package io.github.seongjunkim36.sentinel.evaluation

import io.github.seongjunkim36.sentinel.SentinelApplication
import io.github.seongjunkim36.sentinel.SentinelTopics
import io.github.seongjunkim36.sentinel.analysis.AnalysisResultPublisher
import io.github.seongjunkim36.sentinel.shared.AnalysisDetail
import io.github.seongjunkim36.sentinel.shared.AnalysisResult
import io.github.seongjunkim36.sentinel.shared.LlmMetadata
import io.github.seongjunkim36.sentinel.shared.RoutingDecision
import io.github.seongjunkim36.sentinel.shared.RoutingPriority
import io.github.seongjunkim36.sentinel.shared.Severity
import java.time.Duration
import java.util.UUID
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
    properties = ["sentinel.evaluation.routing.default-channels=telegram"],
    classes = [EvaluationKafkaIntegrationTests.TestApplication::class],
)
@EmbeddedKafka(
    partitions = 1,
    topics = [SentinelTopics.ANALYSIS_RESULTS, SentinelTopics.ROUTED_RESULTS],
    bootstrapServersProperty = "spring.kafka.bootstrap-servers",
)
class EvaluationKafkaIntegrationTests {
    @Autowired
    lateinit var embeddedKafkaBroker: EmbeddedKafkaBroker

    @Autowired
    lateinit var analysisResultPublisher: AnalysisResultPublisher

    @Test
    fun `consumes analysis results and publishes routed results`() {
        val consumerProps = KafkaTestUtils.consumerProps(embeddedKafkaBroker, "routed-results-test", false)
        val consumer =
            DefaultKafkaConsumerFactory(
                consumerProps,
                StringDeserializer(),
                JacksonJsonDeserializer(AnalysisResult::class.java)
                    .trustedPackages("io.github.seongjunkim36.sentinel")
                    .ignoreTypeHeaders(),
            ).createConsumer()

        consumer.use {
            embeddedKafkaBroker.consumeFromAnEmbeddedTopic(it, SentinelTopics.ROUTED_RESULTS)

            analysisResultPublisher.publish(
                AnalysisResult(
                    eventId = UUID.randomUUID(),
                    tenantId = "tenant-alpha",
                    category = "error",
                    severity = Severity.HIGH,
                    confidence = 0.33,
                    summary = "Checkout timeout",
                    detail = AnalysisDetail(analysis = "Payment path is degraded"),
                    llmMetadata = LlmMetadata(model = "stub", promptVersion = "v1"),
                    routing = RoutingDecision(channels = listOf("slack"), priority = RoutingPriority.DIGEST),
                ),
            )

            val record = KafkaTestUtils.getSingleRecord(it, SentinelTopics.ROUTED_RESULTS, Duration.ofSeconds(10))

            assertThat(record.key()).isEqualTo("tenant-alpha")
            assertThat(record.value().routing.channels).containsExactly("telegram")
            assertThat(record.value().routing.priority).isEqualTo(RoutingPriority.IMMEDIATE)
            assertThat(record.value().confidence).isEqualTo(0.5)
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
