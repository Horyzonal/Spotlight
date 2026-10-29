package dev.spotlight.core

import java.nio.file.Files

object SpotlightSettingsCheck {
    @JvmStatic
    fun main(args: Array<String>) {
        val directory = Files.createTempDirectory("spotlight-settings-check")
        try {
            val path = directory.resolve("spotlight/settings.properties")
            val settings = SpotlightSettings(path)
            check(!settings.replaceCommandKey && !Files.exists(path))
            settings.setReplaceCommandKey(true)
            check(settings.replaceCommandKey && SpotlightSettings(path).replaceCommandKey)
            settings.setReplaceCommandKey(false)
            check(!settings.replaceCommandKey && !SpotlightSettings(path).replaceCommandKey)
            Files.writeString(path, "other=preserved\nreplaceCommandKey=true\n")
            SpotlightSettings(path).setReplaceCommandKey(false)
            check(Files.readString(path).contains("other=preserved"))
            for (bad in listOf("TRUE", "yes", "1", "", "false garbage", "\\uZZZZ")) {
                val text = "replaceCommandKey=$bad\n"
                Files.writeString(path, text)
                check(runCatching { SpotlightSettings(path) }.isFailure)
                check(Files.readString(path) == text)
            }
            Files.writeString(path, "replaceCommandKey=true\n")
            val cached = SpotlightSettings(path)
            Files.delete(path)
            Files.createDirectory(path)
            Files.writeString(path.resolve("blocker"), "keep")
            repeat(10) { check(cached.replaceCommandKey) }
            check(runCatching { cached.setReplaceCommandKey(false) }.isFailure)
            check(cached.replaceCommandKey)
            check(Files.readString(path.resolve("blocker")) == "keep")
            Files.list(path.parent).use { files -> check(files.noneMatch { it.fileName.toString().endsWith(".tmp") }) }
            println("Spotlight settings checks passed")
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
