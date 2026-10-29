package dev.spotlight.platform

import com.mojang.brigadier.arguments.StringArgumentType
import dev.spotlight.core.SavedLocation
import dev.spotlight.ui.LocationEditScreen
import dev.spotlight.ui.SpotlightTheme
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import net.minecraft.world.level.chunk.status.ChunkStatus
import java.lang.ref.WeakReference
import java.util.UUID

internal object BlockHitActions {
    private val hits = HitCache()

    fun initialize() {
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> hits.clear() }
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            val root = ClientCommands.literal("spotlight:hit")
            listOf("highlight", "location").forEach { action ->
                root.then(ClientCommands.literal(action).then(
                    ClientCommands.argument("token", StringArgumentType.word()).executes { context ->
                        run(context.source, action, StringArgumentType.getString(context, "token"))
                    }))
            }
            dispatcher.register(root)
        }
    }

    fun message(text: String, pos: BlockPos): Component {
        val client = Minecraft.getInstance()
        val level = requireNotNull(client.level)
        val block = level.getBlockState(pos).block
        val token = hits.put(Hit(Spotlight.scope(), Spotlight.dimension(), WeakReference(level),
            pos.x, pos.y, pos.z, BuiltInRegistries.BLOCK.getKey(block).toString(), block.name.string))
        fun button(label: String, action: String, description: String) = Component.literal(label).setStyle(
            Style.EMPTY.withColor(SpotlightTheme.color(0xFF81B8FF) and 0xFFFFFF).withUnderlined(true)
                .withClickEvent(ClickEvent.RunCommand(command(action, token)))
                .withHoverEvent(HoverEvent.ShowText(Component.literal(description))))
        return Component.literal(text).withStyle { it.withColor(SpotlightTheme.color(0xFFF2F5FC) and 0xFFFFFF) }
            .append(" ").append(button("[Highlight again]", "highlight", "Highlight only this block for another 15 seconds"))
            .append(" ").append(button("[Add location]", "location", "Open a prefilled location editor; nothing saves until Save"))
    }

    internal fun command(action: String, token: UUID): String {
        require(action == "highlight" || action == "location") { "Unknown block hit action" }
        return "/spotlight:hit $action $token"
    }

    private fun run(source: FabricClientCommandSource, action: String, token: String): Int {
        try {
            val client = source.client
            val level = requireNotNull(client.level) { "Join a world first" }
            require(client.player != null) { "Join a world first" }
            val hit = hits.resolve(token, Spotlight.scope(), Spotlight.dimension(), level) { target ->
                level.chunkSource.getChunk(target.x shr 4, target.z shr 4, ChunkStatus.FULL, false)
                    ?.getBlockState(BlockPos(target.x, target.y, target.z))?.block
                    ?.let { BuiltInRegistries.BLOCK.getKey(it).toString() }
            }
            when (action) {
                "highlight" -> {
                    FindTargets.highlightBlock(BlockPos(hit.x, hit.y, hit.z))
                    source.sendFeedback(Component.literal("Highlighting ${hit.name} at ${hit.x}, ${hit.y}, ${hit.z} for 15s"))
                }
                "location" -> {
                    val draft = SavedLocation(UUID.randomUUID().toString(), "${hit.name} (${hit.x}, ${hit.y}, ${hit.z})",
                        hit.x.toDouble(), hit.y.toDouble(), hit.z.toDouble(), hit.dimension, hit.scope)
                    
                    client.schedule { client.gui.setScreen(LocationEditScreen(client.gui.screen(), draft)) }
                }
                else -> error("Unknown block hit action")
            }
            return 1
        } catch (failure: IllegalArgumentException) {
            val message = failure.message ?: "Invalid block hit"
            notifySpotlight(message)
            source.sendError(Component.literal(message))
            return 0
        }
    }

    internal data class Hit(val scope: String, val dimension: String, val level: WeakReference<out Any>,
        val x: Int, val y: Int, val z: Int, val blockId: String, val name: String)

    internal class HitCache {
        private val entries = linkedMapOf<UUID, Hit>()

        fun put(hit: Hit): UUID {
            val token = UUID.randomUUID()
            entries[token] = hit
            if (entries.size > 32) entries.remove(entries.keys.first())
            return token
        }

        fun clear() = entries.clear()

        fun resolve(token: String, scope: String, dimension: String, level: Any, blockAt: (Hit) -> String?): Hit {
            val id = try { UUID.fromString(token) } catch (_: IllegalArgumentException) { null }
            val hit = requireNotNull(entries[id]) { "Block hit expired or unknown; search again" }
            require(hit.scope == scope && hit.dimension == dimension && hit.level.get() === level) {
                "Block hit belongs to another world or dimension; search again"
            }
            require(blockAt(hit) == hit.blockId) { "Block hit unloaded or changed; search again" }
            return hit
        }
    }
}
