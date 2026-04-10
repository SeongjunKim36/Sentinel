package io.github.seongjunkim36.sentinel.deadletter

import jakarta.servlet.http.HttpServletRequest
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
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
    @GetMapping
    fun findRecent(
        @RequestParam(required = false) status: DeadLetterStatus?,
        @RequestParam(required = false) tenantId: String?,
        @RequestParam(required = false) channel: String?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): List<DeadLetterRecord> =
        deadLetterStore.findRecent(
            DeadLetterQuery(
                status = status,
                tenantId = normalizeFilter(tenantId),
                channel = normalizeFilter(channel),
                limit = normalizeLimit(limit),
            ),
        )

    @PostMapping("/{id}/replay")
    fun replay(
        @PathVariable id: UUID,
        @RequestBody(required = false) request: DeadLetterReplayRequest?,
        httpServletRequest: HttpServletRequest,
    ): ResponseEntity<Map<String, Any>> {
        deadLetterReplayAuthorizationService.authorizeReplayOrThrow(httpServletRequest)
        val normalizedOperatorNote = normalizeOperatorNote(request?.operatorNote)
        val replayResult = deadLetterReplayService.replay(
            id = id,
            operatorNote = normalizedOperatorNote,
        )
        if (replayResult == null) {
            return ResponseEntity.notFound().build()
        }

        val response =
            mapOf(
                "id" to replayResult.id,
                "replayed" to replayResult.replayed,
                "status" to replayResult.status.name,
                "outcome" to replayResult.outcome.name,
                "message" to replayResult.message,
            )

        if (replayResult.outcome == DeadLetterReplayOutcome.REPLAY_BLOCKED) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response)
        }

        return ResponseEntity.ok(response)
    }

    @GetMapping("/{id}/replay-audits")
    fun findReplayAudits(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "50") limit: Int,
    ): ResponseEntity<List<DeadLetterReplayAuditRecord>> {
        if (deadLetterStore.findById(id) == null) {
            return ResponseEntity.notFound().build()
        }

        return ResponseEntity.ok(
            deadLetterReplayAuditStore.findRecentByDeadLetterId(
                deadLetterId = id,
                limit = normalizeLimit(limit),
            ),
        )
    }

    private fun normalizeLimit(limit: Int): Int = limit.coerceIn(1, deadLetterApiProperties.maxQueryLimit.coerceAtLeast(1))

    private fun normalizeFilter(value: String?): String? = value?.trim()?.takeIf { it.isNotBlank() }

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
