package io.github.seongjunkim36.sentinel.ingestion.api

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.ResponseStatus
import tools.jackson.databind.json.JsonMapper

@Component
class WebhookSignatureValidator(
    private val webhookSecurityProperties: WebhookSecurityProperties,
    private val jsonMapper: JsonMapper,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val clock: Clock = Clock.systemUTC()

    fun validateOrThrow(
        sourceType: String,
        rawPayload: String,
        headers: Map<String, String>,
    ) {
        if (sourceType.lowercase() != "sentry") {
            return
        }

        val sentrySecurity = webhookSecurityProperties.sentry
        if (!sentrySecurity.signatureValidationEnabled) {
            return
        }

        val secret = sentrySecurity.secret.trim()
        if (secret.isBlank()) {
            throw IllegalStateException(
                "sentinel.ingestion.webhook.sentry.secret must be configured when sentry signature validation is enabled",
            )
        }

        validateTimestampOrThrow(headers, sentrySecurity.maxTimestampSkew)

        val receivedSignature =
            normalizeSignature(headers["sentry-hook-signature"])
                .ifBlank {
                    throw InvalidWebhookSignatureException("Missing sentry-hook-signature header")
                }

        val expectedSignatures = expectedSignatures(rawPayload, secret)
        val signatureValid =
            expectedSignatures.any { candidate ->
                MessageDigest.isEqual(
                    candidate.toByteArray(StandardCharsets.UTF_8),
                    receivedSignature.toByteArray(StandardCharsets.UTF_8),
                )
            }

        if (!signatureValid) {
            logger.warn(
                "Rejected sentry webhook due to invalid signature: receivedSignatureLength={}",
                receivedSignature.length,
            )
            throw InvalidWebhookSignatureException("Invalid sentry-hook-signature")
        }
    }

    private fun validateTimestampOrThrow(
        headers: Map<String, String>,
        maxTimestampSkew: java.time.Duration,
    ) {
        val maxSkewSeconds = maxTimestampSkew.toSeconds().coerceAtLeast(0)
        if (maxSkewSeconds == 0L) {
            return
        }

        val timestampHeader =
            headers["sentry-hook-timestamp"]
                ?.trim()
                ?.ifBlank { null }
                ?: throw InvalidWebhookSignatureException("Missing sentry-hook-timestamp header")

        val requestEpochSeconds =
            timestampHeader.toLongOrNull()
                ?: throw InvalidWebhookSignatureException("Invalid sentry-hook-timestamp header")

        val nowEpochSeconds = Instant.now(clock).epochSecond
        val skewSeconds = kotlin.math.abs(nowEpochSeconds - requestEpochSeconds)
        if (skewSeconds > maxSkewSeconds) {
            throw InvalidWebhookSignatureException("Sentry webhook timestamp skew exceeded allowed window")
        }
    }

    private fun expectedSignatures(
        rawPayload: String,
        secret: String,
    ): Set<String> {
        val canonicalPayload =
            runCatching {
                jsonMapper.writeValueAsString(jsonMapper.readTree(rawPayload))
            }.getOrDefault(rawPayload)

        return setOf(rawPayload, canonicalPayload)
            .map { payload -> hmacSha256Hex(secret, payload) }
            .toSet()
    }

    private fun hmacSha256Hex(
        secret: String,
        payload: String,
    ): String {
        val hmac = Mac.getInstance("HmacSHA256")
        hmac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val digest = hmac.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun normalizeSignature(signature: String?): String =
        signature
            ?.trim()
            ?.removePrefix("sha256=")
            ?.lowercase()
            .orEmpty()
}

@ResponseStatus(HttpStatus.UNAUTHORIZED)
class InvalidWebhookSignatureException(
    override val message: String,
) : RuntimeException(message)
