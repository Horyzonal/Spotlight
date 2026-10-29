package dev.spotlight.platform

import java.lang.ref.WeakReference
import java.util.UUID


object BlockHitActionsCheck {
    @JvmStatic
    fun main(args: Array<String>) {
        val level = Any()
        val cache = BlockHitActions.HitCache()
        val hit = BlockHitActions.Hit("world", "minecraft:overworld", WeakReference(level),
            -32, -60, 48, "minecraft:diamond_ore", "Diamond Ore")
        val token = cache.put(hit)
        fun resolve(value: String = token.toString(), scope: String = hit.scope,
            dimension: String = hit.dimension, world: Any = level, block: String? = hit.blockId) =
            cache.resolve(value, scope, dimension, world) { block }
        fun rejected(action: () -> Unit) {
            check(runCatching(action).exceptionOrNull() is IllegalArgumentException)
        }
        check(resolve() === hit)
        rejected { resolve("not-a-token") }
        rejected { resolve(UUID.randomUUID().toString()) }
        rejected { resolve(scope = "other-world") }
        rejected { resolve(dimension = "minecraft:the_nether") }
        rejected { resolve(world = Any()) }
        rejected { resolve(block = null) }
        rejected { resolve(block = "minecraft:stone") }
        rejected { resolve("$token; say injected") }
        for (action in listOf("highlight", "location")) {
            check(BlockHitActions.command(action, token) == "/spotlight:hit $action $token")
        }
        rejected { BlockHitActions.command("say injected", token) }
        val retained = (1..31).map { cache.put(hit) }
        check(resolve() === hit)
        val newest = cache.put(hit)
        rejected { resolve() }
        retained.forEach { check(resolve(it.toString()) === hit) }
        check(resolve(newest.toString()) === hit)
        cache.clear()
        rejected { resolve(newest.toString()) }
        val expired = cache.put(hit.copy(level = WeakReference<Any>(null)))
        rejected { resolve(expired.toString()) }
        println("BlockHitActionsCheck passed: tokens, 32-entry bound, stale world/dimension/registry, clear, command safety")
    }
}
