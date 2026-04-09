package io.github.seongjunkim36.sentinel.delivery

import com.sun.net.httpserver.HttpServer
import io.github.seongjunkim36.sentinel.SentinelApplication
import io.github.seongjunkim36.sentinel.SentinelTopics
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration
import org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    classes = [WebhookToTelegramDeliveryIntegrationTests.TestApplication::class],
    properties = [
        "sentinel.delivery.default-channels=telegram",
        "sentinel.delivery.telegram.bot-token=bot-token",
        "sentinel.delivery.telegram.chat-id=chat-1",
    ],
)
@EmbeddedKafka(
    partitions = 1,
    topics = [
        SentinelTopics.RAW_EVENTS,
        SentinelTopics.CLASSIFIED_EVENTS,
        SentinelTopics.ANALYSIS_RESULTS,
        SentinelTopics.ROUTED_RESULTS,
    ],
    bootstrapServersProperty = "spring.kafka.bootstrap-servers",
)
class WebhookToTelegramDeliveryIntegrationTests {
    @Autowired
    lateinit var webApplicationContext: WebApplicationContext

    @BeforeEach
    fun resetServerState() {
        requestPath.set("")
        requestBody.set("")
        requestLatch.set(CountDownLatch(1))
    }

    @Test
    fun `delivers a sample sentry event to telegram through the full pipeline`() {
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
                jsonPath("$.accepted") { value(true) }
                jsonPath("$.sourceType") { value("sentry") }
                jsonPath("$.tenantId") { value("tenant-alpha") }
            }

        assertThat(requestLatch.get().await(10, TimeUnit.SECONDS)).isTrue()
        assertThat(requestPath.get()).isEqualTo("/botbot-token/sendMessage")
        assertThat(requestBody.get()).contains("chat-1")
        assertThat(requestBody.get()).contains("Potential HIGH incident detected from sentry.")
        assertThat(requestBody.get()).contains("Bootstrap analysis inferred an error event")
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

    companion object {
        private val requestPath = AtomicReference("")
        private val requestBody = AtomicReference("")
        private val requestLatch = AtomicReference(CountDownLatch(1))
        private val telegramServerStarted = AtomicBoolean(false)
        private val telegramServer: HttpServer =
            HttpServer.create(InetSocketAddress(0), 0).apply {
                createContext("/") { exchange ->
                    requestPath.set(exchange.requestURI.path)
                    requestBody.set(exchange.requestBody.readAllBytes().toString(StandardCharsets.UTF_8))

                    val response = """{"ok":true,"result":{"message_id":42}}"""
                    exchange.responseHeaders.add("Content-Type", "application/json")
                    exchange.sendResponseHeaders(200, response.toByteArray(StandardCharsets.UTF_8).size.toLong())
                    exchange.responseBody.use { outputStream ->
                        outputStream.write(response.toByteArray(StandardCharsets.UTF_8))
                    }

                    requestLatch.get().countDown()
                }
            }

        @JvmStatic
        @DynamicPropertySource
        fun telegramProperties(registry: DynamicPropertyRegistry) {
            ensureTelegramServerStarted()
            registry.add("sentinel.delivery.telegram.api-base-url") {
                "http://127.0.0.1:${telegramServer.address.port}"
            }
        }

        @JvmStatic
        @AfterAll
        fun stopTelegramServer() {
            if (telegramServerStarted.get()) {
                telegramServer.stop(0)
                telegramServerStarted.set(false)
            }
        }

        @Synchronized
        private fun ensureTelegramServerStarted() {
            if (telegramServerStarted.get()) {
                return
            }

            telegramServer.start()
            telegramServerStarted.set(true)
        }
    }
}
