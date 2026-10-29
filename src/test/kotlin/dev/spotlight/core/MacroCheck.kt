package dev.spotlight.core

import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator

object MacroCheck {
    @JvmStatic
    fun main(args: Array<String>) {
        templates()
        val directory = Files.createTempDirectory("spotlight-macro-check-")
        try {
            persistence(directory)
        } finally {
            Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) } }
        }
        println("MacroCheck passed: templates, validation, persistence, atomic rollback")
    }

    private fun rejects(block: () -> Unit) {
        check(runCatching(block).isFailure) { "Expected validation or IO failure" }
    }

    private fun templates() {
        val teleport = "/teleport @e[type=dog] {Argument=location}"
        check(MacroTemplates.arguments(teleport) == listOf(MacroArgument("location", MacroArgumentType.LOCATION)))
        check(MacroTemplates.preview(teleport, emptyMap()) == teleport)
        check(MacroTemplates.preview(teleport, mapOf("location" to "12 6")) == "/teleport @e[type=dog] 12 6")
        check(MacroTemplates.preview(teleport, mapOf("location" to "")) == teleport)
        check(MacroTemplates.preview("/say {Argument=x} {Argument=x} {Player}", mapOf("x" to "hi"), mapOf("Player" to "Alex")) == "/say hi hi Alex")
        check(MacroTemplates.preview("/say {Clipboard}", emptyMap(), mapOf("Clipboard" to "{Player}")) == "/say {Player}")
        check(MacroTemplates.preview("/give @s stone[custom_data={foo:1}]", emptyMap()) == "/give @s stone[custom_data={foo:1}]")
        check(MacroTemplates.expand(teleport, mapOf("location" to "1.5 64 -3"), emptyMap()) ==
            "/teleport @e[type=dog] 1.5 64 -3")
        for (type in MacroArgumentType.entries) {
            check(MacroTemplates.arguments("/say {Argument=target:${type.name.lowercase()}}") ==
                listOf(MacroArgument("target", type)))
        }
        for (name in listOf("player", "item", "block", "entity", "number", "message")) {
            val expected = MacroArgumentType.entries.firstOrNull { it.name.equals(name, true) } ?: MacroArgumentType.TEXT
            check(MacroTemplates.arguments("/say {Argument=$name}").single().type == expected)
        }
        check(MacroTemplates.arguments("/say {Argument=player} {Argument=player:PLAYER}").size == 1)
        check(MacroTemplates.expand("/say {Argument=x} {Argument=x}", mapOf("x" to "hello"), emptyMap()) == "/say hello hello")
        val targetUuid = "123e4567-e89b-12d3-a456-426614174000"
        check("{LookEntity}" in MacroTemplates.placeholderOptions())
        check(MacroTemplates.preview("/kill {LookEntity}", emptyMap(), mapOf("LookEntity" to targetUuid)) == "/kill $targetUuid")
        check(MacroTemplates.expand("/tp {LookEntity} ~ ~1 ~", emptyMap(), mapOf("LookEntity" to targetUuid)) == "/tp $targetUuid ~ ~1 ~")
        rejects { MacroTemplates.expand("/kill {LookEntity}", emptyMap(), emptyMap()) }
        val variables = listOf("Clipboard", "Look", "LookEntity", "Position", "Player", "Dimension").associateWith { "value-$it" }
        check(MacroTemplates.expand("/say " + variables.keys.joinToString(" ") { "{$it}" }, emptyMap(), variables) ==
            "/say " + variables.values.joinToString(" "))
        for (literal in listOf(
            "/give @s stone[custom_data={foo:1}]",
            "/give @s stone[custom_data={outer:{foo:1},text:\"{Foo}\",empty:{}}]",
            "/tellraw @s {\"text\":\"hello\",\"extra\":[{\"text\":\"world\"}]}",
        )) {
            check(MacroTemplates.arguments(literal).isEmpty())
            check(MacroTemplates.expand(literal, emptyMap(), emptyMap()) == literal)
        }
        check(MacroTemplates.expand("/say {Clipboard}", emptyMap(), mapOf("Clipboard" to "{Argument=missing}")) ==
            "/say {Argument=missing}")
        check(MacroTemplates.expand("/say {Argument=x}", mapOf("x" to "{Player}"), emptyMap()) == "/say {Player}")
        for (coordinates in listOf("0 64 -1.5", "~ ~1 ~-2.5", "^ ^1 ^-2", "~ 1 2", "1e2 2 3")) {
            check(MacroTemplates.expand(teleport, mapOf("location" to coordinates), emptyMap()).endsWith(coordinates))
        }
        for (coordinates in listOf("1 2", "1 2 3 4", "NaN 2 3", "1e999 2 3", "^ ~ 1", "~^1 ~ ~", "a b c")) {
            rejects { MacroTemplates.expand(teleport, mapOf("location" to coordinates), emptyMap()) }
        }
        for (value in listOf("1", "-1.5", ".5", "1e3")) {
            check(MacroTemplates.expand("/say {Argument=number}", mapOf("number" to value), emptyMap()) == "/say $value")
        }
        for (value in listOf("NaN", "Infinity", "1e999", "0x1p2", "1f", "1 2")) {
            rejects { MacroTemplates.expand("/say {Argument=number}", mapOf("number" to value), emptyMap()) }
        }
        for (invalid in listOf("", "say hi", "/", "//say hi", "/ say hi", "/say\nhi", "/say\rhi", "/say\u0000hi",
            "/say {Foo}", "/say {Unknown=x}", "/say {Argument=}", "/say {Argument=x:unknown}",
            "/say {Argument=x:TEXT:NUMBER}", "/say {Argument=x", "/say {Argument=x} {Argument=x:NUMBER}",
            "/say " + "x".repeat(32768))) {
            rejects { MacroTemplates.arguments(invalid) }
        }
        for (value in listOf("", " ", "x\r/kill @s", "x\n/kill @s", "x\t", "\u007f")) {
            rejects { MacroTemplates.expand("/say {Argument=x}", mapOf("x" to value), emptyMap()) }
            rejects { MacroTemplates.expand("/say {Clipboard}", emptyMap(), mapOf("Clipboard" to value)) }
        }
        rejects { MacroTemplates.expand(teleport, emptyMap(), emptyMap()) }
        rejects { MacroTemplates.expand("/say {Clipboard}", emptyMap(), emptyMap()) }
        check(MacroTemplates.expand("/say {Argument=x}", mapOf("x" to "x".repeat(32763)), emptyMap()).length == 32768)
        rejects { MacroTemplates.expand("/say {Argument=x}", mapOf("x" to "x".repeat(32764)), emptyMap()) }
    }

    private fun persistence(directory: Path) {
        val path = directory.resolve("macros.bin")
        val store = MacroStore(path)
        check(store.list().isEmpty())
        val macro = Macro("550e8400-e29b-41d4-a716-446655440000", "Teleport dogs", "/teleport @e[type=dog] {Argument=location}")
        val unicode = Macro("家", "家", "/say " + "家".repeat(32763))
        store.put(macro)
        store.put(unicode)
        store.put(macro.copy(name = "Updated"))
        check(MacroStore(path).list() == store.list())
        check(store.list().first().name == "Updated")
        val bytes = Files.readAllBytes(path)
        for (invalid in listOf(macro.copy(id = "../bad"), macro.copy(id = ".."), macro.copy(id = "bad\\path"),
            macro.copy(id = "bad\nID"), macro.copy(id = ""), macro.copy(name = "x".repeat(129)),
            macro.copy(template = "/say {Argument=x:invalid}"))) {
            rejects { store.put(invalid) }
        }
        check(Files.readAllBytes(path).contentEquals(bytes))
        store.remove("missing")
        check(Files.readAllBytes(path).contentEquals(bytes))
        store.remove(macro.id)
        check(MacroStore(path).list() == listOf(unicode))
        store.remove(unicode.id)
        check(MacroStore(path).list().isEmpty())


        store.put(macro)
        Files.delete(path)
        Files.createDirectory(path)
        Files.writeString(path.resolve("keep"), "keep")
        rejects { store.put(macro.copy(name = "Failed")) }
        rejects { store.remove(macro.id) }
        check(store.list() == listOf(macro))
        check(Files.readString(path.resolve("keep")) == "keep")
        Files.delete(path.resolve("keep"))
        Files.delete(path)
        Files.write(path, bytes.copyOf(bytes.size - 1))
        rejects { MacroStore(path) }
        Files.write(path, bytes + byteArrayOf(1))
        rejects { MacroStore(path) }
        Files.newOutputStream(path).use { stream ->
            DataOutputStream(stream).use { output ->
                output.writeUTF("spotlight.macros.1")
                output.writeInt(1025)
            }
        }
        rejects { MacroStore(path) }
        Files.list(directory).use { paths -> check(paths.noneMatch { it.fileName.toString().endsWith(".tmp") }) }
    }
}
