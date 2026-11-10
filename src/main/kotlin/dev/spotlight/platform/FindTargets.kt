package dev.spotlight.platform

import dev.spotlight.core.Entry
import net.minecraftforge.client.event.RenderLevelStageEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.chunk.ChunkStatus
import net.minecraft.world.phys.AABB

import java.util.PriorityQueue
import java.util.UUID
import kotlin.math.sqrt

internal fun entityEntries(): List<Entry> {
    val types = BuiltInRegistries.ENTITY_TYPE.map { type ->
        val id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString()
        Entry("entity:$id", type.description.string, id, id)
    }
    val tags = BuiltInRegistries.ENTITY_TYPE.tags.map { tag ->
        val id = "#${tag.first.location()}"
        Entry("entity:$id", id, "Entity type category", id)
    }.toList()
    val singles = Minecraft.getInstance().level?.entitiesForRendering()?.map { entity ->
        Entry("entity:uuid:${entity.uuid}", (entity.displayName ?: entity.name).string,
            "${BuiltInRegistries.ENTITY_TYPE.getKey(entity.type)} · ${entity.uuid}", "uuid:${entity.uuid}")
    }.orEmpty()
    return types + tags + singles
}

internal object FindTargets {

    private const val MAX_MARKERS = 64
    @Volatile private var highlights = emptyList<AABB>()
    private var highlightLevel: Any? = null
    private var expires = 0L

