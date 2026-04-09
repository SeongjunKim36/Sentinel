package io.github.seongjunkim36.sentinel.delivery

import io.github.seongjunkim36.sentinel.shared.AnalysisResult
import org.springframework.stereotype.Component

@Component
class DeliveryMessageFormatter {
    fun plainText(result: AnalysisResult): String =
        buildString {
            append("[Sentinel] ")
            append(result.severity)
            append(" incident")
            appendLine()
            append("Category: ")
            append(result.category)
            appendLine()
            append("Summary: ")
            append(result.summary)
            appendLine()
            append("Confidence: ")
            append(String.format("%.2f", result.confidence))
            if (result.detail.actionItems.isNotEmpty()) {
                appendLine()
                append("Actions: ")
                append(result.detail.actionItems.joinToString("; "))
            }
        }

    fun slackMarkdown(result: AnalysisResult): String =
        buildString {
            append("*[Sentinel]* ")
            append(result.severity)
            append(" incident")
            appendLine()
            append("*Category:* `")
            append(result.category)
            append('`')
            appendLine()
            append("*Summary:* ")
            append(result.summary)
            appendLine()
            append("*Confidence:* ")
            append(String.format("%.2f", result.confidence))
            if (result.detail.actionItems.isNotEmpty()) {
                appendLine()
                append("*Actions:*")
                result.detail.actionItems.forEach { actionItem ->
                    appendLine()
                    append("• ")
                    append(actionItem)
                }
            }
        }
}
