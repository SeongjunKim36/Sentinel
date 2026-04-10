package io.github.seongjunkim36.sentinel.deadletter

import io.github.seongjunkim36.sentinel.shared.AnalysisResult
import io.github.seongjunkim36.sentinel.shared.DeadLetterPayloadType
import java.time.Instant
import java.util.UUID
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper

@Service
class DeadLetterReplayService(
    private val deadLetterStore: DeadLetterStore,
    private val deadLetterReplayPublisher: DeadLetterReplayPublisher,
    private val jsonMapper: JsonMapper,
) {
    fun replay(id: UUID): DeadLetterReplayResult? {
        val record = deadLetterStore.findById(id) ?: return null

        return try {
            when (record.payloadType) {
                DeadLetterPayloadType.ANALYSIS_RESULT -> {
                    val analysisResult = jsonMapper.readValue(record.payload, AnalysisResult::class.java)
                    deadLetterReplayPublisher.publishAnalysisResult(analysisResult)
                    deadLetterStore.markReplayed(id = id, replayedAt = Instant.now())
                    DeadLetterReplayResult(
                        id = id,
                        replayed = true,
                        status = DeadLetterStatus.REPLAYED,
                        message = "Replay published to routed results topic",
                    )
                }
            }
        } catch (exception: Exception) {
            val errorMessage = exception.message ?: exception.javaClass.simpleName
            deadLetterStore.markReplayFailed(
                id = id,
                replayError = errorMessage,
                replayedAt = Instant.now(),
            )

            DeadLetterReplayResult(
                id = id,
                replayed = false,
                status = DeadLetterStatus.REPLAY_FAILED,
                message = errorMessage,
            )
        }
    }
}

data class DeadLetterReplayResult(
    val id: UUID,
    val replayed: Boolean,
    val status: DeadLetterStatus,
    val message: String,
)
