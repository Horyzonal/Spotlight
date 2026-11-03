package dev.spotlight.platform

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import dev.spotlight.core.Entry
import dev.spotlight.core.Fuzzy
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.*
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ResolvableProfile
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

internal data class SavedHead(val name: String, val tags: List<String>, val raw: CompoundTag)

/** JSON keeps native stack SNBT opaque, including components unavailable in current registries. */
internal class HeadFile(private val directory: Path) {
    companion object {
        const val MAX_BYTES = 4 * 1024 * 1024
        const val MAX_HEADS = 512
        fun validate(head: SavedHead) {
            require(head.name.isNotBlank() && head.name.length <= 128) { "Name must be 1..128 characters" }
            require(head.tags.size <= 16 && head.tags.all { it.isNotBlank() && it.length <= 48 }) { "Use at most 16 tags of 1..48 characters" }
            require(head.raw.toString().toByteArray(Charsets.UTF_8).size <= 65536) { "Head exceeds 64 KiB" }
            require(head.raw.getStringOr("id", "") in setOf("minecraft:player_head", "minecraft:skeleton_skull", "minecraft:wither_skeleton_skull", "minecraft:zombie_head", "minecraft:creeper_head", "minecraft:dragon_head", "minecraft:piglin_head")) { "Expected head or skull item" }
            require(head.raw.getIntOr("count", 0) == 1) { "Saved heads must have count 1" }
            require(!head.raw.contains("components") || head.raw.get("components") is CompoundTag) { "Invalid stack components" }
        }
        fun parse(json: String): List<SavedHead> {
            require(json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "heads.json exceeds 4 MiB" }
            val root = JsonParser.parseString(json).asJsonObject
            require(root.get("version").asInt == 1) { "Unsupported heads.json version" }
            val heads = root.getAsJsonArray("heads")
            require(heads.size() <= MAX_HEADS) { "Maximum 512 saved heads" }
            val result = heads.map { element ->
                val obj = element.asJsonObject
                SavedHead(obj.get("name").asString, obj.getAsJsonArray("tags").map { it.asString }, TagParser.parseCompoundFully(obj.get("stack").asString)).also(::validate)
            }
            require(result.map { it.name.lowercase(Locale.ROOT) }.distinct().size == result.size) { "Duplicate saved name; original preserved" }
            return result
        }
        fun json(heads: List<SavedHead>): String {
            require(heads.size <= MAX_HEADS) { "Maximum 512 saved heads" }
            val root = JsonObject().apply {
                addProperty("version", 1)
                add("heads", JsonArray().apply { heads.forEach { head ->
                    validate(head)
                    add(JsonObject().apply {
                        addProperty("name", head.name)
                        add("tags", JsonArray().apply { head.tags.forEach { add(it) } })
                        addProperty("stack", head.raw.toString())
                    })
                } })
            }
            return GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root).also { parse(it) }
        }
    }
    private fun path(filename: String): Path {
        require(filename.matches(Regex("[A-Za-z0-9_-]{1,64}\\.(json|nbt)"))) { "Use local filename, no directories" }
        Files.createDirectories(directory)
        require(!Files.isSymbolicLink(directory)) { "Head directory cannot be a symlink" }
        return directory.resolve(filename).also { require(!Files.isSymbolicLink(it)) { "Head file cannot be a symlink" } }
    }
    fun load(): List<SavedHead> {
        val file = path("heads.json")
        if (!Files.exists(file, NOFOLLOW_LINKS)) return emptyList()
        require(Files.isRegularFile(file, NOFOLLOW_LINKS) && Files.size(file) <= MAX_BYTES) { "Invalid or oversized heads.json; original preserved" }
        return parse(Files.newInputStream(file, NOFOLLOW_LINKS).use { input ->
            val bytes = input.readNBytes(MAX_BYTES + 1)
            require(bytes.size <= MAX_BYTES) { "heads.json exceeds 4 MiB" }
            bytes.toString(Charsets.UTF_8)
        })
    }
    fun add(head: SavedHead) {
        validate(head)
        val previous = load()
        require(previous.none { it.name.equals(head.name, true) }) { "Name exists; original preserved" }
        val bytes = json(previous + head).toByteArray(Charsets.UTF_8)
        val temporary = Files.createTempFile(directory, "heads-", ".tmp")
        try {
            Files.write(temporary, bytes)
            Files.move(temporary, path("heads.json"), ATOMIC_MOVE, REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temporary) }
    }
    fun importNbt(filename: String): CompoundTag {
        require(filename.endsWith(".nbt")) { "Expected compressed .nbt stack" }
        val file = path(filename)
        require(Files.isRegularFile(file, NOFOLLOW_LINKS) && Files.size(file) <= 65536) { "NBT file exceeds 64 KiB or is missing" }
        return Files.newInputStream(file, NOFOLLOW_LINKS).use { NbtIo.readCompressed(it, NbtAccounter.create(1024 * 1024L)) }
    }
    fun exportNbt(filename: String, raw: CompoundTag) {
        require(filename.endsWith(".nbt")) { "Expected .nbt filename" }
        val destination = path(filename)
        // CREATE_NEW preserves existing exports, including dangling links.
        Files.newOutputStream(destination, java.nio.file.StandardOpenOption.CREATE_NEW).use { NbtIo.writeCompressed(raw, it) }
    }
}

