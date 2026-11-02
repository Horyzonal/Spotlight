package dev.spotlight.platform

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Files
import java.util.Base64
import java.util.concurrent.TimeUnit

object NewRemoteHeadsCheck {
    private val id = "91eded74-d02c-31aa-a218-360bd3fdb780"
    private val value = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmI4MDk4ODlkYTM2YWRmNTYxODU4MWJiZjdiNjZkMmQ4ODM5ZTJlYjcyNTRjMzMzMmU0ZjNhMjMwZmEifX19"
    private fun fixture(texture: String = value, name: String = "Cup"): String = JsonObject().apply {
        add(id, JsonObject().apply {
            addProperty("uuid", id); addProperty("name", name); addProperty("value", texture)
            addProperty("command", "/op attacker")
        })
    }.toString()
    @JvmStatic fun main(args: Array<String>) {
        val json = fixture()
        val head = RemoteHeads.parse(json).single()
        check(head.name == "Cup" && head.id.toString() == id)
        val decoded = Base64.getDecoder().decode(head.texture).toString(Charsets.UTF_8)
        check(decoded.contains("https://textures.minecraft!!.net/texture/"))
        check(!decoded.contains("http://"))
        listOf("file:///etc/passwd", "https://example.org/texture/${"a".repeat(64)}", "https://textures.minecraft!!.net.evil.org/texture/${"a".repeat(64)}", "https://textures.minecraft!!.net/texture/nope").forEach { url ->
            val bad = Base64.getEncoder().encodeToString("""{"textures":{"SKIN":{"url":"$url"}}}""".toByteArray())
            check(runCatching { RemoteHeads.parse(fixture(bad)) }.isFailure)
        }
        listOf("", " ", "a".repeat(129), "bad\nname", "§kname").forEach { check(runCatching { RemoteHeads.parse(fixture(name = it)) }.isFailure) }
        listOf("{}", "[]", "broken", " ".repeat(RemoteHeads.MAX_BYTES + 1), fixture("!"), json.replace(id, "1-1-1-1-1")).forEach {
            check(runCatching { RemoteHeads.parse(it) }.isFailure)
        }
        val duplicate = JsonParser.parseString(json).asJsonObject
        val other = duplicate.getAsJsonObject(id).deepCopy()
        val otherId = "31e712ab-7878-31ea-a398-0c86dab7b333"
        other.addProperty("uuid", otherId)
        duplicate.add(otherId, other)
        check(RemoteHeads.parse(duplicate.toString()).size == 1)
        val directory = Files.createTempDirectory("spotlight-remote-heads-check")
        try {
            val remote = RemoteHeads(directory)
            check(remote.heads.isEmpty())
            remote.accept(json)
            check(remote.heads == listOf(head))
            check(RemoteHeads(directory).heads == listOf(head))
            check(runCatching { remote.accept("broken") }.isFailure)
            check(remote.heads == listOf(head))
            check(Files.readString(directory.resolve("remote-heads.json")) == json)
            Files.writeString(directory.resolve("remote-heads.json"), "broken")
            check(RemoteHeads(directory).heads.isEmpty())
            Files.delete(directory.resolve("remote-heads.json"))
            Files.createSymbolicLink(directory.resolve("remote-heads.json"), directory.resolve("private.json"))
            check(runCatching { remote.accept(json) }.isFailure)
            check(!Files.exists(directory.resolve("private.json")))
            Files.delete(directory.resolve("remote-heads.json"))
            if (args.contains("--live")) {
                val result = remote.refresh().get(25, TimeUnit.SECONDS)
                check(result.isNotEmpty() && result.size <= RemoteHeads.MAX_HEADS)
                check(RemoteHeads(directory).heads == result)
                println("Live HeadDB HTTP 200: ${result.size} validated, distinct textures; cache round-trip passed")
            }
        } finally {
            Files.walk(directory).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
        net.minecraft.SharedConstants.tryDetectVersion()
        net.minecraft.server.Bootstrap.bootStrap()
        val registries = net.minecraft.data.registries.VanillaRegistries.createLookup()
        val stack = HeadDatabase.remoteStack(head)
        check(stack.`is`(net.minecraft.world.item.Items.PLAYER_HEAD))
        check(stack.hoverName.string == "Cup")
        val profile = stack.get(net.minecraft.core.component.DataComponents.PROFILE)!!
        check(HeadDatabase.hasTexture(profile.gameProfile()))
        println("NewRemoteHeadsCheck passed: verified fixture, bounds, URL allowlist, HTTPS, deduplication, atomic cache, native head; commands ignored")
    }
}
