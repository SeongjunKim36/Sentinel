package io.github.seongjunkim36.sentinel.ingestion

import io.github.seongjunkim36.sentinel.ingestion.application.SourcePollingValidationException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper

class RssSourcePluginTests {
    private val jsonMapper = JsonMapper.builder().findAndAddModules().build()

    @Test
    fun `polls rss feed and normalizes entries into events`() {
        val restClientBuilder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(restClientBuilder).build()
        val plugin = RssSourcePlugin(restClientBuilder)

        server.expect(requestTo("https://feeds.example.com/releases.xml"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(sampleRssFeed(), MediaType.APPLICATION_XML))

        val events =
            plugin.poll(
                tenantId = "tenant-alpha",
                request =
                    jsonMapper.readTree(
                        """
                        {
                          "feedUrl": "https://feeds.example.com/releases.xml",
                          "maxItems": 2
                        }
                        """.trimIndent(),
                    ),
                headers = mapOf("x-sentinel-trace-id" to "trace-123"),
            )

        assertThat(events).hasSize(2)
        assertThat(events.first().sourceType).isEqualTo("rss")
        assertThat(events.first().sourceId).isEqualTo("release-1001")
        assertThat(events.first().tenantId).isEqualTo("tenant-alpha")
        assertThat(events.first().payload["feedTitle"]).isEqualTo("Platform Releases")
        assertThat(events.first().payload["feedFormat"]).isEqualTo("rss")
        assertThat(events.first().payload["message"].toString()).contains("Checkout resilience improvements")
        assertThat(events.first().metadata.sourceVersion).isEqualTo("2.0")
        assertThat(events.first().metadata.traceId).isEqualTo("trace-123")
        server.verify()
    }

    @Test
    fun `rejects rss poll request when feed url is missing`() {
        val plugin = RssSourcePlugin(RestClient.builder())

        val exception =
            kotlin.runCatching {
                plugin.poll(
                    tenantId = "tenant-alpha",
                    request = jsonMapper.readTree("""{"maxItems":2}"""),
                    headers = emptyMap(),
                )
            }.exceptionOrNull()

        assertThat(exception).isInstanceOf(SourcePollingValidationException::class.java)
        assertThat((exception as SourcePollingValidationException).message).isEqualTo("feedUrl is required")
    }

    @Test
    fun `rejects rss poll request when max items exceeds supported range`() {
        val plugin = RssSourcePlugin(RestClient.builder())

        val exception =
            kotlin.runCatching {
                plugin.poll(
                    tenantId = "tenant-alpha",
                    request =
                        jsonMapper.readTree(
                            """
                            {
                              "feedUrl": "https://feeds.example.com/releases.xml",
                              "maxItems": 100
                            }
                            """.trimIndent(),
                        ),
                    headers = emptyMap(),
                )
            }.exceptionOrNull()

        assertThat(exception).isInstanceOf(SourcePollingValidationException::class.java)
        assertThat((exception as SourcePollingValidationException).message).isEqualTo("maxItems must be between 1 and 50")
    }

    private fun sampleRssFeed(): String =
        """
        <?xml version="1.0" encoding="UTF-8" ?>
        <rss version="2.0">
          <channel>
            <title>Platform Releases</title>
            <item>
              <title>Checkout resilience improvements</title>
              <description>Release improves timeout handling in the payment pipeline.</description>
              <link>https://example.com/releases/1001</link>
              <guid>release-1001</guid>
              <pubDate>Fri, 17 Apr 2026 10:00:00 GMT</pubDate>
            </item>
            <item>
              <title>Incident review digest</title>
              <description>Weekly review covering retries, alerts, and guardrails.</description>
              <link>https://example.com/releases/1002</link>
              <guid>release-1002</guid>
              <pubDate>Fri, 17 Apr 2026 09:00:00 GMT</pubDate>
            </item>
          </channel>
        </rss>
        """.trimIndent()
}
