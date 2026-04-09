package io.github.seongjunkim36.sentinel.delivery

import io.github.seongjunkim36.sentinel.shared.OutputPlugin
import org.springframework.stereotype.Component

@Component
class OutputPluginRegistry(
    plugins: List<OutputPlugin>,
) {
    private val pluginsByType = plugins.associateBy { it.type }

    fun find(type: String): OutputPlugin? = pluginsByType[type]
}
