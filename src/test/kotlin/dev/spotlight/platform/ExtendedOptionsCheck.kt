package dev.spotlight.platform

import java.nio.file.Files


internal object ExtendedOptionsCheck {
    @JvmStatic
    fun main(args: Array<String>) {
        check(parseExtendedFov("-2147483648") == Int.MIN_VALUE)
        check(parseExtendedFov("2147483647") == Int.MAX_VALUE)
        for (text in listOf("0", "-1", "29", "111")) check(parseExtendedFov(text) == text.toInt())
        for (text in listOf("", "2147483648", "-2147483649", "1.0", "1e2", "NaN", "Infinity", "1 2")) {
            check(runCatching { parseExtendedFov(text) }.isFailure) { "Accepted FOV: $text" }
        }
        for (text in listOf("-2147483648", "2147483647", "-0.25", "0", "1.5", "1e2", "2147483646.75")) {
            check(parseExtendedGamma(text) == text.toDouble())
        }
        for (text in listOf("", "NaN", "Infinity", "-Infinity", "1e309", "1.2.3", "0x1.0p0", "2147483648", "-2147483649", "2147483647.00000000001", "-2147483648.00000000001")) {
            check(runCatching { parseExtendedGamma(text) }.isFailure) { "Accepted gamma: $text" }
        }
        val directory = Files.createTempDirectory("spotlight-extended-check")
        val path = directory.resolve("options.properties")
        try {
            val store = ExtendedOptionsStore(path)
            check(store.gamma == null && store.fov == null && !Files.exists(path))
            store.save(Int.MIN_VALUE.toDouble(), Int.MAX_VALUE)
            val loaded = ExtendedOptionsStore(path)
            check(loaded.gamma == Int.MIN_VALUE.toDouble() && loaded.fov == Int.MAX_VALUE)
            loaded.save(1.25, Int.MIN_VALUE)
            check(ExtendedOptionsStore(path).gamma == 1.25)
            check(ExtendedOptionsStore(path).fov == Int.MIN_VALUE)
            val saved = Files.readAllBytes(path)
            check(runCatching { loaded.save(Double.NaN, 0) }.isFailure)
            check(Files.readAllBytes(path).contentEquals(saved))
            loaded.save(null, null)
            check(ExtendedOptionsStore(path).gamma == null && ExtendedOptionsStore(path).fov == null)
            Files.writeString(path, "gamma=NaN\nfov=70\n")
            check(runCatching { ExtendedOptionsStore(path) }.isFailure)
            check(Files.readString(path) == "gamma=NaN\nfov=70\n")
            val blocked = directory.resolve("blocked")
            Files.writeString(blocked, "keep")
            val failedStore = ExtendedOptionsStore(blocked.resolve("options.properties"))
            check(runCatching { failedStore.save(2.0, 120) }.isFailure)
            check(failedStore.gamma == null && failedStore.fov == null)
            check(Files.readString(blocked) == "keep")
            println("Extended options checks passed: signed boundaries, decimals, invalid input, persistence, reset, write failure.")
        } finally {
            Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
        }
    }
}
