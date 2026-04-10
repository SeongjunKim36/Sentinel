package io.github.seongjunkim36.sentinel.deadletter

import io.github.seongjunkim36.sentinel.shared.AnalysisResult
import io.github.seongjunkim36.sentinel.shared.DeadLetterPayloadType
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper

@Service
class DeadLetterReplayService(
    private val deadLetterStore: DeadLetterStore,
    private val deadLetterReplayAuditStore: DeadLetterReplayAuditStore,
    private val deadLetterReplayPublisher: DeadLetterReplayPublisher,
    private val jsonMapper: JsonMapper,
    private val replayProperties: DeadLetterReplayProperties,
) {
    fun replay(
        id: UUID,
        operatorNote: String? = null,
    ): DeadLetterReplayResult? {
        val record = deadLetterStore.findById(id) ?: return null
        val normalizedOperatorNote = operatorNote?.trim()?.takeIf { it.isNotBlank() }
        val now = Instant.now()
        val maxReplayAttempts = replayProperties.maxReplayAttempts.coerceAtLeast(1)
        val cooldown = replayProperties.cooldown.coerceAtLeast(Duration.ZERO)

        if (replayProperties.requireOperatorNote && normalizedOperatorNote == null) {
            return saveAuditAndBuildResult(
                id = id,
                status = record.status,
                outcome = DeadLetterReplayOutcome.REPLAY_BLOCKED,
                message = "Replay requires an operator note",
                operatorNote = normalizedOperatorNote,
            )
        }

        if (record.replayCount >= maxReplayAttempts) {
            return saveAuditAndBuildResult(
                id = id,
                status = record.status,
                outcome = DeadLetterReplayOutcome.REPLAY_BLOCKED,
                message = "Replay blocked: max replay attempts reached ($maxReplayAttempts)",
                operatorNote = normalizedOperatorNote,
            )
        }

        val nextReplayAt = record.lastReplayAt?.plus(cooldown)
        if (nextReplayAt != null && nextReplayAt.isAfter(now)) {
            val remainingSeconds = Duration.between(now, nextReplayAt).seconds.coerceAtLeast(1)
            return saveAuditAndBuildResult(
                id = id,
                status = record.status,
                outcome = DeadLetterReplayOutcome.REPLAY_BLOCKED,
                message = "Replay blocked: cooldown active for ${remainingSeconds}s",
                operatorNote = normalizedOperatorNote,
            )
        }

        return try {
            when (record.payloadType) {
                DeadLetterPayloadType.ANALYSIS_RESULT -> {
                    val analysisResult = jsonMapper.readValue(record.payload, AnalysisResult::class.java)
                    deadLetterReplayPublisher.publishAnalysisResult(analysisResult)
                    deadLetterStore.markReplayed(
                        id = id,
                        replayedAt = now,
                        operatorNote = normalizedOperatorNote,
                    )
                    saveAuditAndBuildResult(
                        id = id,
                        status = DeadLetterStatus.REPLAYED,
                        outcome = DeadLetterReplayOutcome.REPLAYED,
                        message = "Replay published to routed results topic",
                        operatorNote = normalizedOperatorNote,
                    )
                }
            }
        } catch (exception: Exception) {
            val errorMessage = exception.message ?: exception.javaClass.simpleName
            deadLetterStore.markReplayFailed(
                id = id,
                replayError = errorMessage,
                replayedAt = now,
                operatorNote = normalizedOperatorNote,
            )

            saveAuditAndBuildResult(
                id = id,
                status = DeadLetterStatus.REPLAY_FAILED,
                outcome = DeadLetterReplayOutcome.REPLAY_FAILED,
                message = errorMessage,
                operatorNote = normalizedOperatorNote,
            )
        }
    }

    private fun saveAuditAndBuildResult(
        id: UUID,
        status: DeadLetterStatus,
        outcome: DeadLetterReplayOutcome,
        message: String,
        operatorNote: String?,
    ): DeadLetterReplayResult {
        deadLetterReplayAuditStore.save(
            DeadLetterReplayAuditWrite(
                deadLetterId = id,
                outcome = outcome,
                status = status,
                message = message,
                operatorNote = operatorNote,
            ),
        )

        return DeadLetterReplayResult(
            id = id,
            replayed = outcome == DeadLetterReplayOutcome.REPLAYED,
            status = status,
            outcome = outcome,
            message = message,
        )
    }
}

enum class DeadLetterReplayOutcome {
    REPLAYED,
    REPLAY_FAILED,
    REPLAY_BLOCKED,
}

data class DeadLetterReplayResult(
    val id: UUID,
    val replayed: Boolean,
    val status: DeadLetterStatus,
    val outcome: DeadLetterReplayOutcome,
    val message: String,
)
