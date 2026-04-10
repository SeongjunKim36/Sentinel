package io.github.seongjunkim36.sentinel.ingestion.api

import java.nio.charset.StandardCharsets
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class WebhookSignatureValidatorTests {
    private val jsonMapper = JsonMapper.builder().findAndAddModules().build()
    private val payload =
        """
        {
          "event_id": "evt-123",
          "message": "Example sentry event"
        }
        """.trimIndent()

    @Test
    fun `skips validation for non-sentry sources`() {
        val validator =
            WebhookSignatureValidator(
                webhookSecurityProperties =
                    WebhookSecurityProperties(
                        sentry =
                            SentryWebhookSecurityProperties(
                                signatureValidationEnabled = true,
                                secret = "test-secret",
                            ),
                    ),
                jsonMapper = jsonMapper,
            )

        assertThatCode {
            validator.validateOrThrow(
                sourceType = "github",
                rawPayload = payload,
                headers = emptyMap(),
            )
        }.doesNotThrowAnyException()
    }

    @Test
    fun `rejects sentry request when signature header is missing`() {
        val validator = enabledValidator()

        assertThatThrownBy {
            validator.validateOrThrow(
                sourceType = "sentry",
                rawPayload = payload,
                headers = mapOf("sentry-hook-timestamp" to Instant.now().epochSecond.toString()),
            )
        }.isInstanceOf(InvalidWebhookSignatureException::class.java)
    }

    @Test
    fun `accepts sentry request with valid signature and timestamp`() {
        val validator = enabledValidator()
        val secret = "test-secret"
        val signature = sign(secret, payload)
        val now = Instant.now().epochSecond.toString()

        assertThatCode {
            validator.validateOrThrow(
                sourceType = "sentry",
                rawPayload = payload,
                headers =
                    mapOf(
                        "sentry-hook-signature" to signature,
                        "sentry-hook-timestamp" to now,
                    ),
            )
        }.doesNotThrowAnyException()
    }

    @Test
    fun `rejects sentry request with stale timestamp`() {
        val validator = enabledValidator()
        val secret = "test-secret"
        val signature = sign(secret, payload)
        val staleTimestamp = Instant.now().minusSeconds(3_600).epochSecond.toString()

        assertThatThrownBy {
            validator.validateOrThrow(
                sourceType = "sentry",
                rawPayload = payload,
                headers =
                    mapOf(
                        "sentry-hook-signature" to signature,
                        "sentry-hook-timestamp" to staleTimestamp,
                    ),
            )
        }.isInstanceOf(InvalidWebhookSignatureException::class.java)
    }

    private fun enabledValidator(): WebhookSignatureValidator =
        WebhookSignatureValidator(
            webhookSecurityProperties =
                WebhookSecurityProperties(
                    sentry =
                        SentryWebhookSecurityProperties(
                            signatureValidationEnabled = true,
                            secret = "test-secret",
                        ),
                ),
            jsonMapper = jsonMapper,
        )

    private fun sign(
        secret: String,
        value: String,
    ): String {
        val hmac = Mac.getInstance("HmacSHA256")
        hmac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val digest = hmac.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
