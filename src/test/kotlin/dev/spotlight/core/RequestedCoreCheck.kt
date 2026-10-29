package dev.spotlight.core

import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator

object RequestedCoreCheck {
    @JvmStatic
    fun main(args: Array<String>) {
        check(Calculator.evaluate("2 + 3 * (4 - 1)")?.value == 11.0)
        check(Calculator.evaluate("-2^2")?.value == -4.0)
        check(Calculator.evaluate("2^3^2")?.value == 512.0)
        check(Calculator.evaluate("1e2 / 4 + 5 % 2")?.value == 26.0)
        check(Calculator.evaluate("130")?.stacks == "2 stacks + 2")
        check(Calculator.evaluate("-65.5")?.stacks == "-1 stacks + -1.5")
        check(Calculator.evaluate("0.5")?.display == "0.5")
        for (invalid in listOf("", "hello", "1/0", "0%0", "2(3)", "NaN", "1e16", "9^999",
            "1 +", "(".repeat(65) + "1" + ")".repeat(65), "1".repeat(513))) {
            check(Calculator.evaluate(invalid) == null) { "Accepted calculator input: $invalid" }
        }
        val directory = Files.createTempDirectory("spotlight-core-check-")
        try {
            settings(directory)
            locations(directory)
            sync()
        } finally {
            Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) } }
        }
        println("RequestedCoreCheck passed: calculator, settings, locations, reconciliation")
    }

    private fun rejects(block: () -> Unit) {
        check(runCatching(block).isFailure) { "Expected validation or IO failure" }
    }

    private fun settings(directory: Path) {
        val path = directory.resolve("commands.bin")
        val settings = CommandSettings(path)
        settings.setAlias("give", " G ")
        settings.setHotkey("give", "shift+control+g")
        check(settings.aliases == mapOf("give" to "g"))
        check(settings.hotkeys == mapOf("give" to "CTRL+SHIFT+G"))
        check(settings.resolveAlias("G") == "give")
        check(settings.resolveAlias("g stone") == null)
        val bytes = Files.readAllBytes(path)
        rejects { settings.setAlias("other", "g") }
        rejects { settings.setHotkey("other", "G+SHIFT+CTRL") }
        rejects { settings.setAlias("bad\nid", "b") }
        rejects { settings.setAlias("other", "two words") }
        for (chord in listOf("CTRL", "CTRL+CTRL+G", "CTRL++G", "G+H", "F26", "FAKE")) {
            rejects { CommandSettings.normalizeChord(chord) }
        }
        check(Files.readAllBytes(path).contentEquals(bytes))
        val loaded = CommandSettings(path)
        check(loaded.aliases == settings.aliases && loaded.hotkeys == settings.hotkeys)
        loaded.setAlias("give", "")
        loaded.setHotkey("give", "")
        check(CommandSettings(path).aliases.isEmpty() && CommandSettings(path).hotkeys.isEmpty())
        val blocked = directory.resolve("blocked")
        Files.createDirectory(blocked)
        Files.writeString(blocked.resolve("keep"), "keep")
        val target = directory.resolve("failure.bin")
        val failed = CommandSettings(target)
        Files.createDirectory(target)
        Files.writeString(target.resolve("keep"), "keep")
        rejects { failed.setAlias("give", "g") }
        check(failed.aliases.isEmpty())
        check(Files.readString(target.resolve("keep")) == "keep")
        Files.write(path, byteArrayOf(0, 1, 2))
        rejects { CommandSettings(path) }
    }

    private fun locations(directory: Path) {
        val path = directory.resolve("locations.bin")
        val store = LocationStore(path)
        val home = SavedLocation("home", "Home", 1.5, 64.0, -3.0, "minecraft:overworld", "server:a")
        store.put(home)
        store.put(home.copy(scope = "server:b"))
        store.put(home.copy(name = "Updated", dimension = "minecraft:the_nether"))
        check(store.list("server:a").single().name == "Updated")
        check(store.list("server:b").single() == home.copy(scope = "server:b"))
        val bytes = Files.readAllBytes(path)
        rejects { store.put(home.copy(x = Double.NaN)) }
        rejects { store.put(home.copy(z = Double.POSITIVE_INFINITY)) }
        rejects { store.put(home.copy(id = "bad\nid")) }
        rejects { store.put(home.copy(name = "x".repeat(129))) }
        rejects { store.put(home.copy(scope = "")) }
        check(Files.readAllBytes(path).contentEquals(bytes))
        val loaded = LocationStore(path)
        check(loaded.list("server:a") == store.list("server:a"))
        loaded.remove("server:a", "home")
        check(LocationStore(path).list("server:a").isEmpty())
        check(LocationStore(path).list("server:b").size == 1)
        Files.write(path, bytes.copyOf(bytes.size - 1))
        rejects { LocationStore(path) }
    }

    private fun sync() {
        val home = SavedLocation("home", "Home", 0.0, 64.0, 0.0, "minecraft:overworld", "world:a")
        val changed = home.copy(x = 10.0)
        fun merge(base: List<SavedLocation>, local: List<SavedLocation>, remote: List<SavedLocation>) =
            LocationSync.reconcile(home.scope, base, local, remote)
        check(merge(emptyList(), listOf(home), emptyList()).merged == listOf(home))
        check(merge(listOf(home), listOf(home), listOf(changed)).merged == listOf(changed))
        check(merge(listOf(home), emptyList(), listOf(home)).merged!!.isEmpty())
        check(merge(listOf(home), listOf(changed), listOf(changed)).merged == listOf(changed))
        val conflict = merge(listOf(home), emptyList(), listOf(changed))
        check(conflict.merged == null && conflict.conflicts.single().id == "home")
        check(merge(emptyList(), listOf(home), listOf(changed)).merged == null)
        rejects { merge(emptyList(), listOf(home, home), emptyList()) }
        rejects { merge(emptyList(), listOf(home.copy(scope = "world:b")), emptyList()) }
    }
}
