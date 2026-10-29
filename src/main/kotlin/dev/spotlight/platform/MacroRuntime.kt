package dev.spotlight.platform

import dev.spotlight.core.Macro
import dev.spotlight.core.MacroArgumentType
import dev.spotlight.core.MacroStore
import dev.spotlight.core.MacroTemplates
import dev.spotlight.ui.MacroRunScreen
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft

import java.security.MessageDigest
import java.util.Locale

object MacroRuntime {
    private val savedStore by lazy {
        MacroStore(FabricLoader.getInstance().configDir.resolve("spotlight-macros.dat"))
    }

    fun store(): MacroStore = savedStore

    fun variables(template: String): Map<String, String> {
        val referenced = linkedSetOf<String>()
        val arguments = MacroTemplates.arguments(template).associate {
            it.name to if (it.type == MacroArgumentType.LOCATION) "0 0 0" else "0"
        }
        
        MacroTemplates.expand(template, arguments, object : Map<String, String> by emptyMap() {
            override fun get(key: String): String {
                referenced += key
                return "0"
            }
        })
        val client by lazy { Minecraft.getInstance() }
        val targets by lazy { MacroTargeting.capture(client, "LookEntity" in referenced) }
        return referenced.associateWith { name ->
            when (name) {
                "Clipboard" -> client.keyboardHandler.clipboard
                "Position" -> {
                    val player = requireNotNull(client.player) { "Position requires a player; join a world first" }
                    String.format(Locale.ROOT, "%.2f %.2f %.2f", player.x, player.y, player.z)
                }
                "Player" -> requireNotNull(client.player) { "Player requires a player; join a world first" }.gameProfile.name
                "Dimension" -> Spotlight.dimension()
                "LookEntity" -> {
                    requireNotNull(targets.entity) { "No loaded entity along look ray within 256 blocks" }
                }
                "Look" -> {
                    val pos = requireNotNull(targets.block) { "No loaded block along look ray within 256 blocks" }
                    String.format(Locale.ROOT, "%d %d %d", pos.x, pos.y, pos.z)
                }
                else -> error("Unknown macro variable: $name")
            }
        }
    }

    fun commands(): List<SpotlightCommand> = store().list().map(::command)

    fun command(macro: Macro): SpotlightCommand {
        val id = MessageDigest.getInstance("SHA-256").digest(macro.id.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(Locale.ROOT, it) }
        return SpotlightCommand("macro:$id", "${macro.name} [Macro]", macro.template, unavailable = ::worldUnavailable) {
            run(macro)
        }
    }

    fun run(macro: Macro) {
        val client = Minecraft.getInstance()
        client.setScreen(MacroRunScreen(client.screen, macro))
    }
}
