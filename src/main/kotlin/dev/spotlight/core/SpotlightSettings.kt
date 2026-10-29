package dev.spotlight.core

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.Properties

class SpotlightSettings(private val path: Path) {
    private val values = Properties()
    var replaceCommandKey: Boolean = false
        private set

    init {
        if (Files.exists(path)) {
            Files.newInputStream(path).use { values.load(it) }
            replaceCommandKey = values.getProperty("replaceCommandKey")?.let {
                requireNotNull(it.toBooleanStrictOrNull()) { "replaceCommandKey must be true or false" }
            } ?: false
        }
    }

    fun setReplaceCommandKey(enabled: Boolean) {
        val next = Properties().also { it.putAll(values); it.setProperty("replaceCommandKey", enabled.toString()) }
        val directory = path.toAbsolutePath().parent
        Files.createDirectories(directory)
        val temporary = Files.createTempFile(directory, "settings-", ".tmp")
        try {
            Files.newOutputStream(temporary).use { next.store(it, "Spotlight settings") }
            Files.move(temporary, path.toAbsolutePath(), ATOMIC_MOVE, REPLACE_EXISTING)
            values.putAll(next)
            replaceCommandKey = enabled
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
