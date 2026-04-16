package io.github.seongjunkim36.sentinel.deadletter

import jakarta.servlet.http.HttpServletRequest
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/v1/dead-letters")
class DeadLetterController(
    private val deadLetterStore: DeadLetterStore,
    private val deadLetterReplayAuditStore: DeadLetterReplayAuditStore,
    private val deadLetterReplayService: DeadLetterReplayService,
    private val deadLetterReplayAuthorizationService: DeadLetterReplayAuthorizationService,
    private val deadLetterApiProperties: DeadLetterApiProperties,
) {
    companion object {
        private const val TENANT_HEADER_NAME = "X-Sentinel-Tenant-Id"
    }

    @GetMapping
    fun findRecent(
        @RequestParam(required = false) status: DeadLetterStatus?,
        @RequestParam(required = false) tenantId: String?,
        @RequestParam(required = false) channel: String?,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(required = false) cursor: String?,
        @RequestHeader(name = TENANT_HEADER_NAME) tenantScopeHeader: String,
    ): DeadLetterPageResponse {
        val scopedTenantId = normalizeTenantScope(tenantScopeHeader)
        ensureTenantFilterMatchesScope(tenantId = normalizeFilter(tenantId), scopedTenantId = scopedTenantId)
        val normalizedLimit = normalizeLimit(limit)
        val records =
            deadLetterStore.findRecent(
                DeadLetterQuery(
                    status = status,
                    tenantId = scopedTenantId,
                    channel = normalizeFilter(channel),
                    limit = normalizedLimit + 1,
                    cursor = decodeDeadLetterCursor(cursor),
                ),
            )
        val hasMore = records.size > normalizedLimit
        val items = records.take(normalizedLimit)
        val nextCursor = if (hasMore && items.isNotEmpty()) encodeDeadLetterCursor(items.last()) else null

        return DeadLetterPageResponse(
            items = items,
            page = DeadLetterPage(limit = normalizedLimit, hasMore = hasMore, nextCursor = nextCursor),
        )
    }

    @PostMapping("/{id}/replay")
    fun replay(
        @PathVariable id: UUID,
        @RequestBody(required = false) request: DeadLetterReplayRequest?,
        @RequestHeader(name = TENANT_HEADER_NAME) tenantScopeHeader: String,
        httpServletRequest: HttpServletRequest,
    ): ResponseEntity<Map<String, Any>> {
        val scopedTenantId = normalizeTenantScope(tenantScopeHeader)
        deadLetterReplayAuthorizationService.authorizeReplayOrThrow(httpServletRequest)
        val deadLetterRecord = deadLetterStore.findById(id) ?: throw DeadLetterReplayNotFoundException(id)
        if (!isRecordInScope(deadLetterRecord, scopedTenantId)) {
            throw DeadLetterReplayNotFoundException(id)
        }

        val normalizedOperatorNote = normalizeOperatorNote(request?.operatorNote)
        val replayResult = deadLetterReplayService.replay(id = id, operatorNote = normalizedOperatorNote) ?: throw DeadLetterReplayNotFoundException(id)

        val response =
            mapOf(
                "id" to replayResult.id,
                "replayed" to replayResult.replayed,
                "status" to replayResult.status.name,
                "outcome" to replayResult.outcome.name,
                "message" to replayResult.message,
            )

        if (replayResult.outcome == DeadLetterReplayOutcome.REPLAY_BLOCKED) {
            throw DeadLetterReplayBlockedException(
                deadLetterId = replayResult.id,
                replayStatus = replayResult.status,
                replayOutcome = replayResult.outcome,
                message = replayResult.message,
            )
        }

        return ResponseEntity.ok(response)
    }

    @GetMapping("/{id}/replay-audits")
    fun findReplayAudits(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(required = false) cursor: String?,
        @RequestHeader(name = TENANT_HEADER_NAME) tenantScopeHeader: String,
    ): ResponseEntity<DeadLetterReplayAuditPageResponse> {
        val scopedTenantId = normalizeTenantScope(tenantScopeHeader)
        val deadLetterRecord = deadLetterStore.findById(id) ?: throw DeadLetterReplayAuditsNotFoundException(id)
        if (!isRecordInScope(deadLetterRecord, scopedTenantId)) {
            throw DeadLetterReplayAuditsNotFoundException(id)
        }

        val normalizedLimit = normalizeLimit(limit)
        val records =
            deadLetterReplayAuditStore.findRecentByDeadLetterId(
                deadLetterId = id,
                query =
                    DeadLetterReplayAuditQuery(
                        limit = normalizedLimit + 1,
                        cursor = decodeReplayAuditCursor(cursor),
                    ),
            )
        val hasMore = records.size > normalizedLimit
        val items = records.take(normalizedLimit)
        val nextCursor = if (hasMore && items.isNotEmpty()) encodeReplayAuditCursor(items.last()) else null

        return ResponseEntity.ok(
            DeadLetterReplayAuditPageResponse(
                items = items,
                page = DeadLetterPage(limit = normalizedLimit, hasMore = hasMore, nextCursor = nextCursor),
            ),
        )
    }

    private fun normalizeLimit(limit: Int): Int = limit.coerceIn(1, deadLetterApiProperties.maxQueryLimit.coerceAtLeast(1))

    private fun normalizeFilter(value: String?): String? = value?.trim()?.takeIf { it.isNotBlank() }

    private fun normalizeTenantScope(tenantScopeHeader: String): String {
        val scopedTenantId = tenantScopeHeader.trim()
        if (scopedTenantId.isBlank()) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "$TENANT_HEADER_NAME header is required",
            )
        }
        return scopedTenantId
    }

    private fun ensureTenantFilterMatchesScope(
        tenantId: String?,
        scopedTenantId: String,
    ) {
        if (tenantId == null) {
            return
        }
        if (tenantId != scopedTenantId) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "tenantId filter must match scoped tenant header",
            )
        }
    }

    private fun isRecordInScope(
        deadLetterRecord: DeadLetterRecord,
        scopedTenantId: String,
    ): Boolean = deadLetterRecord.tenantId == scopedTenantId

    private fun decodeDeadLetterCursor(cursor: String?): DeadLetterCursor? {
        val normalizedCursor = cursor?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val token = decodeCursorToken(normalizedCursor)
        val parts = token.split('|')
        if (parts.size != 2) {
            throw invalidCursorException()
        }

        val createdAtEpochMillis = parts[0].toLongOrNull() ?: throw invalidCursorException()
        val id = kotlin.runCatching { UUID.fromString(parts[1]) }.getOrElse { throw invalidCursorException() }

        return DeadLetterCursor(
            createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
            id = id,
        )
    }

    private fun decodeReplayAuditCursor(cursor: String?): DeadLetterReplayAuditCursor? {
        val normalizedCursor = cursor?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val token = decodeCursorToken(normalizedCursor)
        val parts = token.split('|')
        if (parts.size != 2) {
            throw invalidCursorException()
        }

        val createdAtEpochMillis = parts[0].toLongOrNull() ?: throw invalidCursorException()
        val id = parts[1].toLongOrNull() ?: throw invalidCursorException()

        return DeadLetterReplayAuditCursor(
            createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
            id = id,
        )
    }

    private fun encodeDeadLetterCursor(record: DeadLetterRecord): String = encodeCursorToken("${record.createdAt.toEpochMilli()}|${record.id}")

    private fun encodeReplayAuditCursor(record: DeadLetterReplayAuditRecord): String = encodeCursorToken("${record.createdAt.toEpochMilli()}|${record.id}")

    private fun encodeCursorToken(rawToken: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(rawToken.toByteArray(StandardCharsets.UTF_8))

    private fun decodeCursorToken(cursor: String): String =
        kotlin.runCatching {
            String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
        }.getOrElse {
            throw invalidCursorException()
        }

    private fun invalidCursorException(): ResponseStatusException =
        ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cursor format")

    private fun normalizeOperatorNote(operatorNote: String?): String? {
        val normalized = operatorNote?.trim()?.takeIf { it.isNotBlank() }
        val maxLength = deadLetterApiProperties.maxOperatorNoteLength.coerceAtLeast(1)
        if (normalized != null && normalized.length > maxLength) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "operatorNote exceeds maximum length ($maxLength)",
            )
        }
        return normalized
    }
}

data class DeadLetterReplayRequest(
    val operatorNote: String? = null,
)

data class DeadLetterPageResponse(
    val items: List<DeadLetterRecord>,
    val page: DeadLetterPage,
)

data class DeadLetterReplayAuditPageResponse(
    val items: List<DeadLetterReplayAuditRecord>,
    val page: DeadLetterPage,
)

data class DeadLetterPage(
    val limit: Int,
    val hasMore: Boolean,
    val nextCursor: String?,
)
