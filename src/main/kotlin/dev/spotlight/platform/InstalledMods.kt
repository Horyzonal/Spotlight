package dev.spotlight.platform

import net.minecraft.client.gui.screens.Screen
import net.neoforged.fml.ModList
import net.neoforged.neoforge.client.gui.IConfigScreenFactory
import java.net.URI

object InstalledMods {
    data class Mod(val id: String, val name: String, val version: String, val description: String, val homepage: URI?, val issues: URI?)

    fun search(query: String): List<Mod> = ModList.get().mods.map { metadata ->
        Mod(metadata.modId, metadata.displayName, metadata.version.toString(), metadata.description,
            metadata.modURL.orElse(null)?.toString()?.let(::webLink),
            metadata.owningFile.config.getConfigElement<String>("issueTrackerURL").orElse(null)?.let(::webLink))
    }.filter { mod -> listOf(mod.name, mod.id, mod.version, mod.description).any { it.contains(query.trim(), ignoreCase = true) } }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    fun webLink(value: String): URI? = runCatching { URI(value) }.getOrNull()?.takeIf {
        it.scheme in listOf("https", "http") && !it.host.isNullOrBlank() && it.rawUserInfo == null
    }

    val configStatus: String get() = "Settings available for mods exposing NeoForge's native config screen API"

    fun hasConfigScreen(id: String): Boolean = ModList.get().getModContainerById(id)
        .flatMap { it.getCustomExtension(IConfigScreenFactory::class.java) }.isPresent

    fun configScreen(id: String, parent: Screen): Screen? {
        val container = ModList.get().getModContainerById(id).orElse(null) ?: return null
        return container.getCustomExtension(IConfigScreenFactory::class.java).orElse(null)?.createScreen(container, parent)
    }
}
