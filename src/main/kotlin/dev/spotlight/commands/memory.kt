package dev.spotlight.commands

import dev.spotlight.platform.reportDebug

internal fun debugMemory(bytes: Long): String = "${debugNumber(bytes / 1_048_576.0)} MiB"

internal fun registerMemory() {
    registerCommand("memory", "Memory usage", "F3 Java heap used, allocated and max memory; CPU processors and runtime") {
        val runtime = Runtime.getRuntime()
        val allocated = runtime.totalMemory()
        val used = allocated - runtime.freeMemory()
        reportDebug("Memory usage", "heap used=${debugMemory(used)}; allocated=${debugMemory(allocated)}; max=${debugMemory(runtime.maxMemory())}; available processors=${runtime.availableProcessors()}; Java=${Runtime.version()}")
    }
}
