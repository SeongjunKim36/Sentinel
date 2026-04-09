package io.github.seongjunkim36.sentinel

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.modulith.Modulithic

@Modulithic(sharedModules = ["shared"], systemName = "Sentinel")
@SpringBootApplication
class SentinelApplication

fun main(args: Array<String>) {
    runApplication<SentinelApplication>(*args)
}
