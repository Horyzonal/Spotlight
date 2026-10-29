package dev.spotlight.ui

import dev.spotlight.core.Theme
import dev.spotlight.core.ThemeConfig
import dev.spotlight.core.ThemeLibrary
import dev.spotlight.core.ThemeReport
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.resources.Identifier
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import org.slf4j.LoggerFactory


object SpotlightTheme {
    private val logger = LoggerFactory.getLogger("spotlight-theme")
    @Volatile private var config: ThemeConfig = Theme.defaults
    @Volatile private var fontId: Identifier? = null
    @Volatile private var shadow: Boolean = Theme.defaults.textShadow()

    private val themeLibrary by lazy { ThemeLibrary(FabricLoader.getInstance().configDir) }

    fun library(): ThemeLibrary = themeLibrary

    @Synchronized
    fun apply(id: String): ThemeReport {
        val report = library().apply(id)
        if (report.warnings.isNotEmpty()) return report.copy(config = config)
        return reload()
    }

    fun isDefault(): Boolean = config == Theme.defaults

    fun color(defaultArgb: Long): Int = config.color(defaultArgb)

    fun textShadow(): Boolean = shadow

    fun metric(name: String, default: Int, min: Int = 1, max: Int = 4096): Int =
        config.metric(name, default, min, max)

    
    fun font(): Identifier? = fontId

    fun open(): ThemeReport = reload()

    @Synchronized
    fun reload(): ThemeReport {
        val report = try {
            Theme.load(FabricLoader.getInstance().configDir.resolve("spotlight-theme.properties"))
        } catch (failure: Exception) {
            ThemeReport(Theme.defaults, listOf("Cannot resolve theme config: ${failure.message ?: failure.javaClass.simpleName}"))
        }
        config = report.config
        shadow = report.config.textShadow()
        fontId = report.config.font?.let(Identifier::tryParse)
        report.warnings.forEach { logger.warn("Spotlight theme: {}", it) }
        return report
    }
}

internal fun GuiGraphicsExtractor.themeText(font: Font, text: String, x: Int, y: Int, color: Int) =
    text(font, text, x, y, color, SpotlightTheme.textShadow())

internal fun GuiGraphicsExtractor.themeText(font: Font, text: Component, x: Int, y: Int, color: Int) =
    text(font, text, x, y, color, SpotlightTheme.textShadow())

internal fun GuiGraphicsExtractor.themeText(font: Font, text: FormattedCharSequence, x: Int, y: Int, color: Int) =
    text(font, text, x, y, color, SpotlightTheme.textShadow())

internal fun GuiGraphicsExtractor.themeCenteredText(font: Font, text: Component, x: Int, y: Int, color: Int) =
    themeText(font, text, x - font.width(text) / 2, y, color)

internal fun GuiGraphicsExtractor.themeCenteredText(font: Font, text: FormattedCharSequence, x: Int, y: Int, color: Int) =
    themeText(font, text, x - font.width(text) / 2, y, color)