object HeadDatabase {
    data class Head(val id: String, val title: String, val source: String, val tags: List<String>, val raw: CompoundTag)
    private val client get() = Minecraft.getInstance()
    private val files by lazy { HeadFile(FabricLoader.getInstance().configDir.resolve("spotlight")) }
    private val remote by lazy { RemoteHeads(FabricLoader.getInstance().configDir.resolve("spotlight")) }
    val remoteStatus get() = remote.status
    fun refreshRemote(): CompletableFuture<*> = remote.refresh()
    private var remoteSnapshot: List<RemoteHead>? = null
    private var remoteEntries: List<Head> = emptyList()
    internal fun remoteStack(head: RemoteHead): ItemStack {
        val properties = com.mojang.authlib.properties.PropertyMap(com.google.common.collect.ImmutableMultimap.of("textures", Property("textures", head.texture)))
        val profile = GameProfile(head.id, "HeadDB", properties)
        return profileStack(profile).apply { set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(head.name)) }
    }
    private val vanilla get() = listOf(Items.SKELETON_SKULL, Items.WITHER_SKELETON_SKULL, Items.ZOMBIE_HEAD, Items.CREEPER_HEAD, Items.DRAGON_HEAD, Items.PIGLIN_HEAD)
    internal fun validPlayer(value: String): Boolean = value.matches(Regex("[A-Za-z0-9_]{1,16}")) ||
        (value.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) && runCatching { UUID.fromString(value) }.isSuccess)
    fun profileStack(profile: GameProfile): ItemStack = ItemStack(Items.PLAYER_HEAD).apply {
        set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile))
    }
    fun hasTexture(profile: GameProfile) = profile.properties().containsKey("textures")
    fun entries(): List<Head> {
        requireNotNull(client.connection) { "Join a world first" }
        val saved = files.load().map { Head("saved:${it.name}", it.name, "Saved", it.tags, it.raw.copy()) }
        val builtIn = vanilla.map { item ->
            val stack = ItemStack(item)
            val raw = ClientItemStorage.encode(stack)
            Head("vanilla:${raw.getStringOr("id", "")}", stack.hoverName.string, "Vanilla", listOf("mob"), raw)
        }
        val profiles = (client.connection!!.onlinePlayers.map { it.profile } + client.gameProfile).distinctBy { it.id() }
        val online = profiles.map { profile ->
            Head("online:${profile.id()}", profile.name(), "Online", listOf("player", if (hasTexture(profile)) "textured" else "default skin"), ClientItemStorage.encode(profileStack(profile)))
        }
        val snapshot = remote.heads
        if (snapshot !== remoteSnapshot) {
            remoteEntries = snapshot.map { Head("headdb:${it.id}", it.name, "HeadDB.org", listOf("remote", "custom"), ClientItemStorage.encode(remoteStack(it))) }
            remoteSnapshot = snapshot
        }
        return saved + builtIn + online + remoteEntries
    }
    fun search(query: String, entries: List<Head>): List<Head> {
        val indexed = entries.associateBy { it.id }
        return Fuzzy.search(query, entries.map { Entry(it.id, it.title, "${it.source} ${it.tags.joinToString(" ")} ${it.raw.getStringOr("id", "")}") }, limit = entries.size).map { indexed.getValue(it.id) }
    }
    fun saveHeld(name: String, tags: String) {
        val stack = requireNotNull(client.player) { "Join a world first" }.mainHandItem.copy().apply { count = 1 }
        files.add(SavedHead(name.trim(), tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }, ClientItemStorage.encode(stack)))
    }
    fun importHead(filename: String, name: String, tags: String) {
        val raw = files.importNbt(filename)
        files.add(SavedHead(name.trim(), tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }, raw))
    }
    fun exportHead(head: Head, filename: String) = files.exportNbt(filename, head.raw)
    fun give(head: Head) = ClientItemStorage.give(listOf(head.raw))
    private var pending: CompletableFuture<GameProfile>? = null
    fun resolve(value: String): CompletableFuture<GameProfile> {
        check(client.isSameThread) { "Client thread required" }
        requireNotNull(client.connection) { "Join a world first" }
        require(validPlayer(value)) { "Use Minecraft username (1..16 ASCII letters/digits/_) or dashed UUID" }
        val online = (client.connection?.onlinePlayers.orEmpty().map { it.profile } + client.gameProfile)
            .firstOrNull { it.name().equals(value, true) || it.id().toString().equals(value, true) }
        if (online != null) return CompletableFuture.completedFuture(online)
        check(pending?.isDone != false) { "Profile lookup already pending" }
        val resolver = client.services().profileResolver()
        val unresolved = if (value.contains('-')) ResolvableProfile.createUnresolved(UUID.fromString(value)) else ResolvableProfile.createUnresolved(value)
        // Native cache owns underlying request; timeout affects only this dependent future.
        val request = unresolved.resolveProfile(resolver)
                pending = request
                return request.thenApply { it }.orTimeout(5, TimeUnit.SECONDS)
    }
}
