package dev.spotlight.platform

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

internal object ServerPluginsCheck {
    @JvmStatic
    fun main(args: Array<String>) {
        check(serverPluginNamespaces(listOf(
            "minecraft:help", "brigadier:help", "help", "/essentials:home", "essentials:spawn",
            "bukkit:help", "spigot:version", "worldedit:wand target", "/bukkit:help", "abc_2.x-y:cmd",
        )) == listOf("abc_2.x-y", "bukkit", "essentials", "spigot", "worldedit"))
        check(serverPluginNamespaces(listOf(
            "", ":bad", "bad:", "//bad:cmd", "UPPER:cmd", "bad/name:cmd", "bad:cmd:extra",
            "say essentials:home", " bukkit:help", "bad\u0000:cmd", "bad:cmd\n", "bad:cmd\u001b",
            "bad\u202e:cmd", "bad:cmd\u00a7c", "bad:cmd\u200b", "x".repeat(65) + ":cmd",
        )).isEmpty())
        check(serverPluginNamespaces(listOf("x:cmd", "x".repeat(64) + ":cmd")).size == 2)
        check(serverPluginNamespaces(emptyList()).isEmpty())
        val dispatcher = CommandDispatcher<Any>()
        dispatcher.register(literal<Any>("zeta:root"))
        dispatcher.register(literal<Any>("alpha:root"))
        val suggestions = SuggestionsBuilder("/", 1).suggest("/alpha:other").suggest("beta:cmd").build()
        check(serverPluginNamespaces(dispatcher.root.children.map { it.name } + suggestions.list.map { it.text }) == listOf("alpha", "beta", "zeta"))
        check(dispatcher.parse("/", Any()).context.build("/").input == "/")

        val waiting = CompletableFuture<Suggestions>()
        check(boundedPluginSuggestions(waiting, 10).get(2, TimeUnit.SECONDS) == null)
        check(!waiting.isDone && !waiting.isCancelled)
        waiting.complete(suggestions)
        check(waiting.join() === suggestions)
        check(boundedPluginSuggestions(CompletableFuture.completedFuture(suggestions)).join() === suggestions)
        val failed = CompletableFuture<Suggestions>()
        val bounded = boundedPluginSuggestions(failed)
        failed.completeExceptionally(IllegalStateException("unavailable"))
        check(runCatching { bounded.join() }.isFailure)
        val interrupted = CompletableFuture<Suggestions>()
        val interruptedBounded = boundedPluginSuggestions(interrupted)
        interrupted.cancel(false)
        check(runCatching { interruptedBounded.join() }.isFailure)
        println("Server plugin namespace checks passed")
    }
}
