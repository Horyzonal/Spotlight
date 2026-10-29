package dev.spotlight.core

import java.nio.file.Path


data class Macro(val id: String, val name: String, val template: String)
data class MacroArgument(val name: String, val type: MacroArgumentType)
enum class MacroArgumentType { TEXT, NUMBER, PLAYER, LOCATION, ITEM, BLOCK, ENTITY }

object MacroTemplates {
    private const val MAX_LENGTH = 32768
    private val identifier = Regex("[\\p{L}_][\\p{L}\\p{N}_-]{0,63}")
    private val number = Regex("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")
    private val builtins = setOf("Clipboard", "Look", "LookEntity", "Position", "Player", "Dimension")
    private data class Placeholder(val start: Int, val end: Int, val argument: MacroArgument?, val variable: String?)

    fun placeholderOptions(): List<String> = MacroArgumentType.entries.flatMap {
        val type = it.name.lowercase()
        val name = when (it) { MacroArgumentType.TEXT -> "message"; MacroArgumentType.PLAYER -> "target"; else -> type }
        listOf("{Argument=$type}", "{Argument=$name:$type}")
    } + builtins.map { "{$it}" }

    fun arguments(template: String): List<MacroArgument> = parse(template).mapNotNull { it.argument }.distinct()

    
    fun preview(template: String, arguments: Map<String, String>, variables: Map<String, String> = emptyMap()): String {
        val placeholders = parse(template)
        return buildString {
            var offset = 0
            for (placeholder in placeholders) {
                append(template, offset, placeholder.start)
                val value = placeholder.argument?.let { arguments[it.name] } ?: if (placeholder.argument == null) variables[placeholder.variable] else null
                append(value?.takeIf { it.isNotEmpty() } ?: template.substring(placeholder.start, placeholder.end))
                offset = placeholder.end
            }
            append(template, offset, template.length)
        }
    }

    fun expand(template: String, arguments: Map<String, String>, variables: Map<String, String>): String {
        val placeholders = parse(template)
        val result = StringBuilder()
        var offset = 0
        for (placeholder in placeholders) {
            result.append(template, offset, placeholder.start)
            val argument = placeholder.argument
            val name = argument?.name ?: placeholder.variable!!
            val value = (if (argument != null) arguments[name] else variables[name])
                ?: throw IllegalArgumentException("Missing macro value: $name")
            CoreStorage.text(value, "Macro value $name", MAX_LENGTH)
            if (argument?.type == MacroArgumentType.NUMBER) {
                require(finiteNumber(value)) { "Argument $name must be a finite number" }
            }
            if (argument?.type == MacroArgumentType.LOCATION) {
                val coordinates = value.trim().split(Regex(" +"))
                require(coordinates.size == 3 && coordinates.all { coordinate ->
                    val suffix = coordinate.drop(1)
                    if (coordinate.startsWith('~') || coordinate.startsWith('^')) {
                        suffix.isEmpty() || finiteNumber(suffix)
                    } else finiteNumber(coordinate)
                } && (coordinates.all { it.startsWith('^') } || coordinates.none { it.startsWith('^') })) {
                    "Argument $name must contain three finite coordinates; local coordinates cannot be mixed"
                }
            }
            require(result.length + value.length <= MAX_LENGTH) { "Expanded command exceeds $MAX_LENGTH characters" }
            result.append(value)
            offset = placeholder.end
        }
        result.append(template, offset, template.length)
        return result.toString().also { validateCommand(it) }
    }

    private fun finiteNumber(value: String): Boolean =
        number.matches(value) && value.toDoubleOrNull()?.isFinite() == true

    private fun validateCommand(template: String) {
        CoreStorage.text(template, "Macro command", MAX_LENGTH)
        require(template.startsWith('/') && template.length > 1 && !template[1].isWhitespace() && template[1] != '/') {
            "Macro must start with one slash followed by a command"
        }
    }

