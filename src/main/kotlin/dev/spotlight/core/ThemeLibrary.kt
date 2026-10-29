package dev.spotlight.core

import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.UUID
import java.security.MessageDigest

data class ThemeEntry(val id: String, val name: String, val builtin: Boolean, val config: ThemeConfig)


class ThemeLibrary(configDir: Path) {
    private val directory = configDir.toAbsolutePath().normalize()
    private val library = directory.resolve("spotlight").resolve("themes")
    private val legacy = directory.resolve("spotlight-themes")
    private val active = directory.resolve("spotlight-theme.properties")
    private val backup = directory.resolve("spotlight-theme.properties.bak")
    private val builtins = listOf(ThemeEntry("default", "Spotlight Default", true, Theme.defaults)) + CatppuccinThemes.entries

    @Synchronized
    fun list(): List<ThemeEntry> {
        migrateLegacy()
        return listOf(load("current")) + builtins + customPaths().map { readEntry(it) }.sortedBy { it.name }
    }

    @Synchronized
    fun load(id: String): ThemeEntry {
        checkDirectory(directory)
        if (id == "current") {
            val config = if (Files.exists(active, NOFOLLOW_LINKS)) parse(read(active)) else Theme.defaults
            return ThemeEntry("current", "Current", false, config)
        }
        builtins.find { it.id == id }?.let { return it }
        val target = path(id)
        migrateLegacy()
        checkLibraryDirectories()
        return readEntry(target)
    }

    
    @Synchronized
    fun save(id: String?, name: String, config: ThemeConfig): ThemeEntry {
        val displayName = name.trim()
        require(displayName.isNotEmpty() && displayName.length <= 64 && displayName.none { it.isISOControl() || it == '\u2028' || it == '\u2029' }) {
            "Theme name must contain 1..64 characters without line breaks or control characters"
        }
        val text = "# name: $displayName\n" + serialize(config)
        require(text.toByteArray(Charsets.UTF_8).size < MAX_BYTES) { "Theme exceeds 128 KiB" }
        val create = id == null || id == "current" || builtins.any { it.id == id }
        val entryId = if (create) UUID.randomUUID().toString() else requireNotNull(id)
        val target = path(entryId)
        migrateLegacy()
        checkLibraryDirectories()
        Files.createDirectories(library)
        if (create) require(customPaths().size < MAX_THEMES) { "Theme library is limited to $MAX_THEMES custom themes" }
        else readEntry(target)
        publish(target, text.toByteArray(Charsets.UTF_8), create)
        return ThemeEntry(entryId, displayName, false, parse(text))
    }

    
    @Synchronized
    fun importTheme(name: String, text: String): ThemeEntry {
        require(text.toByteArray(Charsets.UTF_8).size < MAX_BYTES) { "Theme exceeds 128 KiB" }
        return save(null, name, parse(text))
    }

    
    @Synchronized
    fun importFile(path: Path, name: String? = null): ThemeEntry {
        val source = path.toAbsolutePath().normalize()
        require(source.fileName.toString().endsWith(".properties")) { "Theme file must end in .properties" }
        var parent = source.parent
        while (parent != null) {
            checkDirectory(parent)
            parent = parent.parent
        }
        val text = read(source)
        return importTheme(name ?: readName(text), text)
    }

    
    @Synchronized
    fun apply(id: String): ThemeReport = try {
        val entry = load(id)
        val bytes = serialize(entry.config).toByteArray(Charsets.UTF_8)
        Files.createDirectories(directory)
        if (Files.exists(active, NOFOLLOW_LINKS)) {
            val previous = readBytes(active)
            if (Files.exists(backup, NOFOLLOW_LINKS)) readBytes(backup)
            else publish(backup, previous, true)
        }
        publish(active, bytes, false)
        ThemeReport(entry.config)
    } catch (failure: Exception) {
        ThemeReport(Theme.defaults, listOf("Cannot apply theme '$id': ${failure.message ?: failure.javaClass.simpleName}"))
    }

    private fun path(id: String): Path {
        require(UUID_PATTERN.matches(id)) { "Invalid custom theme ID: $id" }
        return library.resolve("$id.properties")
    }

    private fun customPaths(): List<Path> {
        checkLibraryDirectories()
        if (!Files.exists(library, NOFOLLOW_LINKS)) return emptyList()
        return Files.newDirectoryStream(library).use { stream ->
            val paths = mutableListOf<Path>()
            for (file in stream) {
                if (!file.fileName.toString().endsWith(".properties")) continue
                path(file.fileName.toString().removeSuffix(".properties"))
                require(paths.size < MAX_THEMES) { "Theme library exceeds $MAX_THEMES custom themes" }
                require(Files.isRegularFile(file, NOFOLLOW_LINKS)) { "Theme must be a regular file: $file" }
                paths.add(file)
            }
            paths
        }
    }