    fun initialize() {
        MinecraftForge.EVENT_BUS.addListener<RenderLevelStageEvent> { context ->
            if (context.stage == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS &&
                Minecraft.getInstance().level === highlightLevel && System.nanoTime() < expires) {
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
        requireNotNull(client.player)
        val predicate: (Entity) -> Boolean = when {
            text.startsWith("uuid:") -> {
                val uuid = UUID.fromString(text.removePrefix("uuid:"));
                { entity -> entity.uuid == uuid }
            }
            text.startsWith('#') -> {
                val id = requireNotNull(ResourceLocation.tryParse(text.drop(1))) { "Invalid entity category" }
                val tag = BuiltInRegistries.ENTITY_TYPE.tags.filter { it.first.location() == id }.findFirst()
                    .orElseThrow { IllegalArgumentException("Unknown entity category: $text") }
                val types = tag.second.stream().map { it.value() }.toList().toSet();
                { entity -> entity.type in types }
            }
            else -> {
                val id = requireNotNull(ResourceLocation.tryParse(text)) { "Invalid entity type" }
                require(BuiltInRegistries.ENTITY_TYPE.containsKey(id)) { "Unknown entity type: $text" }
                val type = BuiltInRegistries.ENTITY_TYPE.get(id);
                { entity -> entity.type == type }
            }
        }
        val job = PersistentTargetSearch<SearchArea, EntityScan>(
            create = { area -> EntityScan(level.entitiesForRendering().iterator(), area, requireNotNull(client.player).position()) },
            advance = { pass ->
                val deadline = System.nanoTime() + 2_000_000L
                var work = 0
                while (work < 4096 && System.nanoTime() < deadline && pass.entities.hasNext()) {
                    val entity = pass.entities.next()
                    work++
                    if (entity.isAlive && pass.area.contains(entity.blockPosition()) && predicate(entity)) {
                        pass.nearest.add(entity, entity.distanceToSqr(pass.start))
                    }
                }
                !pass.entities.hasNext()
            },
            found = { pass -> pass.nearest.values().any { it.isAlive && pass.area.contains(it.blockPosition()) } },
        )
        var taskId = 0L
        taskId = BackgroundTasks.register("Find entities: $text", "Searching loaded entities; stop in Tasks", tick = {
            val currentPlayer = client.player
            if (client.level !== level || currentPlayer == null) {
                job.cancel()
                true
            } else {
                val area = SearchArea(currentPlayer.blockPosition(), client.options.effectiveRenderDistance)
                val pass = job.tick(area)
                BackgroundTasks.update(taskId, "Loaded entities; ${area.radius} chunks each direction; retry until found; stop in Tasks")
                if (pass == null) false else {
                    val found = pass.nearest.values().filter { it.isAlive && area.contains(it.blockPosition()) }
                        .sortedBy { it.distanceToSqr(currentPlayer) }
                    val nearest = found.first()
                    highlight(found.map { it.boundingBox.inflate(0.05) })
                    val pos = nearest.blockPosition()
                    val distance = String.format(java.util.Locale.ROOT, "%.1f", sqrt(nearest.distanceToSqr(currentPlayer)))
                    client.gui.chat.addMessage(Component.literal(
                        "Nearest loaded entity: ${(nearest.displayName ?: nearest.name).string} at ${pos.x}, ${pos.y}, ${pos.z}; " +
                            "UUID ${nearest.uuid}; $distance blocks; highlighting nearest ${found.size} (up to $MAX_MARKERS) for 15s"))
                    true
                }
            }
        }, cancel = job::cancel)
        notifySpotlight("Finding loaded $text across render distance; retrying until found or stopped in Tasks")
    }

    fun blocks(text: String) {
        val id = requireNotNull(ResourceLocation.tryParse(text)) { "Invalid block ID" }
        require(BuiltInRegistries.BLOCK.containsKey(id)) { "Unknown block: $text" }
        val client = Minecraft.getInstance()
        requireNotNull(client.player) { "Join a world first" }
        val level = requireNotNull(client.level)
        val block = BuiltInRegistries.BLOCK.get(id)
        val job = PersistentTargetSearch<SearchArea, NearestBlockSearch>(
            create = { area ->
                val start = requireNotNull(client.player).position()
                NearestBlockSearch(start.x, start.y, start.z,
                    area.minX, level.minBuildHeight, area.minZ, area.maxX, (level.maxBuildHeight - 1), area.maxZ,
                    sectionMayContain = { x, y, z ->
                        level.chunkSource.getChunk(x, z, ChunkStatus.FULL, false)
                            ?.getSection(level.getSectionIndexFromSectionY(y))?.maybeHas { it.block == block } == true
                    },
                    matches = { x, y, z ->
                        level.chunkSource.getChunk(x shr 4, z shr 4, ChunkStatus.FULL, false)
                            ?.getBlockState(BlockPos(x, y, z))?.block == block
                    })
            },
            advance = { it.advance(); it.done },
            found = { it.result != null },
        )
        var taskId = 0L
        taskId = BackgroundTasks.register("Find block: $id", "Searching loaded blocks; stop in Tasks", tick = {
            val currentPlayer = client.player
            if (client.level !== level || currentPlayer == null) {
                job.cancel()
                true
            } else {
                val area = SearchArea(currentPlayer.blockPosition(), client.options.effectiveRenderDistance)
                val pass = job.tick(area)
                BackgroundTasks.update(taskId, "${area.radius} chunks each direction, full build height; retry until found; stop in Tasks")
                val hit = pass?.result
                if (hit == null) false else {
                    val pos = BlockPos(hit.x, hit.y, hit.z)
                    highlightBlock(pos)
                    val distance = String.format(java.util.Locale.ROOT, "%.1f", sqrt(hit.distanceSquared))
                    client.gui.chat.addMessage(BlockHitActions.message(
                        "Nearest loaded block $id at ${hit.x}, ${hit.y}, ${hit.z}; $distance blocks from search start; highlighting for 15s", pos))
                    true
                }
            }
        }, cancel = job::cancel)
        notifySpotlight("Finding nearest loaded $id across render distance (${client.options.effectiveRenderDistance} chunks each direction), " +
            "full build height; 4096 steps/2 ms per tick; retrying until found or stopped in Tasks")
    }

    fun tick() {
        val client = Minecraft.getInstance()
        if (client.level !== highlightLevel || System.nanoTime() >= expires) {
            highlightLevel = null
            highlights = emptyList()
        }
    }

    internal data class SearchArea(val center: BlockPos, val radius: Int) {
        val minX = ((center.x shr 4) - radius) shl 4
        val minZ = ((center.z shr 4) - radius) shl 4
        val maxX = (((center.x shr 4) + radius + 1) shl 4) - 1
        val maxZ = (((center.z shr 4) + radius + 1) shl 4) - 1

        fun contains(pos: BlockPos): Boolean = pos.x in minX..maxX && pos.z in minZ..maxZ
    }

    private class EntityScan(
        val entities: Iterator<Entity>, val area: SearchArea, val start: net.minecraft.world.phys.Vec3,
    ) {
        val nearest = NearestTargetCandidates<Entity>(MAX_MARKERS)
    }
}

internal class PersistentTargetSearch<C, P>(
    private val create: (C) -> P,
    private val advance: (P) -> Boolean,
    private val found: (P) -> Boolean,
) {
    private var center: C? = null
    private var pass: P? = null
    private var cooldown = 0
    private var stopped = false

    fun cancel() {
        stopped = true
        pass = null
    }

    fun tick(current: C): P? {
        if (stopped) return null
        if (center != current) {
            center = current
            pass = null
            cooldown = 0
        }
        if (cooldown > 0) {
            cooldown--
            return null
        }
        val scan = pass ?: create(current).also { pass = it }
        if (!advance(scan)) return null
        pass = null
        if (found(scan)) {
            stopped = true
            return scan
        }
        cooldown = 20
        return null
    }
}

internal class NearestTargetCandidates<T>(private val limit: Int) {
    private data class Candidate<T>(val value: T, val distance: Double)
    private val nearest = PriorityQueue<Candidate<T>>(compareByDescending { it.distance })

    fun add(value: T, distance: Double) {
        if (nearest.size < limit) nearest.add(Candidate(value, distance))
        else if (distance < nearest.peek().distance) {
            nearest.poll()
            nearest.add(Candidate(value, distance))
        }
    }

    fun values(): List<T> = nearest.sortedBy { it.distance }.map { it.value }
}
