package dev.spotlight.platform

import com.mojang.blaze3d.platform.InputConstants
import dev.spotlight.ui.SpotlightScreen
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.RegisterKeyMappingsEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.TickEvent
import net.minecraftforge.fml.DistExecutor
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import thedarkcolour.kotlinforforge.forge.MOD_BUS

@Mod("spotlight")
class SpotlightClient {
    init {
        DistExecutor.safeRunWhenOn(Dist.CLIENT) { ClientHooks }
    }

    companion object {
        @JvmStatic
        fun shouldReplaceCommandKey(): Boolean = Spotlight.spotlightSettings?.replaceCommandKey == true
    }

    private object ClientHooks : DistExecutor.SafeRunnable {
        private lateinit var key: KeyMapping

        override fun run() {
            key = KeyMapping("key.spotlight.open", InputConstants.Type.KEYSYM, InputConstants.KEY_GRAVE,
                "key.category.spotlight.controls")
            MOD_BUS.addListener<RegisterKeyMappingsEvent> { it.register(key) }
            MOD_BUS.addListener<FMLClientSetupEvent> { event ->
                event.enqueueWork {
                    ExtendedOptions.initialize()
                    registerBuiltIns()
                    Spotlight.initializeSpotlightSettings()
                    Spotlight.settings()
                    Spotlight.locations()
                    Spotlight.loadExtensions()
                }
            }
            BlockHitActions.initialize()
            FindTargets.initialize()
            RegionSelection.initialize()
            MinecraftForge.EVENT_BUS.addListener<net.minecraftforge.client.event.ScreenEvent.Init.Post> { event ->
                dev.spotlight.ui.InventoryStorageButtons.attach(event)
            }
            MinecraftForge.EVENT_BUS.addListener<TickEvent.ClientTickEvent> { event ->
                if (event.phase == TickEvent.Phase.END) {
                    LocationActions.tick()
                    FindTargets.tick()
                    RegionSelection.tick()
                    CommandHotkeys.tick()
                    ClientItemStorage.tick()
                    while (key.consumeClick()) {
                        if (Minecraft.getInstance().screen !is SpotlightScreen) Spotlight.open()
                    }
                }
            }
        }
    }
}
