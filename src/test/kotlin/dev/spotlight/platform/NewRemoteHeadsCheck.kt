package dev.spotlight.platform

import com.google.gson.JsonParser
import java.nio.file.Files
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

object NewRemoteHeadsCheck {
    @JvmStatic fun main(args: Array<String>) {
        val json = requireNotNull(javaClass.getResource("/heads/alphabet.json")).readText()
        val head = RemoteHeads.parse(json).first()
        check(head.name == "? Block" && "Punctuation Mark" in head.tags)
        val decoded = Base64.getDecoder().decode(head.texture).toString(Charsets.UTF_8)
        check(decoded.contains("https://textures.minecraft.net/texture/") && !decoded.contains("http://"))
        fun mutated(key: String, value: String): String = JsonParser.parseString(json).asJsonArray.apply {
            get(0).asJsonObject.addProperty(key, value)
        }.toString()
        listOf("file:///etc/passwd", "https://example.org/texture/${"a".repeat(64)}", "https://textures.minecraft.net.evil.org/texture/${"a".repeat(64)}", "https://textures.minecraft.net/texture/nope", "https://textures.minecraft.net/texture/${"a".repeat(64)}?x=1").forEach { url ->
            val bad = Base64.getEncoder().encodeToString("""{"textures":{"SKIN":{"url":"$url"}}}""".toByteArray())
            check(runCatching { RemoteHeads.parse(mutated("value", bad)) }.isFailure)
        }
        listOf("", " ", "a".repeat(129), "bad\nname").forEach { check(runCatching { RemoteHeads.parse(mutated("name", it)) }.isFailure) }
        listOf("{}", "[]", "broken", " ".repeat(RemoteHeads.MAX_BYTES + 1), mutated("value", "!"), mutated("uuid", "1-1-1-1-1"), mutated("tags", "a".repeat(4097)), json.replace("\"tags\":", "\"not_tags\":")).forEach {
            check(runCatching { RemoteHeads.parse(it) }.isFailure)
        }
        check(RemoteHeads.parse(mutated("name", "Aqua (#55FFFF / §b)")).first().name == "Aqua (#55FFFF / &b)")
        check(RemoteHeads.failureMessage(java.util.concurrent.CompletionException(IllegalArgumentException("Category rejected"))) == "Category rejected")
        val duplicate = JsonParser.parseString(json).asJsonArray
        duplicate.add(duplicate[0].deepCopy().apply { asJsonObject.addProperty("uuid", "31e712ab-7878-31ea-a398-0c86dab7b333") })
        check(RemoteHeads.parse(duplicate.toString()).size == RemoteHeads.parse(json).size)
        val directory = Files.createTempDirectory("spotlight-remote-heads-check")
        try {
            Files.writeString(directory.resolve("remote-heads.json"), "unrelated legacy data")
            val remote = RemoteHeads(directory)
            check(remote.heads.isEmpty())
            remote.accept("alphabet", json)
            check(remote.heads.first() == head)
            check(RemoteHeads(directory).heads == remote.heads)
            check(runCatching { remote.accept("alphabet", "broken") }.isFailure)
            check(remote.heads.first() == head)
            check(Files.readString(directory.resolve("minecraft-heads-alphabet.json")) == json)
            check(Files.readString(directory.resolve("remote-heads.json")) == "unrelated legacy data")
            check(runCatching { remote.accept("../escape", json) }.isFailure)
            Files.createSymbolicLink(directory.resolve("minecraft-heads-blocks.json"), directory.resolve("private.json"))
            check(runCatching { remote.accept("blocks", json) }.isFailure)
            check(!Files.exists(directory.resolve("private.json")))
            check(remote.heads.first() == head)
            Files.delete(directory.resolve("minecraft-heads-blocks.json"))
            Files.delete(directory.resolve("minecraft-heads-alphabet.json"))
            Files.createSymbolicLink(directory.resolve("minecraft-heads-alphabet.json"), directory.resolve("private.json"))
            check(runCatching { remote.accept("alphabet", json) }.isFailure)
            check(remote.heads.first() == head && !Files.exists(directory.resolve("private.json")))
            Files.delete(directory.resolve("minecraft-heads-alphabet.json"))
            remote.accept("alphabet", json)
            val requests = mutableListOf<String>()
            val first = CompletableFuture<String>()
            val controlled = RemoteHeads(directory) { category ->
                synchronized(requests) { requests += category }
                if (category == "alphabet") first else CompletableFuture.completedFuture(json)
            }
            val all = controlled.refresh()
            check(runCatching { controlled.refresh("blocks").join() }.isFailure)
            check(!all.isDone)
            check(synchronized(requests) { requests.all { it == "alphabet" } })
            first.complete(json)
            all.get(20, TimeUnit.SECONDS)
            check(requests == RemoteHeads.CATEGORIES)
            check(controlled.heads.size == RemoteHeads.parse(json).size)
            controlled.refresh("alphabet", force = true).get(5, TimeUnit.SECONDS)
            check(requests == RemoteHeads.CATEGORIES)
            controlled.refresh(force = false).get(5, TimeUnit.SECONDS)
            check(requests == RemoteHeads.CATEGORIES)
            val failed = RemoteHeads(directory) { CompletableFuture.completedFuture("broken") }
            val before = failed.heads
            check(runCatching { failed.refresh("alphabet").get(5, TimeUnit.SECONDS) }.isFailure)
            check(failed.heads == before && failed.status.contains("failed") && failed.status.contains("Prior category caches retained"))
            Files.writeString(directory.resolve("minecraft-heads-alphabet.json"), "broken")
            check(RemoteHeads(directory).heads("alphabet").isEmpty())
            if (args.contains("--live")) {
                val result = remote.refresh("alphabet").get(30, TimeUnit.SECONDS)
                check(result.isNotEmpty() && result.size <= RemoteHeads.MAX_HEADS)
                check(RemoteHeads(directory).heads("alphabet") == result)
                println("Live legacy alphabet HTTP 200: ${result.size} validated distinct textures; cache round-trip passed")
            }
        } finally {
            Files.walk(directory).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
        net.minecraft.SharedConstants.tryDetectVersion()
        net.minecraft.server.Bootstrap.bootStrap()
        val registries = net.minecraft.data.registries.VanillaRegistries.createWorldLookup()
        net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registries).forEach { it.apply() }
        val stack = HeadDatabase.remoteStack(head)
        check(stack.`is`(net.minecraft.world.item.Items.PLAYER_HEAD))
        check(stack.hoverName.string == head.name)
        check(HeadDatabase.hasTexture(stack.get(net.minecraft.core.component.DataComponents.PROFILE)!!.partialProfile()))
        println("NewRemoteHeadsCheck passed: live-derived array fixture, tags, bounds, texture allowlist, HTTPS, duplicates, isolated atomic caches, sequential loads, failures, single-flight, native head")
    }
}
