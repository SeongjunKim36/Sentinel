package io.github.seongjunkim36.sentinel

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

class SentinelModulithTests {
    @Test
    fun verifiesModuleStructure() {
        ApplicationModules.of(SentinelApplication::class.java).verify()
    }
}