    private fun parse(template: String): List<Placeholder> {
        validateCommand(template)
        val result = mutableListOf<Placeholder>()
        val types = linkedMapOf<String, MacroArgumentType>()
        var index = 0
        var quote: Char? = null
        var escaped = false
        while (index < template.length) {
            val char = template[index]
            if (escaped) { escaped = false; index++; continue }
            if (quote != null && char == '\\') { escaped = true; index++; continue }
            if (char == quote) { quote = null; index++; continue }
            if (quote == null && (char == '"' || char == '\'')) { quote = char; index++; continue }
            if (char != '{') { index++; continue }
            var end = index + 1
            while (end < template.length && template[end] != '{' && template[end] != '}') end++
            val token = template.substring(index + 1, end)
            if (end == template.length || template[end] != '}') {
                require(!token.startsWith("Argument=")) { "Unclosed argument placeholder" }
                index++
                continue
            }
            val argument = if (token.startsWith("Argument=")) {
                val parts = token.removePrefix("Argument=").split(':')
                require(parts.size in 1..2 && identifier.matches(parts[0])) { "Invalid argument placeholder: {$token}" }
                val type = if (parts.size == 2) {
                    MacroArgumentType.entries.firstOrNull { it.name.equals(parts[1], ignoreCase = true) }
                        ?: throw IllegalArgumentException("Unknown macro argument type: ${parts[1]}")
                } else {
                    MacroArgumentType.entries.firstOrNull { it.name.equals(parts[0], ignoreCase = true) }
                        ?: MacroArgumentType.TEXT
                }
                require(types.put(parts[0], type).let { it == null || it == type }) {
                    "Conflicting types for argument: ${parts[0]}"
                }
                MacroArgument(parts[0], type)
            } else null
            val variable = token.takeIf { it in builtins }
            if (argument != null || variable != null) {
                result += Placeholder(index, end + 1, argument, variable)
                index = end + 1
            } else {
                
                require(quote != null || (!identifier.matches(token) &&
                    !identifier.matches(token.substringBefore('=')))) { "Unknown macro placeholder: {$token}" }
                index++
            }
        }
        return result
    }
}

class MacroStore(private val path: Path) {
    private var macros = linkedMapOf<String, Macro>()

    init {
        CoreStorage.read(path, "spotlight.macros.1") { input ->
            repeat(CoreStorage.count(input, 1024)) {
                val id = input.readUTF()
                val name = input.readUTF()
                val template = buildString {
                    repeat(CoreStorage.count(input, 32768)) { append(input.readChar()) }
                }
                val macro = Macro(id, name, template)
                validate(macro)
                require(id !in macros) { "Duplicate macro ID: $id" }
                macros[id] = macro
            }
        }
    }

    fun list(): List<Macro> = macros.values.sortedWith(compareBy({ it.name }, { it.id }))

    fun put(macro: Macro) {
        validate(macro)
        val next = LinkedHashMap(macros)
        next[macro.id] = macro
        save(next)
        macros = next
    }

    fun remove(id: String) {
        validateId(id)
        if (id !in macros) return
        val next = LinkedHashMap(macros)
        next.remove(id)
        save(next)
        macros = next
    }

    private fun save(next: Map<String, Macro>) {
        require(next.size <= 1024) { "Too many macros (maximum 1024)" }
        CoreStorage.write(path, "spotlight.macros.1") { output ->
            output.writeInt(next.size)
            for (macro in next.values) {
                output.writeUTF(macro.id)
                output.writeUTF(macro.name)
                output.writeInt(macro.template.length)
                output.writeChars(macro.template)
            }
        }
    }

    private fun validate(macro: Macro) {
        validateId(macro.id)
        CoreStorage.text(macro.name, "Macro name", 128)
        MacroTemplates.arguments(macro.template)
    }

    private fun validateId(id: String) {
        CoreStorage.text(id, "Macro ID", 128)
        require(id != "." && id != ".." && '/' !in id && '\\' !in id) { "Macro ID must not be a path" }
    }
}
