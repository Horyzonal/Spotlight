package dev.spotlight.platform

import com.mojang.brigadier.suggestion.Suggestions
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.multiplayer.ClientSuggestionProvider
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

internal fun serverPluginNamespaces(commands: Iterable<String>): List<String> = commands.mapNotNull { text ->
    if (text.any { it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt() }) return@mapNotNull null
    val token = text.removePrefix("/").takeWhile { !it.isWhitespace() }
    val namespace = token.substringBefore(':', "")
    namespace.takeIf {
        it.matches(Regex("[a-z0-9_.-]{1,64}")) && it !in setOf("minecraft", "brigadier") &&
            token.substringAfter(':', "").matches(Regex("[A-Za-z0-9_.-]+"))
    }
}.distinct().sorted()

internal fun boundedPluginSuggestions(future: CompletableFuture<Suggestions>, timeoutMillis: Long = 3000): CompletableFuture<Suggestions?> =
    future.thenApply<Suggestions?> { it }.completeOnTimeout(null, timeoutMillis, TimeUnit.MILLISECONDS)

internal object ServerPlugins {
    private val inFlight = Collections.newSetFromMap(IdentityHashMap<ClientPacketListener, Boolean>())
    
    private val pending = runCatching {
        ClientSuggestionProvider::class.java.getDeclaredField("pendingSuggestionsFuture").apply { isAccessible = true }
    }.getOrNull()

    fun query() {
        val client = Minecraft.getInstance()
        if (client.level == null || client.player == null) return
        val connection = client.connection ?: return
        val level = client.level
        if (!inFlight.add(connection)) {
            notifySpotlight("Server plugin namespace request already in progress")
            return
        }
        val roots = connection.commands.root.children.map { it.name }
        fun finish(suggestions: Suggestions?, status: String) {
            inFlight.remove(connection)
            if (client.connection !== connection || client.level !== level) return
            val namespaces = serverPluginNamespaces(roots + suggestions?.list.orEmpty().map { it.text })
            val result = if (namespaces.isEmpty()) "0; no matching namespaces found" else "${namespaces.size}; ${namespaces.joinToString(", ")}"
            reportDebug("Possible server plugin namespaces (inferred)", "$result. $status. Not authoritative; server may hide commands or plugins, and namespaces need not identify plugins. No permission bypass.")
        }
        val provider = connection.suggestionsProvider
        val idle = runCatching { pending != null && pending.get(provider) == null }.getOrDefault(false)
        if (!idle) {
            finish(null, "Server completion unavailable (busy or busy check unavailable); dispatcher only; no request sent")
            return
        }
        try {
            val context = connection.commands.parse("/", provider).context.build("/")
            val future = provider.customSuggestion(context)
            notifySpotlight("Checking possible plugin namespaces; one completion request, up to 3 seconds; new tab completion may interrupt it")
            boundedPluginSuggestions(future).whenComplete { suggestions, error ->
                client.execute {
                    finish(suggestions, when {
                        error != null -> "Server completion unavailable or interrupted; dispatcher only"
                        suggestions == null -> "Server completion timed out after 3 seconds; dispatcher only"
                        else -> "Sources: visible dispatcher commands and one server completion response"
                    })
                }
            }
        } catch (_: Exception) {
            finish(null, "Server completion unavailable; dispatcher only; no retry")
        }
    }
}
