package dev.spotlight.core

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.Properties

class MusicPreferences(private val path: Path) {
    var endless: Boolean = false
        private set
    var error: String? = null
        private set

    init {
        runCatching {
            val value = read().getProperty("endless", "false")
            endless = requireNotNull(value.toBooleanStrictOrNull()) { "Invalid endless value: $value" }
        }.onFailure { error = "Music preferences could not load: ${it.message}" }
    }

    fun setEndless(value: Boolean): Boolean = runCatching {
        val properties = read().apply { setProperty("endless", value.toString()) }
        val parent = path.toAbsolutePath().parent
        Files.createDirectories(parent)
        val temporary = Files.createTempFile(parent, "music-", ".properties.tmp")
        try {
            Files.newOutputStream(temporary).use { properties.store(it, "Spotlight music") }
            Files.move(temporary, path.toAbsolutePath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            runCatching { Files.deleteIfExists(temporary) }
        }
        endless = value
        error = null
        true
    }.getOrElse {
        error = "Music preferences could not save: ${it.message}"
        false
    }

    private fun read(): Properties = Properties().apply {
        if (Files.notExists(path)) return@apply
        Files.newInputStream(path).use { load(it) }
    }
}
