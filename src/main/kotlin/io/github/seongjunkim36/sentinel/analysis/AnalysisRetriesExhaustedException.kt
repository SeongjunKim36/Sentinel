package io.github.seongjunkim36.sentinel.analysis

import java.util.UUID

class AnalysisRetriesExhaustedException(
    val eventId: UUID,
    val tenantId: String,
    val attempts: Int,
    cause: Throwable,
) : RuntimeException(
        "Analysis failed after $attempts attempt(s) for eventId=$eventId tenantId=$tenantId",
        cause,
    )
