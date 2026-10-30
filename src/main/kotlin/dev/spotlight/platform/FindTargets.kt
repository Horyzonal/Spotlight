package dev.spotlight.platform

import dev.spotlight.core.Entry
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries

import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.phys.AABB

import java.util.UUID
import kotlin.math.sqrt

internal fun entityEntries(): List<Entry> {
    val types = BuiltInRegistries.ENTITY_TYPE.map { type ->
        val id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString()
        Entry("entity:$id", type.description.string, id, id)
    }
    val tags = BuiltInRegistries.ENTITY_TYPE.tags.map { tag ->
        val id = "#${tag.key().location()}"
        Entry("entity:$id", id, "Entity type category", id)
    }.toList()
    val singles = Minecraft.getInstance().level?.entitiesForRendering()?.map { entity ->
        Entry("entity:uuid:${entity.uuid}", (entity.displayName ?: entity.name).string,
            "${BuiltInRegistries.ENTITY_TYPE.getKey(entity.type)} · ${entity.uuid}", "uuid:${entity.uuid}")
    }.orEmpty()
    return types + tags + singles
}

internal object FindTargets {

    private const val RADIUS = 32
    private const val MAX_MARKERS = 64
    private var scan: BlockScan? = null
    @Volatile private var highlights = emptyList<AABB>()
    private var highlightLevel: Any? = null
    private var expires = 0L

    fun initialize() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register { context ->
            if (Minecraft.getInstance().level === highlightLevel && System.nanoTime() < expires) {
                highlights.forEach { drawWorldOutline(context, it, 0xff55ffff.toInt(), 2f) }
            }
        }
    }

    private fun highlight(boxes: List<AABB>) {
        highlightLevel = Minecraft.getInstance().level
        highlights = boxes
        expires = System.nanoTime() + 15_000_000_000L
    }

    fun highlightBlock(pos: BlockPos) {
        highlight(listOf(AABB(pos).inflate(0.01)))
    }

    fun entities(text: String) {
        val client = Minecraft.getInstance()
        val level = requireNotNull(client.level) { "Join a world first" }
        val player = requireNotNull(client.player)
        val predicate: (Entity) -> Boolean = when {
            text.startsWith("uuid:") -> {
                val uuid = UUID.fromString(text.removePrefix("uuid:"));
                { entity -> entity.uuid == uuid }
            }
            text.startsWith('#') -> {
                val id = requireNotNull(ResourceLocation.tryParse(text.drop(1))) { "Invalid entity category" }
                val tag = BuiltInRegistries.ENTITY_TYPE.tags.filter { it.key().location() == id }.findFirst()
                    .orElseThrow { IllegalArgumentException("Unknown entity category: $text") }
                val types = tag.stream().map { it.value() }.toList().toSet();
                { entity -> entity.type in types }
            }
            else -> {
                val id = requireNotNull(ResourceLocation.tryParse(text)) { "Invalid entity type" }
                require(BuiltInRegistries.ENTITY_TYPE.containsKey(id)) { "Unknown entity type: $text" }
                val type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
                { entity -> entity.type == type }
            }
        }
        val found = level.entitiesForRendering().filter { it.isAlive && predicate(it) && it.distanceToSqr(player) <= RADIUS * RADIUS }
            .sortedBy { it.distanceToSqr(player) }
        highlight(found.take(MAX_MARKERS).map { it.boundingBox.inflate(0.05) })
        feedback(found.size, found.firstOrNull()?.let { sqrt(it.distanceToSqr(player)) }, "entities")
    }

    fun blocks(text: String) {
        val id = requireNotNull(ResourceLocation.tryParse(text)) { "Invalid block ID" }
        require(BuiltInRegistries.BLOCK.containsKey(id)) { "Unknown block: $text" }
        val client = Minecraft.getInstance()
        val player = requireNotNull(client.player) { "Join a world first" }
        val level = requireNotNull(client.level)
        val start = player.position()
        val radius = client.options.effectiveRenderDistance
        val origin = player.blockPosition()
        val block = BuiltInRegistries.BLOCK.getValue(id)
        val search = NearestBlockSearch(start.x, start.y, start.z,
            ((origin.x shr 4) - radius) shl 4, level.minY, ((origin.z shr 4) - radius) shl 4,
            (((origin.x shr 4) + radius + 1) shl 4) - 1, level.maxY, (((origin.z shr 4) + radius + 1) shl 4) - 1,
            sectionMayContain = { x, y, z ->
                level.chunkSource.getChunk(x, z, ChunkStatus.FULL, false)
                    ?.getSection(level.getSectionIndexFromSectionY(y))?.maybeHas { it.block == block } == true
            },
            matches = { x, y, z ->
                level.chunkSource.getChunk(x shr 4, z shr 4, ChunkStatus.FULL, false)
                    ?.getBlockState(BlockPos(x, y, z))?.block == block
            })
        scan = BlockScan(level, search, radius)
        highlight(emptyList())
        notifySpotlight("Finding nearest loaded $id across render distance ($radius chunks each direction), full build height; 4096 steps/2 ms per tick")
    }

    fun tick() {
        val client = Minecraft.getInstance()
        if (client.level == null) {
            scan = null
            highlightLevel = null
            highlights = emptyList()
            return
        }
        val current = scan ?: return
        if (client.level !== current.level || client.player == null) { scan = null; return }
        current.search.advance()
        if (current.search.done) {
            val hit = current.search.result
            if (hit == null) highlight(emptyList()) else highlightBlock(BlockPos(hit.x, hit.y, hit.z))
            val message = if (hit == null) {
                "No matching loaded block across render distance (${current.radius} chunks each direction), full build height"
            } else {
                "Nearest loaded block at ${hit.x}, ${hit.y}, ${hit.z}; " +
                    "${String.format(java.util.Locale.ROOT, "%.1f", sqrt(hit.distanceSquared))} blocks from search start; highlighting for 15s"
            }
            client.gui.chat.addMessage(if (hit == null) Component.literal(message)
                else BlockHitActions.message(message, BlockPos(hit.x, hit.y, hit.z)))
            scan = null
        }
    }

    private fun feedback(count: Int, distance: Double?, kind: String) {
        val message = "$count loaded $kind within $RADIUS blocks" +
            (distance?.let { "; nearest ${String.format(java.util.Locale.ROOT, "%.1f", it)} blocks; highlighting up to $MAX_MARKERS for 15s" } ?: "")
        Minecraft.getInstance().gui.chat.addMessage(Component.literal(message))
    }


    private class BlockScan(val level: net.minecraft.client.multiplayer.ClientLevel, val search: NearestBlockSearch, val radius: Int)
}
