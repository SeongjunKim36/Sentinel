package io.github.seongjunkim36.sentinel.ingestion

import io.github.seongjunkim36.sentinel.ingestion.application.SourcePollingValidationException
import io.github.seongjunkim36.sentinel.shared.Event
import io.github.seongjunkim36.sentinel.shared.EventMetadata
import io.github.seongjunkim36.sentinel.shared.PollingSourcePlugin
import java.io.StringReader
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import tools.jackson.databind.JsonNode

@Component
class RssSourcePlugin(
    restClientBuilder: RestClient.Builder,
) : PollingSourcePlugin {
    companion object {
        private const val DEFAULT_MAX_ITEMS = 10
        private const val MAX_ITEMS_LIMIT = 50
    }

    override val type: String = "rss"

    private val restClient = restClientBuilder.build()

    override fun poll(
        tenantId: String,
        request: JsonNode,
        headers: Map<String, String>,
    ): List<Event> {
        val pollRequest = toPollRequest(request)
        val feedBody =
            restClient.get()
                .uri(pollRequest.feedUrl)
                .accept(MediaType.APPLICATION_RSS_XML, MediaType.APPLICATION_XML, MediaType.TEXT_XML)
                .retrieve()
                .body(String::class.java)
                ?.trim()
                .orEmpty()

        if (feedBody.isBlank()) {
            throw SourcePollingValidationException(type, "RSS feed response body was empty")
        }

        val feed = parseFeed(feedBody, pollRequest.feedUrl)
        return feed.entries
            .take(pollRequest.maxItems)
            .map { entry ->
                val message =
                    listOf(entry.title.takeIf { it.isNotBlank() }, entry.summary.takeIf { it.isNotBlank() })
                        .joinToString(" - ")
                        .ifBlank { "Feed update from ${feed.feedTitle}" }
                val sourceId = sourceIdFor(entry = entry, fallbackMessage = message)

                Event(
                    sourceType = type,
                    sourceId = sourceId,
                    tenantId = tenantId,
                    payload =
                        mapOf(
                            "message" to message,
                            "title" to entry.title,
                            "summary" to entry.summary,
                            "link" to entry.link,
                            "guid" to entry.guid,
                            "publishedAt" to entry.publishedAt,
                            "feedTitle" to feed.feedTitle,
                            "feedUrl" to pollRequest.feedUrl,
                            "feedFormat" to feed.format,
                        ),
                    metadata =
                        EventMetadata(
                            sourceVersion = feed.sourceVersion,
                            headers = headers + ("x-sentinel-source-feed-url" to pollRequest.feedUrl),
                            traceId = headers["x-sentinel-trace-id"]?.ifBlank { null },
                        ),
                )
            }
    }

    private fun toPollRequest(request: JsonNode): RssPollRequest {
        val feedUrl = request.get("feedUrl").nodeText().trim()
        if (feedUrl.isBlank()) {
            throw SourcePollingValidationException(type, "feedUrl is required")
        }

        val feedUri =
            runCatching { URI(feedUrl) }.getOrElse {
                throw SourcePollingValidationException(type, "feedUrl must be a valid absolute URI")
            }
        if (!feedUri.isAbsolute || feedUri.scheme !in setOf("http", "https")) {
            throw SourcePollingValidationException(type, "feedUrl must use http or https")
        }

        val maxItemsNode = request.get("maxItems")
        val maxItems =
            when {
                maxItemsNode == null || maxItemsNode.isNull -> DEFAULT_MAX_ITEMS
                maxItemsNode.canConvertToInt() -> maxItemsNode.intValue()
                else -> throw SourcePollingValidationException(type, "maxItems must be a valid integer")
            }
        if (maxItems !in 1..MAX_ITEMS_LIMIT) {
            throw SourcePollingValidationException(type, "maxItems must be between 1 and $MAX_ITEMS_LIMIT")
        }

        return RssPollRequest(
            feedUrl = feedUrl,
            maxItems = maxItems,
        )
    }

    private fun parseFeed(
        xml: String,
        feedUrl: String,
    ): ParsedFeed {
        val documentBuilderFactory = DocumentBuilderFactory.newInstance()
        documentBuilderFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        documentBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        documentBuilderFactory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        documentBuilderFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        documentBuilderFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
        documentBuilderFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        documentBuilderFactory.isNamespaceAware = true
        documentBuilderFactory.isXIncludeAware = false
        documentBuilderFactory.isExpandEntityReferences = false

        val document =
            documentBuilderFactory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val root =
            document.documentElement
                ?: throw SourcePollingValidationException(type, "RSS feed did not contain a document element")

        return when (root.localNameOrNodeName().lowercase()) {
            "rss" -> parseRss(root, feedUrl)
            "feed" -> parseAtom(root, feedUrl)
            else -> throw SourcePollingValidationException(type, "Unsupported feed format for RSS source plugin")
        }
    }

    private fun parseRss(
        root: Element,
        feedUrl: String,
    ): ParsedFeed {
        val channel =
            root.childElements().firstOrNull { it.localNameOrNodeName().equals("channel", ignoreCase = true) }
                ?: throw SourcePollingValidationException(type, "RSS channel element is missing")
        val feedTitle = channel.childText("title").ifBlank { feedUrl }
        val items =
            channel.childElements()
                .filter { it.localNameOrNodeName().equals("item", ignoreCase = true) }
                .map { item ->
                    ParsedFeedEntry(
                        title = item.childText("title"),
                        summary = item.childText("description"),
                        link = item.childText("link"),
                        guid = item.childText("guid"),
                        publishedAt = item.childText("pubDate"),
                    )
                }

        return ParsedFeed(
            feedTitle = feedTitle,
            sourceVersion = root.getAttribute("version").ifBlank { "rss-2.0" },
            format = "rss",
            entries = items,
        )
    }

    private fun parseAtom(
        root: Element,
        feedUrl: String,
    ): ParsedFeed {
        val feedTitle = root.childText("title").ifBlank { feedUrl }
        val entries =
            root.childElements()
                .filter { it.localNameOrNodeName().equals("entry", ignoreCase = true) }
                .map { entry ->
                    val linkElement =
                        entry.childElements().firstOrNull { it.localNameOrNodeName().equals("link", ignoreCase = true) }
                    ParsedFeedEntry(
                        title = entry.childText("title"),
                        summary = entry.childText("summary").ifBlank { entry.childText("content") },
                        link = linkElement?.getAttribute("href").orEmpty(),
                        guid = entry.childText("id"),
                        publishedAt = entry.childText("published").ifBlank { entry.childText("updated") },
                    )
                }

        return ParsedFeed(
            feedTitle = feedTitle,
            sourceVersion = "atom",
            format = "atom",
            entries = entries,
        )
    }

    private fun sourceIdFor(
        entry: ParsedFeedEntry,
        fallbackMessage: String,
    ): String {
        val candidate =
            listOf(entry.guid, entry.link, entry.title)
                .firstOrNull { it.isNotBlank() }
                ?.trim()
        if (!candidate.isNullOrBlank()) {
            return candidate
        }

        val digest = MessageDigest.getInstance("SHA-256")
        val payload = "${entry.publishedAt}|$fallbackMessage"
        return digest
            .digest(payload.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}

private data class RssPollRequest(
    val feedUrl: String,
    val maxItems: Int,
)

private data class ParsedFeed(
    val feedTitle: String,
    val sourceVersion: String,
    val format: String,
    val entries: List<ParsedFeedEntry>,
)

private data class ParsedFeedEntry(
    val title: String,
    val summary: String,
    val link: String,
    val guid: String,
    val publishedAt: String,
)

private fun Element.childElements(): List<Element> =
    buildList {
        val children = childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child.nodeType == Node.ELEMENT_NODE) {
                add(child as Element)
            }
        }
    }

private fun Element.childText(localName: String): String =
    childElements()
        .firstOrNull { it.localNameOrNodeName().equals(localName, ignoreCase = true) }
        ?.textContent
        ?.trim()
        .orEmpty()

private fun Element.localNameOrNodeName(): String = localName ?: nodeName

private fun JsonNode?.nodeText(): String =
    when {
        this == null || isNull -> ""
        isTextual -> toString().trim('"')
        else -> toString()
    }