    private fun readEntry(file: Path): ThemeEntry {
        val text = read(file)
        return ThemeEntry(file.fileName.toString().removeSuffix(".properties"), readName(text), false, parse(text))
    }

    private fun readName(text: String): String {
        val name = text.lineSequence().firstOrNull()?.takeIf { it.startsWith("# name: ") }?.removePrefix("# name: ")
            ?: error("Missing theme name")
        require(name.isNotBlank() && name.length <= 64 && name.none { it.isISOControl() || it == '\u2028' || it == '\u2029' }) { "Invalid theme name" }
        return name
    }

    private fun checkLibraryDirectories() {
        checkDirectory(directory)
        checkDirectory(library.parent)
        checkDirectory(library)
    }

    private fun migrateLegacy() {
        checkLibraryDirectories()
        checkDirectory(legacy)
        if (!Files.exists(legacy, NOFOLLOW_LINKS)) return
        Files.newDirectoryStream(legacy, "*.properties").use { files ->
            for (file in files) {
                val id = file.fileName.toString().removeSuffix(".properties")
                var target = path(id)
                val bytes = readBytes(file)
                val text = Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
                val name = readName(text)
                parse(text)
                var migrated = bytes
                if (Files.exists(target, NOFOLLOW_LINKS)) {
                    if (readBytes(target).contentEquals(bytes)) continue
                    
                    val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
                    val collisionId = UUID.nameUUIDFromBytes(file.fileName.toString().toByteArray(Charsets.UTF_8) + hash).toString()
                    target = path(collisionId)
                    val legacyName = name.take(64 - " (legacy)".length) + " (legacy)"
                    migrated = ("# name: $legacyName\n" + text.substringAfter('\n', "")).toByteArray(Charsets.UTF_8)
                    require(migrated.size < MAX_BYTES) { "Migrated theme exceeds 128 KiB: $file" }
                    if (Files.exists(target, NOFOLLOW_LINKS)) {
                        require(readBytes(target).contentEquals(migrated)) { "Legacy migration collision: $target" }
                        continue
                    }
                }
                require(customPaths().size < MAX_THEMES) { "Theme library is limited to $MAX_THEMES custom themes" }
                Files.createDirectories(library)
                publish(target, migrated, true)
            }
        }
    }

    private fun checkDirectory(path: Path) {
        require(!Files.exists(path, NOFOLLOW_LINKS) || Files.isDirectory(path, NOFOLLOW_LINKS)) { "Not a real directory: $path" }
    }

    private fun readBytes(path: Path): ByteArray {
        require(Files.isRegularFile(path, NOFOLLOW_LINKS)) { "Theme must be a regular file: $path" }
        val bytes = Files.newInputStream(path, NOFOLLOW_LINKS).use { it.readNBytes(MAX_BYTES) }
        require(bytes.size < MAX_BYTES) { "Theme exceeds 128 KiB: $path" }
        return bytes
    }

    private fun read(path: Path): String =
        Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(readBytes(path))).toString()

    private fun publish(target: Path, bytes: ByteArray, create: Boolean) {
        checkDirectory(target.parent)
        require(!Files.exists(target, NOFOLLOW_LINKS) || Files.isRegularFile(target, NOFOLLOW_LINKS)) { "Theme must be a regular file: $target" }
        val temporary = Files.createTempFile(target.parent, ".spotlight-theme-", ".tmp")
        try {
            Files.write(temporary, bytes)
            if (create) Files.createLink(target, temporary)
            else {
                require(!Files.exists(target, NOFOLLOW_LINKS) || Files.isRegularFile(target, NOFOLLOW_LINKS)) { "Theme must be a regular file: $target" }
                
                Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    companion object {
        private const val MAX_BYTES = 128 * 1024
        private const val MAX_THEMES = 128
        private val UUID_PATTERN = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

        private fun parse(text: String): ThemeConfig {
            val report = Theme.parse(text)
            require(report.warnings.isEmpty()) { report.warnings.joinToString("; ") }
            return report.config
        }

        @JvmStatic
        fun serialize(config: ThemeConfig): String {
            require(config.colors.keys.all { key -> ThemeColor.entries.any { it.defaultArgb == key } }) { "Unknown theme color" }
            require(config.metrics.keys.all { key -> ThemeMetric.entries.any { it.key == key } }) { "Unknown theme metric" }
            require(config.font == null || config.font.matches(Regex("[a-z0-9_.-]+:[a-z0-9/._-]+"))) { "Invalid theme font" }
            val text = buildString {
                appendLine("font=${config.font.orEmpty()}")
                ThemeColor.entries.forEach { appendLine("colors.${it.key}=#${config.color(it.defaultArgb).toUInt().toString(16).uppercase().padStart(8, '0')}") }
                ThemeMetric.entries.forEach { appendLine("layout.${it.key}=${config.metrics[it.key] ?: it.default}") }
            }
            parse(text)
            return text
        }
    }
}
