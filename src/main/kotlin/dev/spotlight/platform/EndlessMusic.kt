package dev.spotlight.platform

import dev.spotlight.core.MusicPreferences
import net.minecraftforge.fml.loading.FMLPaths

object EndlessMusic {
    private val preferences by lazy {
        MusicPreferences(FMLPaths.CONFIGDIR.get().resolve("spotlight/music.properties"))
    }

    val enabled: Boolean get() = preferences.endless
    val error: String? get() = preferences.error

    fun setEnabled(value: Boolean): Boolean = preferences.setEndless(value)
}
