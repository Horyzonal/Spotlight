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
