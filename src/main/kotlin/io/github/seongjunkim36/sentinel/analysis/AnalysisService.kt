package io.github.seongjunkim36.sentinel.analysis

import io.github.seongjunkim36.sentinel.shared.AnalysisDetail
import io.github.seongjunkim36.sentinel.shared.AnalysisResult
import io.github.seongjunkim36.sentinel.shared.ClassifiedEvent
import io.github.seongjunkim36.sentinel.shared.LlmMetadata
import io.github.seongjunkim36.sentinel.shared.ResultCategories
import io.github.seongjunkim36.sentinel.shared.RoutingDecision
import io.github.seongjunkim36.sentinel.shared.RoutingPriority
import io.github.seongjunkim36.sentinel.shared.Severity
import java.time.Duration
import kotlin.math.min
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AnalysisService(
    private val llmClient: LlmClient,
    private val analysisProperties: AnalysisProperties,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun analyze(classifiedEvent: ClassifiedEvent): AnalysisResult {
        val llmResponse = analyzeWithRetry(classifiedEvent)

        return toAnalysisResult(classifiedEvent, llmResponse)
    }

    fun toFailureResult(
        classifiedEvent: ClassifiedEvent,
        exception: Throwable,
    ): AnalysisResult {
        val attempts = (exception as? AnalysisRetriesExhaustedException)?.attempts ?: normalizedRetryProperties().maxAttempts
        val reason = exception.cause?.message ?: exception.message ?: exception.javaClass.simpleName
        val channels = analysisProperties.failureRouting.channels.distinct().ifEmpty { listOf("telegram") }

        return AnalysisResult(
            eventId = classifiedEvent.event.id,
            tenantId = classifiedEvent.event.tenantId,
            category = ResultCategories.ANALYSIS_FAILURE,
            severity = Severity.CRITICAL,
            confidence = 1.0,
            summary = "Analysis pipeline failure for ${classifiedEvent.event.sourceType} event ${classifiedEvent.event.sourceId}.",
            detail = AnalysisDetail(
                analysis =
                    "LLM analysis failed after $attempts attempt(s). Original category was ${classifiedEvent.category}. " +
                        "Reason: $reason",
                actionItems =
                    listOf(
                        "Check Sentinel analysis consumer logs and LLM provider health.",
                        "Replay the original event after recovery.",
                    ),
            ),
            llmMetadata = LlmMetadata(
                model = "analysis-fallback",
                promptVersion = "failure-routing-v1",
            ),
            routing = RoutingDecision(
                channels = channels,
                priority = RoutingPriority.IMMEDIATE,
            ),
        )
    }

    private fun analyzeWithRetry(classifiedEvent: ClassifiedEvent): LlmAnalysisResponse {
        val retry = normalizedRetryProperties()
        var attempt = 1
        var backoff = retry.initialBackoff

        while (true) {
            try {
                return llmClient.analyze(classifiedEvent)
            } catch (exception: Exception) {
                if (attempt >= retry.maxAttempts) {
                    throw AnalysisRetriesExhaustedException(
                        eventId = classifiedEvent.event.id,
                        tenantId = classifiedEvent.event.tenantId,
                        attempts = attempt,
                        cause = exception,
                    )
                }

                logger.warn(
                    "Analysis attempt failed: eventId={}, tenantId={}, attempt={}, maxAttempts={}, nextBackoffMs={}, message={}",
                    classifiedEvent.event.id,
                    classifiedEvent.event.tenantId,
                    attempt,
                    retry.maxAttempts,
                    backoff.toMillis(),
                    exception.message ?: exception.javaClass.simpleName,
                )

                sleep(backoff)
                attempt += 1
                backoff = nextBackoff(backoff = backoff, multiplier = retry.multiplier, maxBackoff = retry.maxBackoff)
            }
        }
    }

    private fun toAnalysisResult(
        classifiedEvent: ClassifiedEvent,
        llmResponse: LlmAnalysisResponse,
    ): AnalysisResult =
        AnalysisResult(
            eventId = classifiedEvent.event.id,
            tenantId = classifiedEvent.event.tenantId,
            category = classifiedEvent.category,
            severity = llmResponse.severity,
            confidence = llmResponse.confidence,
            summary = llmResponse.summary,
            detail = AnalysisDetail(
                analysis = llmResponse.analysis,
                actionItems = llmResponse.actionItems,
            ),
            llmMetadata = LlmMetadata(
                model = llmResponse.model,
                promptVersion = llmResponse.promptVersion,
                tokenUsage = llmResponse.tokenUsage,
                costUsd = llmResponse.costUsd,
                latencyMs = llmResponse.latencyMs,
            ),
            routing = RoutingDecision(
                channels = listOf("slack"),
                priority = RoutingPriority.BATCHED,
            ),
        )

    private fun normalizedRetryProperties(): AnalysisRetryProperties {
        val retry = analysisProperties.retry
        val maxAttempts = retry.maxAttempts.coerceAtLeast(1)
        val initialBackoff = retry.initialBackoff.coerceAtLeast(Duration.ZERO)
        val maxBackoff = retry.maxBackoff.coerceAtLeast(Duration.ZERO)
        val multiplier = retry.multiplier.coerceAtLeast(1.0)

        return AnalysisRetryProperties(
            maxAttempts = maxAttempts,
            initialBackoff = initialBackoff,
            multiplier = multiplier,
            maxBackoff = maxBackoff,
        )
    }

    private fun nextBackoff(
        backoff: Duration,
        multiplier: Double,
        maxBackoff: Duration,
    ): Duration {
        if (backoff.isZero) {
            return Duration.ZERO
        }

        val multipliedMillis = (backoff.toMillis() * multiplier).toLong()
        return Duration.ofMillis(min(multipliedMillis, maxBackoff.toMillis()))
    }

    private fun sleep(backoff: Duration) {
        if (backoff.isZero) {
            return
        }

        try {
            Thread.sleep(backoff.toMillis())
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("Interrupted during analysis retry backoff", exception)
        }
    }
}
