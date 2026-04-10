package io.github.seongjunkim36.sentinel.deadletter

import java.util.UUID
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/dead-letters")
class DeadLetterController(
    private val deadLetterStore: DeadLetterStore,
    private val deadLetterReplayService: DeadLetterReplayService,
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
                tenantId = tenantId,
                channel = channel,
                limit = limit,
            ),
        )

    @PostMapping("/{id}/replay")
    fun replay(
        @PathVariable id: UUID,
    ): ResponseEntity<Map<String, Any>> {
        val replayResult = deadLetterReplayService.replay(id)
        if (replayResult == null) {
            return ResponseEntity.notFound().build()
        }

        return ResponseEntity.ok(
            mapOf(
                "id" to replayResult.id,
                "replayed" to replayResult.replayed,
                "status" to replayResult.status.name,
                "message" to replayResult.message,
            ),
        )
    }
}
