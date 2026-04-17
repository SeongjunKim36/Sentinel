package io.github.seongjunkim36.sentinel.ingestion.api

import io.github.seongjunkim36.sentinel.ingestion.application.RawEventPublisher
import io.github.seongjunkim36.sentinel.ingestion.application.SourcePollingService
import io.github.seongjunkim36.sentinel.ingestion.application.SourcePollingValidationException
import io.github.seongjunkim36.sentinel.shared.Event
import io.github.seongjunkim36.sentinel.shared.EventMetadata
import io.github.seongjunkim36.sentinel.shared.PollingSourcePlugin
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.support.StaticListableBeanFactory
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

class SourcePollingControllerTests {
    private val jsonMapper = JsonMapper.builder().findAndAddModules().build()

    @Test
    fun `accepts source polling request and publishes normalized events`() {
        val rawEventPublisher = RecordingRawEventPublisher()
        val controller =
            SourcePollingController(
                sourcePollingService =
                    SourcePollingService(
                        pollingSourcePlugins = listOf(TestPollingSourcePlugin()),
                        rawEventPublisher = rawEventPublisher,
                    ),
                tracerProvider = StaticListableBeanFactory().getBeanProvider(io.micrometer.tracing.Tracer::class.java),
                jsonMapper = jsonMapper,
            )
        val mockMvc =
            MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(SourcePollingApiExceptionHandler())
                .build()

        mockMvc.post("/api/v1/sources/rss/poll") {
            header("X-Sentinel-Tenant-Id", "tenant-alpha")
            contentType = MediaType.APPLICATION_JSON
            content = """{"feedUrl":"https://feeds.example.com/releases.xml","maxItems":1}"""
        }.andExpect {
            status { isAccepted() }
            jsonPath("$.accepted") { value(true) }
            jsonPath("$.sourceType") { value("rss") }
            jsonPath("$.tenantId") { value("tenant-alpha") }
            jsonPath("$.publishedCount") { value(1) }
            jsonPath("$.sourceIds[0]") { value("rss-entry-1001") }
            jsonPath("$.traceId") { isNotEmpty() }
        }

        assertThat(rawEventPublisher.publishedEvents).hasSize(1)
        assertThat(rawEventPublisher.publishedEvents.single().sourceType).isEqualTo("rss")
    }

    @Test
    fun `returns 404 contract when source plugin is not registered for polling`() {
        val controller =
            SourcePollingController(
                sourcePollingService =
                    SourcePollingService(
                        pollingSourcePlugins = emptyList(),
                        rawEventPublisher = RecordingRawEventPublisher(),
                    ),
                tracerProvider = StaticListableBeanFactory().getBeanProvider(io.micrometer.tracing.Tracer::class.java),
                jsonMapper = jsonMapper,
            )
        val mockMvc =
            MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(SourcePollingApiExceptionHandler())
                .build()

        mockMvc.post("/api/v1/sources/rss/poll") {
            header("X-Sentinel-Tenant-Id", "tenant-alpha")
            contentType = MediaType.APPLICATION_JSON
            content = """{"feedUrl":"https://feeds.example.com/releases.xml"}"""
        }.andExpect {
            status { isNotFound() }
            header { string("Cache-Control", "no-store") }
            jsonPath("$.scope") { value("source-polling") }
            jsonPath("$.errorCode") { value("SOURCE_PLUGIN_NOT_FOUND") }
            jsonPath("$.type") { value("urn:sentinel:error:source-plugin-not-found") }
        }
    }

    @Test
    fun `returns 400 contract when source polling request is invalid`() {
        val controller =
            SourcePollingController(
                sourcePollingService =
                    SourcePollingService(
                        pollingSourcePlugins = listOf(InvalidRequestPollingSourcePlugin()),
                        rawEventPublisher = RecordingRawEventPublisher(),
                    ),
                tracerProvider = StaticListableBeanFactory().getBeanProvider(io.micrometer.tracing.Tracer::class.java),
                jsonMapper = jsonMapper,
            )
        val mockMvc =
            MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(SourcePollingApiExceptionHandler())
                .build()

        mockMvc.post("/api/v1/sources/rss/poll") {
            header("X-Sentinel-Tenant-Id", "tenant-alpha")
            contentType = MediaType.APPLICATION_JSON
            content = """{"feedUrl":"https://feeds.example.com/releases.xml"}"""
        }.andExpect {
            status { isBadRequest() }
            header { string("Cache-Control", "no-store") }
            jsonPath("$.scope") { value("source-polling") }
            jsonPath("$.errorCode") { value("SOURCE_POLL_REQUEST_INVALID") }
            jsonPath("$.type") { value("urn:sentinel:error:source-poll-request-invalid") }
            jsonPath("$.sourceType") { value("rss") }
            jsonPath("$.detail") { value("feedUrl is not allowed for this test plugin") }
        }
    }

    private class RecordingRawEventPublisher : RawEventPublisher {
        val publishedEvents = mutableListOf<Event>()

        override fun publish(event: Event) {
            publishedEvents += event
        }
    }

    private class TestPollingSourcePlugin : PollingSourcePlugin {
        override val type: String = "rss"

        override fun poll(
            tenantId: String,
            request: JsonNode,
            headers: Map<String, String>,
        ): List<Event> =
            listOf(
                Event(
                    id = UUID.randomUUID(),
                    sourceType = type,
                    sourceId = "rss-entry-1001",
                    tenantId = tenantId,
                    payload =
                        mapOf(
                            "message" to "Checkout resilience improvements",
                            "feedUrl" to request.get("feedUrl").nodeText(),
                        ),
                    metadata = EventMetadata(sourceVersion = "rss-2.0", headers = headers),
                ),
            )
    }

    private class InvalidRequestPollingSourcePlugin : PollingSourcePlugin {
        override val type: String = "rss"

        override fun poll(
            tenantId: String,
            request: JsonNode,
            headers: Map<String, String>,
        ): List<Event> {
            throw SourcePollingValidationException(type, "feedUrl is not allowed for this test plugin")
        }
    }
}

private fun JsonNode?.nodeText(): String =
    when {
        this == null || isNull -> ""
        isTextual -> toString().trim('"')
        else -> toString()
    }
