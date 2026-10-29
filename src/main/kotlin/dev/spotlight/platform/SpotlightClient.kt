package dev.spotlight.platform

import com.mojang.blaze3d.platform.InputConstants
import dev.spotlight.ui.SpotlightScreen
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.KeyMapping
import net.minecraft.resources.Identifier


class SpotlightClient : ClientModInitializer {
    companion object {
        @JvmStatic
        fun shouldReplaceCommandKey(): Boolean = Spotlight.spotlightSettings?.replaceCommandKey == true
    }
    override fun onInitializeClient() {
        ExtendedOptions.initialize()
        BlockHitActions.initialize()
        registerBuiltIns()
        Spotlight.initializeSpotlightSettings()
        Spotlight.settings()
        Spotlight.locations()
        FindTargets.initialize()
        RegionSelection.initialize()
        FabricLoader.getInstance().getEntrypoints("spotlight", SpotlightExtension::class.java)
            .forEach { it.registerCommands() }
        val key = KeyMappingHelper.registerKeyMapping(KeyMapping(
            "key.spotlight.open", InputConstants.Type.KEYBOARD, InputConstants.KEY_GRAVE,
            KeyMapping.Category.register(Identifier.parse("spotlight:controls")),
        ))
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            LocationActions.tick()
            FindTargets.tick()
            RegionSelection.tick()
            CommandHotkeys.tick()
            while (key.consumeClick()) {
                if (client.gui.screen() !is SpotlightScreen) Spotlight.open()
            }
        }
    }
}
