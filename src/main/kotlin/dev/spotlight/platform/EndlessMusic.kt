package dev.spotlight.platform

import dev.spotlight.core.MusicPreferences
import net.fabricmc.loader.api.FabricLoader

object EndlessMusic {
    private val preferences by lazy {
        MusicPreferences(FabricLoader.getInstance().configDir.resolve("spotlight/music.properties"))
    }

    val enabled: Boolean get() = preferences.endless
    val error: String? get() = preferences.error

    fun setEnabled(value: Boolean): Boolean = preferences.setEndless(value)
}
