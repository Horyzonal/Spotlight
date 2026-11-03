package dev.spotlight.platform

import net.minecraft.nbt.CompoundTag
import java.nio.file.Files

object HeadDatabaseCheck {
    @JvmStatic fun main(args: Array<String>) {
        net.minecraft.SharedConstants.tryDetectVersion()
        net.minecraft.server.Bootstrap.bootStrap()
        val raw = CompoundTag().apply {
            putString("id", "minecraft:player_head")
            putByte("Count", 1)
            put("tag", CompoundTag().apply {
                putString("missing_mod:component", "opaque retained")
                put("SkullOwner", CompoundTag().apply { putString("name", "Player_1") })
            })
        }
        val head = SavedHead("Garden apple", listOf("food", "red"), raw)
        check(HeadFile.parse(HeadFile.json(listOf(head))).single() == head)
        check(runCatching { HeadFile.json(listOf(head, head.copy(name = "GARDEN APPLE"))) }.isFailure)
        check(runCatching { HeadFile.validate(head.copy(name = "")) }.isFailure)
        check(runCatching { HeadFile.validate(head.copy(tags = List(17) { "tag" })) }.isFailure)
        check(runCatching { HeadFile.validate(head.copy(raw = raw.copy().apply { putByte("Count", 64) })) }.isFailure)
        check(runCatching { HeadFile.validate(head.copy(raw = raw.copy().apply { putString("id", "minecraft:stone") })) }.isFailure)
        check(runCatching { HeadFile.parse(" ".repeat(HeadFile.MAX_BYTES + 1)) }.isFailure)
        check(runCatching { HeadFile.json(List(513) { head.copy(name = "Head $it") }) }.isFailure)
        listOf("Player_1", "a", "1234567890123456", "01234567-89ab-cdef-0123-456789abcdef").forEach { check(HeadDatabase.validPlayer(it)) }
        listOf("", "longer_than_16_chars", "a b", "../file", "https://example.org", "a\n", "玩家", "1-1-1-1-1").forEach { check(!HeadDatabase.validPlayer(it)) }
        val entries = listOf(
            HeadDatabase.Head("saved:apple", "Garden apple", "Saved", listOf("food", "red"), raw),
            HeadDatabase.Head("online:1", "Player_1", "Online", listOf("player"), raw),
        )
        check(HeadDatabase.search("grdn red", entries).single().title == "Garden apple")
        check(HeadDatabase.search("online player_1", entries).single().source == "Online")
        val searchEntries = entries + listOf("Apple", "Pineapple", "aplp e", "Grüner Apfel", "abc", "acb", " ", "Red apple", "apple pie").mapIndexed { i, title ->
            HeadDatabase.Head("test:$i", title, "Test", listOf("food", "garden", "red"), raw)
        }
        val fuzzyEntries = searchEntries.map { dev.spotlight.core.Entry(it.id, it.title, "${it.source} ${it.tags.joinToString(" ")} ${it.raw.getString("id")}") }
        listOf("", "  ", "apple", "APPLE", "aple", "appel", "grdn red", "food", "online player_1", "minecraft:player_head", "acb", "不存在", "grüner", "apple pie", "test:3").forEach { query ->
            val expected = dev.spotlight.core.Fuzzy.search(query, fuzzyEntries, limit = searchEntries.size).map { it.id }
            check(HeadDatabase.search(query, searchEntries).map { it.id } == expected) { "Search ranking changed: $query" }
        }
        val large = List(150_001) { i -> HeadDatabase.Head("bulk:$i", "Garden apple $i", "Test", listOf("red", "food"), raw) }
        check(HeadDatabase.search(" ", large) === large)
        val start = System.nanoTime()
        check(HeadDatabase.search("grdn red", large).size == large.size)
        val indexed = large.first().fields
        check(HeadDatabase.search("apple", large).size == large.size)
        check(large.first().fields === indexed)
        var cancellationChecks = 0
        check(HeadDatabase.search("apple", large) { ++cancellationChecks >= 2 }.isEmpty())
        check(cancellationChecks == 2)
        println("Head search: 150001 results retained, two indexed queries ${(System.nanoTime() - start) / 1_000_000} ms; cancellation and Fuzzy ranking verified")
        val remote = RemoteHead(java.util.UUID.randomUUID(), "Lazy preview", "texture", listOf("food"))
        val lightweight = HeadDatabase.Head(remote)
        check(HeadDatabase.search("lazy food", listOf(lightweight)).single() === lightweight)
        val directory = Files.createTempDirectory("spotlight-heads-check")
        try {
            val file = HeadFile(directory)
            check(file.load().isEmpty())
            file.add(head)
            check(file.load().single() == head)
            val original = Files.readAllBytes(directory.resolve("heads.json"))
            check(runCatching { file.add(head) }.isFailure)
            check(Files.readAllBytes(directory.resolve("heads.json")).contentEquals(original))
            file.exportNbt("head.nbt", raw)
            check(file.importNbt("head.nbt") == raw)
            check(runCatching { file.exportNbt("head.nbt", CompoundTag()) }.isFailure)
            check(runCatching { file.importNbt("../head.nbt") }.isFailure)
            Files.createSymbolicLink(directory.resolve("link.nbt"), directory.resolve("head.nbt"))
            check(runCatching { file.importNbt("link.nbt") }.isFailure)
            Files.writeString(directory.resolve("heads.json"), "broken")
            check(runCatching { file.add(head) }.isFailure)
            check(Files.readString(directory.resolve("heads.json")) == "broken")
        } finally {
            Files.walk(directory).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
        println("HeadDatabaseCheck passed: format, bounds, opaque components, names, fuzzy search, no overwrite, paths; no network")
    }
}
