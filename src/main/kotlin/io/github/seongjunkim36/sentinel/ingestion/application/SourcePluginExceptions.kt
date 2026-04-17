package io.github.seongjunkim36.sentinel.ingestion.application

class UnsupportedSourceTypeException(
    sourceType: String,
) : IllegalArgumentException("No source plugin registered for type '$sourceType'")

class SourcePollingValidationException(
    val sourceType: String,
    override val message: String,
) : IllegalArgumentException(message)
