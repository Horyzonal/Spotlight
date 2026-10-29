package dev.spotlight.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.util.Locale

class CommandSettings(private val path: Path) {
    private var aliasValues = linkedMapOf<String, String>()
    private var hotkeyValues = linkedMapOf<String, String>()
    val aliases: Map<String, String> get() = aliasValues.toMap()
    val hotkeys: Map<String, String> get() = hotkeyValues.toMap()

    init {
        CoreStorage.read(path, "spotlight.commands.1") { input ->
            repeat(CoreStorage.count(input, 1024)) {
                val id = input.readUTF().also { CoreStorage.text(it, "Command ID", 128) }
                val alias = normalizeAlias(input.readUTF())
                val chord = normalizeChord(input.readUTF())
                require(id !in aliasValues && id !in hotkeyValues) { "Duplicate command ID: $id" }
                require(alias.isNotEmpty() || chord.isNotEmpty()) { "Empty command binding" }
                if (alias.isNotEmpty()) {
                    require(alias !in aliasValues.values) { "Duplicate alias: $alias" }
                    aliasValues[id] = alias
                }
                if (chord.isNotEmpty()) {
                    require(chord !in hotkeyValues.values) { "Duplicate hotkey: $chord" }
                    hotkeyValues[id] = chord
                }
            }
        }
    }

    fun setAlias(commandId: String, alias: String) {
        CoreStorage.text(commandId, "Command ID", 128)
        val normalized = normalizeAlias(alias)
        require(aliasValues.none { it.key != commandId && it.value == normalized }) { "Alias already assigned: $normalized" }
        val next = LinkedHashMap(aliasValues)
        if (normalized.isEmpty()) next.remove(commandId) else next[commandId] = normalized
        save(next, hotkeyValues)
        aliasValues = next
    }

    fun setHotkey(commandId: String, chord: String) {
        CoreStorage.text(commandId, "Command ID", 128)
        val normalized = normalizeChord(chord)
        require(hotkeyValues.none { it.key != commandId && it.value == normalized }) { "Hotkey already assigned: $normalized" }
        val next = LinkedHashMap(hotkeyValues)
        if (normalized.isEmpty()) next.remove(commandId) else next[commandId] = normalized
        save(aliasValues, next)
        hotkeyValues = next
    }

    
    fun resolveAlias(text: String): String? = aliasValues.entries.firstOrNull {
        it.value == text.trim().lowercase(Locale.ROOT)
    }?.key

    private fun save(aliases: Map<String, String>, hotkeys: Map<String, String>) {
        val ids = (aliases.keys + hotkeys.keys).sorted()
        require(ids.size <= 1024) { "Too many command bindings (maximum 1024)" }
        CoreStorage.write(path, "spotlight.commands.1") { output ->
            output.writeInt(ids.size)
            for (id in ids) {
                output.writeUTF(id)
                output.writeUTF(aliases[id].orEmpty())
                output.writeUTF(hotkeys[id].orEmpty())
            }
        }
    }

    companion object {
        private fun normalizeAlias(text: String): String {
            require(text.length <= 64) { "Alias too long (maximum 64)" }
            val alias = text.trim().lowercase(Locale.ROOT)
            require(alias.isEmpty() || alias.matches(Regex("[a-z0-9_][a-z0-9_.-]*"))) { "Alias must be one ASCII command token" }
            return alias
        }

        
        fun normalizeChord(text: String): String {
            require(text.length <= 128) { "Hotkey too long (maximum 128)" }
            if (text.isBlank()) return ""
            val parts = text.uppercase(Locale.ROOT).split('+').map { part ->
                when (val token = part.trim()) {
                    "CONTROL" -> "CTRL"
                    "OPTION" -> "ALT"
                    "CMD", "COMMAND", "META", "WIN" -> "SUPER"
                    "ESC" -> "ESCAPE"
                    "RETURN" -> "ENTER"
                    else -> token
                }
            }
            require(parts.distinct().size == parts.size) { "Duplicate hotkey component" }
            val modifiers = listOf("CTRL", "ALT", "SHIFT", "SUPER")
            val keys = parts.filterNot { it in modifiers }
            require(keys.size == 1) { "Hotkey needs exactly one non-modifier key" }
            val key = keys.single()
            require(key.matches(Regex("[A-Z0-9]")) || key in namedKeys ||
                key.matches(Regex("F([1-9]|1[0-9]|2[0-5])"))) { "Unknown hotkey key: $key" }
            return (modifiers.filter { it in parts } + key).joinToString("+")
        }

        private val namedKeys = setOf("SPACE", "TAB", "ENTER", "ESCAPE", "BACKSPACE", "DELETE", "INSERT",
            "HOME", "END", "PAGEUP", "PAGEDOWN", "UP", "DOWN", "LEFT", "RIGHT", "MINUS", "EQUAL",
            "LEFT_BRACKET", "RIGHT_BRACKET", "BACKSLASH", "SEMICOLON", "APOSTROPHE", "GRAVE_ACCENT",
            "COMMA", "PERIOD", "SLASH", "CAPS_LOCK", "SCROLL_LOCK", "NUM_LOCK", "PRINT_SCREEN", "PAUSE",
            "KP_0", "KP_1", "KP_2", "KP_3", "KP_4", "KP_5", "KP_6", "KP_7", "KP_8", "KP_9",
            "KP_DECIMAL", "KP_DIVIDE", "KP_MULTIPLY", "KP_SUBTRACT", "KP_ADD", "KP_ENTER", "KP_EQUAL")
    }
}


internal object CoreStorage {
    private const val MAX_BYTES = 2 * 1024 * 1024

    fun text(value: String, label: String, limit: Int) {
        require(value.isNotBlank() && value.length <= limit && value.none { it.isISOControl() }) {
            "$label must contain 1..$limit characters without control characters"
        }
    }

    fun count(input: DataInputStream, limit: Int): Int = input.readInt().also {
        require(it in 0..limit) { "Invalid record count (maximum $limit)" }
    }

    fun read(path: Path, magic: String, read: (DataInputStream) -> Unit) {
        
        val bytes = try {
            Files.newInputStream(path).use { it.readNBytes(MAX_BYTES + 1) }
        } catch (_: java.nio.file.NoSuchFileException) { return }
        require(bytes.size <= MAX_BYTES) { "Store exceeds $MAX_BYTES bytes" }
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readUTF() == magic) { "Unknown store format" }
            read(input)
            require(input.read() == -1) { "Unexpected trailing store data" }
        }
    }

    fun write(path: Path, magic: String, write: (DataOutputStream) -> Unit) {
        val bytes = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { output -> output.writeUTF(magic); write(output) }
        }.toByteArray()
        require(bytes.size <= MAX_BYTES) { "Store exceeds $MAX_BYTES bytes" }
        val target = path.toAbsolutePath()
        Files.createDirectories(target.parent)
        val temporary = Files.createTempFile(target.parent, ".spotlight-", ".tmp")
        try {
            FileChannel.open(temporary, WRITE).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            
            Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temporary) }
    }
}
