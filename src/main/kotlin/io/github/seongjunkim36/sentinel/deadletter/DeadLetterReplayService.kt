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
            return DeadLetterReplayResult(
                id = id,
                replayed = false,
                status = record.status,
                outcome = DeadLetterReplayOutcome.REPLAY_BLOCKED,
                message = "Replay requires an operator note",
            )
        }

        if (record.replayCount >= maxReplayAttempts) {
            return DeadLetterReplayResult(
                id = id,
                replayed = false,
                status = record.status,
                outcome = DeadLetterReplayOutcome.REPLAY_BLOCKED,
                message = "Replay blocked: max replay attempts reached ($maxReplayAttempts)",
            )
        }

        val nextReplayAt = record.lastReplayAt?.plus(cooldown)
        if (nextReplayAt != null && nextReplayAt.isAfter(now)) {
            val remainingSeconds = Duration.between(now, nextReplayAt).seconds.coerceAtLeast(1)
            return DeadLetterReplayResult(
                id = id,
                replayed = false,
                status = record.status,
                outcome = DeadLetterReplayOutcome.REPLAY_BLOCKED,
                message = "Replay blocked: cooldown active for ${remainingSeconds}s",
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
                    DeadLetterReplayResult(
                        id = id,
                        replayed = true,
                        status = DeadLetterStatus.REPLAYED,
                        outcome = DeadLetterReplayOutcome.REPLAYED,
                        message = "Replay published to routed results topic",
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

            DeadLetterReplayResult(
                id = id,
                replayed = false,
                status = DeadLetterStatus.REPLAY_FAILED,
                outcome = DeadLetterReplayOutcome.REPLAY_FAILED,
                message = errorMessage,
            )
        }
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
