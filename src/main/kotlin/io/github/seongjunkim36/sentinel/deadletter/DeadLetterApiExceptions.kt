package io.github.seongjunkim36.sentinel.deadletter

import java.util.UUID

class DeadLetterReplayNotFoundException(
    val deadLetterId: UUID,
    override val message: String = "Dead-letter replay target not found in scoped tenant.",
) : RuntimeException(message)

class DeadLetterReplayBlockedException(
    val deadLetterId: UUID,
    val replayStatus: DeadLetterStatus,
    val replayOutcome: DeadLetterReplayOutcome,
    override val message: String,
) : RuntimeException(message)
