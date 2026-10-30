package dev.spotlight.platform

import dev.spotlight.core.Entry
import dev.spotlight.core.Preferences
import dev.spotlight.core.CommandSettings
import dev.spotlight.core.SpotlightSettings
import dev.spotlight.core.LocationStore
import dev.spotlight.core.LocationAdapter
import net.minecraft.network.chat.Component
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.world.level.storage.LevelResource
import java.security.MessageDigest
import java.util.UUID
import dev.spotlight.ui.SpotlightScreen
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft

import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.Collections

enum class CommandInput { NONE, ITEM, ITEM_QUANTITY, NUMBER, ENTITY, BLOCK, LOCATION }

data class CommandContext(val itemId: String? = null, val quantity: Int = 1, val text: String = "")

data class SpotlightCommand(
    val id: String,
    val title: String,
    val description: String = "",
    val input: CommandInput = CommandInput.NONE,
    val unavailable: () -> String? = { null },
    val execute: (CommandContext) -> Unit,
)

fun interface SpotlightExtension {
    fun registerCommands()
}

object Spotlight {
    private val registeredCommands = mutableListOf<SpotlightCommand>()
    val commands: List<SpotlightCommand> = Collections.unmodifiableList(registeredCommands)
    private val savedPreferences by lazy {
        Preferences(FabricLoader.getInstance().configDir.resolve("spotlight.properties"))
    }

    fun register(command: SpotlightCommand) {
        require(ResourceLocation.tryParse(command.id) != null && ':' in command.id) { "Command ID must be namespaced: ${command.id}" }
        require(command.title.isNotBlank()) { "Command title must not be blank" }
        require(commands.none { it.id == command.id }) { "Duplicate Spotlight command: ${command.id}" }
        registeredCommands.add(command)
    }

    fun items(): List<Entry> = BuiltInRegistries.ITEM.filter { it != Items.AIR }.map { item ->
        val id = BuiltInRegistries.ITEM.getKey(item).toString()
        Entry("item:$id", ItemStack(item).hoverName.string, id, id)
    }

    fun stack(id: String): ItemStack {
        val key = requireNotNull(ResourceLocation.tryParse(id)) { "Invalid item ID: $id" }
        require(BuiltInRegistries.ITEM.containsKey(key)) { "Unknown item: $id" }
        val item = requireNotNull(BuiltInRegistries.ITEM.getValue(key)) { "Unknown item: $id" }
        require(item != Items.AIR) { "Air is not an item action target" }
        return ItemStack(item)
    }

    private val savedSettings by lazy { loadStore("commands", ::CommandSettings) }
    private val savedLocations by lazy { loadStore("locations", ::LocationStore) }
    var requestedCommandId: String? = null

    var requestedQuery: String? = null

    var spotlightSettings: SpotlightSettings? = null
        private set
    var spotlightSettingsError: String? = null
        private set

    internal fun initializeSpotlightSettings() {
        try {
            spotlightSettings = SpotlightSettings(FabricLoader.getInstance().configDir.resolve("spotlight/settings.properties"))
        } catch (failure: Exception) {
            spotlightSettingsError = "Cannot load Spotlight settings: ${failure.message}. Original preserved."
            notifySpotlight(spotlightSettingsError!!)
        }
    }

    fun settings(): CommandSettings = savedSettings

    fun builtinAlias(text: String): String? = builtInAliases[text.trim().lowercase(java.util.Locale.ROOT)]
    fun resolveAlias(text: String): String? = fallbackAlias(text, settings().aliases)
    fun commandAliases(commandId: String): List<String> = aliasesForCommand(commandId, settings().aliases)
    fun resolveCommandAlias(query: String): SpotlightCommand? = resolveAlias(query)?.let { id ->
        allCommands().firstOrNull { it.id == id }
    }
    fun locations(): LocationStore = savedLocations
    fun entities(): List<Entry> = entityEntries()
    fun blocks(): List<Entry> = BuiltInRegistries.BLOCK.map { block ->
        val id = BuiltInRegistries.BLOCK.getKey(block).toString()
        Entry("block:$id", block.name.string, id, id)
    }
    fun locationEntries(): List<Entry> = if (worldUnavailable() != null) emptyList() else locations().list(scope()).map {
        Entry("location:${it.id}", it.name, "${it.dimension} · ${it.x}, ${it.y}, ${it.z}", it.id)
    }

