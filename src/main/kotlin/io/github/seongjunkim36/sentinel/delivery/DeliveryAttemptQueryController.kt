package io.github.seongjunkim36.sentinel.delivery

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/v1/delivery-attempts")
class DeliveryAttemptQueryController(
    private val deliveryAttemptStore: DeliveryAttemptStore,
) {
    companion object {
        private const val MAX_QUERY_LIMIT = 200
    }

    @GetMapping
    fun findRecent(
        @RequestParam(required = false) eventId: UUID?,
        @RequestParam(required = false) tenantId: String?,
        @RequestParam(required = false) channel: String?,
        @RequestParam(required = false) success: Boolean?,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(required = false) cursor: String?,
    ): DeliveryAttemptPageResponse {
        val normalizedLimit = normalizeLimit(limit)
        val records =
            deliveryAttemptStore.findRecent(
                DeliveryAttemptQuery(
                    eventId = eventId,
                    tenantId = normalizeFilter(tenantId),
                    channel = normalizeFilter(channel),
                    success = success,
                    limit = normalizedLimit + 1,
                    cursor = decodeCursor(cursor),
                ),
            )
        val hasMore = records.size > normalizedLimit
        val items = records.take(normalizedLimit)
        val nextCursor = if (hasMore && items.isNotEmpty()) encodeCursor(items.last()) else null

        return DeliveryAttemptPageResponse(
            items = items,
            page = DeliveryAttemptPage(limit = normalizedLimit, hasMore = hasMore, nextCursor = nextCursor),
        )
    }

    private fun normalizeLimit(limit: Int): Int = limit.coerceIn(1, MAX_QUERY_LIMIT)

    private fun normalizeFilter(value: String?): String? = value?.trim()?.takeIf { it.isNotBlank() }

    private fun decodeCursor(cursor: String?): DeliveryAttemptCursor? {
        val normalizedCursor = cursor?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val token =
            kotlin.runCatching {
                String(Base64.getUrlDecoder().decode(normalizedCursor), StandardCharsets.UTF_8)
            }.getOrElse {
                throw invalidCursorException()
            }
        val parts = token.split('|')
        if (parts.size != 2) {
            throw invalidCursorException()
        }

        val attemptedAtEpochMillis = parts[0].toLongOrNull() ?: throw invalidCursorException()
        val id = parts[1].toLongOrNull() ?: throw invalidCursorException()

        return DeliveryAttemptCursor(
            attemptedAt = Instant.ofEpochMilli(attemptedAtEpochMillis),
            id = id,
        )
    }

    private fun encodeCursor(record: DeliveryAttemptRecord): String {
        val token = "${record.attemptedAt.toEpochMilli()}|${record.id}"
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token.toByteArray(StandardCharsets.UTF_8))
    }

    private fun invalidCursorException(): ResponseStatusException =
        ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cursor format")
}

data class DeliveryAttemptPageResponse(
    val items: List<DeliveryAttemptRecord>,
    val page: DeliveryAttemptPage,
)

data class DeliveryAttemptPage(
    val limit: Int,
    val hasMore: Boolean,
    val nextCursor: String?,
)
