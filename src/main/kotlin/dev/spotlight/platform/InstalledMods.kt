package dev.spotlight.platform

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.screens.Screen
import java.net.URI

object InstalledMods {
    data class Mod(val id: String, val name: String, val version: String, val description: String, val homepage: URI?, val issues: URI?)

    fun search(query: String): List<Mod> = FabricLoader.getInstance().allMods.map { container ->
        val metadata = container.metadata
        Mod(metadata.id, metadata.name, metadata.version.friendlyString, metadata.description,
            metadata.contact.get("homepage").orElse(null)?.let(::webLink),
            metadata.contact.get("issues").orElse(null)?.let(::webLink))
    }.filter { mod -> listOf(mod.name, mod.id, mod.version, mod.description).any { it.contains(query.trim(), ignoreCase = true) } }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    fun webLink(value: String): URI? = runCatching { URI(value) }.getOrNull()?.takeIf {
        it.scheme in listOf("https", "http") && !it.host.isNullOrBlank() && it.rawUserInfo == null
    }

    val configStatus: String get() = if (FabricLoader.getInstance().isModLoaded("modmenu"))
        "Settings available only for mods exposing Mod Menu's public config API"
    else "No config API: install Mod Menu for supported native mod settings"

    fun configScreen(id: String, parent: Screen): Screen? {
        val loader = FabricLoader.getInstance()
        if (!loader.isModLoaded("modmenu")) return null
        val api = Class.forName("com.terraformersmc.modmenu.api.ModMenuApi")
        val factoryApi = Class.forName("com.terraformersmc.modmenu.api.ConfigScreenFactory")
        val method = api.getMethod("getModConfigScreenFactory")
        val create = factoryApi.getMethod("create", Screen::class.java)
        for (container in loader.getEntrypointContainers("modmenu", Any::class.java)) {
            if (container.provider.metadata.id != id) continue
            val entrypoint = container.entrypoint
            if (!api.isInstance(entrypoint)) continue
            val factory = method.invoke(entrypoint) ?: continue
            if (!factoryApi.isInstance(factory)) continue
            val screen = create.invoke(factory, parent)
            if (screen is Screen) return screen
        }
        return null
    }
}