    fun scope(): String {
        val client = Minecraft.getInstance()
        check(worldUnavailable() == null) { "Join a world first" }
        val identity = client.singleplayerServer?.let {
            "world:" + it.getWorldPath(LevelResource.ROOT).toRealPath().toString()
        } ?: "server:" + requireNotNull(client.currentServer) { "Server identity unavailable" }.ip.trim().lowercase(java.util.Locale.ROOT)
        return MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun dimension(): String = requireNotNull(Minecraft.getInstance().level) { "Join a world first" }.dimension().location().toString()

    fun executeSlash(text: String) {
        val command = validatedSlash(text)
        requireNotNull(Minecraft.getInstance().connection) { "Join a world first" }.sendCommand(command)
    }

    fun dispatch(command: SpotlightCommand) {
        command.unavailable()?.let { error(it) }
        if (command.input == CommandInput.NONE) command.execute(CommandContext()) else openCommand(command.id)
    }

    fun macroCommands(): List<SpotlightCommand> = MacroRuntime.commands()
    fun allCommands(): List<SpotlightCommand> = commands + macroCommands() + serverCommands()

    fun openCommand(id: String) {
        val command = allCommands().firstOrNull { it.id == id } ?: error("Unknown command: $id")
        if (id.startsWith("server:") || id.startsWith("macro:")) {
            command.unavailable()?.let { error(it) }
            command.execute(CommandContext())
        } else {
            requestedCommandId = id
            open()
        }
    }

    fun registerLocationAdapter(adapter: LocationAdapter) = LocationAdapters.register(adapter)
    fun syncLocations(): String = LocationAdapters.sync()
    fun locationSyncStatus(): String = LocationAdapters.status()

    fun preferences(): Preferences = savedPreferences

    fun open(parent: Screen? = Minecraft.getInstance().screen) {
        Minecraft.getInstance().setScreen(SpotlightScreen(parent))
    }

    fun serverCommands(): List<SpotlightCommand> {
        val connection = Minecraft.getInstance().connection ?: return emptyList()
        return connection.commands.root.children.mapNotNull { node ->
            val name = node.name
            if (!name.matches(Regex("[a-zA-Z0-9_:.+-]+"))) null
            else SpotlightCommand("server:$name", "/$name", "Execute server command directly via palette", unavailable = ::worldUnavailable) {
                requestedCommandId = null
                requestedQuery = "/$name "
                open()
            }
        }.sortedBy { it.title }
    }
}


internal fun notifySpotlight(message: String) {
    val client = Minecraft.getInstance()
    SystemToast.add(client.toastManager, SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
        Component.literal("Spotlight"), Component.literal(message))
}

private fun <T> loadStore(name: String, create: (java.nio.file.Path) -> T): T {
    val directory = FabricLoader.getInstance().configDir
    return try { create(directory.resolve("spotlight-$name.dat")) } catch (failure: Exception) {
        notifySpotlight("Cannot load $name: ${failure.message}. Original preserved; recovery store used.")
        create(directory.resolve("spotlight-$name-recovery-${UUID.randomUUID()}.dat"))
    }
}

internal val builtInAliases: Map<String, String> = Collections.unmodifiableMap(linkedMapOf(
    "gms" to "spotlight:gamemode_survival", "gm0" to "spotlight:gamemode_survival",
    "gmc" to "spotlight:gamemode_creative", "gm1" to "spotlight:gamemode_creative",
    "gma" to "spotlight:gamemode_adventure", "gm2" to "spotlight:gamemode_adventure",
    "gmsp" to "spotlight:gamemode_spectator", "gm3" to "spotlight:gamemode_spectator",
))


internal fun fallbackAlias(text: String, user: Map<String, String>): String? {
    val token = text.trim().lowercase(java.util.Locale.ROOT)
    return user.entries.firstOrNull { it.value == token }?.key ?: Spotlight.builtinAlias(token)
}

internal fun aliasesForCommand(commandId: String, user: Map<String, String>): List<String> =
    (listOfNotNull(user[commandId]) + builtInAliases.filter { (alias, id) ->
        id == commandId && alias !in user.values
    }.keys).distinct()

internal fun worldUnavailable(): String? =
    if (Minecraft.getInstance().player == null || Minecraft.getInstance().connection == null) "Join a world first" else null
