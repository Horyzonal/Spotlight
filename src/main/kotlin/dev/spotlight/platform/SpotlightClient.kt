package dev.spotlight.platform

import com.mojang.blaze3d.platform.InputConstants
import dev.spotlight.ui.SpotlightScreen
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.common.NeoForge


@Mod(value = "spotlight", dist = [Dist.CLIENT])
class SpotlightClient(modEventBus: IEventBus) {
    companion object {
        @JvmStatic
        fun shouldReplaceCommandKey(): Boolean = Spotlight.spotlightSettings?.replaceCommandKey == true
    }
    private val key by lazy {
        KeyMapping("key.spotlight.open", InputConstants.Type.KEYSYM, InputConstants.KEY_GRAVE,
            "key.category.spotlight.controls")
    }

    init {
        modEventBus.addListener { event: RegisterKeyMappingsEvent -> event.register(key) }
        modEventBus.addListener { event: FMLClientSetupEvent -> event.enqueueWork { initialize() } }
        NeoForge.EVENT_BUS.addListener { _: ClientTickEvent.Post -> tick() }
        NeoForge.EVENT_BUS.addListener { event: ScreenEvent.Init.Post -> dev.spotlight.ui.InventoryStorageButtons.attach(event) }
    }

    private fun initialize() {
        ExtendedOptions.initialize()
        BlockHitActions.initialize()
        registerBuiltIns()
        Spotlight.initializeSpotlightSettings()
        Spotlight.settings()
        Spotlight.locations()
        FindTargets.initialize()
        RegionSelection.initialize()
        Spotlight.loadExtensions()
    }

    private fun tick() {
        val client = Minecraft.getInstance()
        LocationActions.tick()
        FindTargets.tick()
        BackgroundTasks.tick()
        RegionSelection.tick()
        CommandHotkeys.tick()
        ClientItemStorage.tick()
        while (key.consumeClick()) {
            if (client.screen !is SpotlightScreen) Spotlight.open()
        }
    }
}
