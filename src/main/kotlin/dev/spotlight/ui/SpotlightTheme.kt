package dev.spotlight.ui

import dev.spotlight.core.Theme
import dev.spotlight.core.ThemeConfig
import dev.spotlight.core.ThemeLibrary
import dev.spotlight.core.ThemeReport
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.resources.ResourceLocation
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import org.slf4j.LoggerFactory


object SpotlightTheme {
    private val logger = LoggerFactory.getLogger("spotlight-theme")
    @Volatile private var config: ThemeConfig = Theme.defaults
    @Volatile private var fontId: ResourceLocation? = null
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


    fun font(): ResourceLocation? = fontId

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
        fontId = report.config.font?.let(ResourceLocation::tryParse)
        report.warnings.forEach { logger.warn("Spotlight theme: {}", it) }
        return report
    }
}

// ponytail: 1.20.1 Style has no shadow control; delegate native drawing and metrics until it does.
internal fun catalogFont(font: Font): Font = object : Font({ error("Font operations must delegate to native font") }, false) {
    override fun width(text: String) = font.width(text)
    override fun width(text: net.minecraft.network.chat.FormattedText) = font.width(text)
    override fun width(text: FormattedCharSequence) = font.width(text)
    override fun plainSubstrByWidth(text: String, width: Int, reverse: Boolean) = font.plainSubstrByWidth(text, width, reverse)
    override fun plainSubstrByWidth(text: String, width: Int) = font.plainSubstrByWidth(text, width)
    override fun substrByWidth(text: net.minecraft.network.chat.FormattedText, width: Int) = font.substrByWidth(text, width)
    override fun wordWrapHeight(text: String, width: Int) = font.wordWrapHeight(text, width)
    override fun wordWrapHeight(text: net.minecraft.network.chat.FormattedText, width: Int) = font.wordWrapHeight(text, width)
    override fun split(text: net.minecraft.network.chat.FormattedText, width: Int) = font.split(text, width)
    override fun getSplitter() = font.splitter
    override fun isBidirectional() = font.isBidirectional
    override fun bidirectionalShaping(text: String) = font.bidirectionalShaping(text)
    override fun drawInBatch8xOutline(text: FormattedCharSequence, x: Float, y: Float, color: Int, outline: Int,
        matrix: org.joml.Matrix4f, buffers: net.minecraft.client.renderer.MultiBufferSource, light: Int) =
        font.drawInBatch8xOutline(text, x, y, color, outline, matrix, buffers, light)
    override fun drawInBatch(text: String, x: Float, y: Float, color: Int, shadow: Boolean,
        matrix: org.joml.Matrix4f, buffers: net.minecraft.client.renderer.MultiBufferSource,
        mode: Font.DisplayMode, background: Int, light: Int): Int =
        font.drawInBatch(text, x, y, color, shadow && SpotlightTheme.textShadow(), matrix, buffers, mode, background, light)

    override fun drawInBatch(text: String, x: Float, y: Float, color: Int, shadow: Boolean,
        matrix: org.joml.Matrix4f, buffers: net.minecraft.client.renderer.MultiBufferSource,
        mode: Font.DisplayMode, background: Int, light: Int, bidi: Boolean): Int =
        font.drawInBatch(text, x, y, color, shadow && SpotlightTheme.textShadow(), matrix, buffers, mode, background, light, bidi)

    override fun drawInBatch(text: Component, x: Float, y: Float, color: Int, shadow: Boolean,
        matrix: org.joml.Matrix4f, buffers: net.minecraft.client.renderer.MultiBufferSource,
        mode: Font.DisplayMode, background: Int, light: Int): Int =
        font.drawInBatch(text, x, y, color, shadow && SpotlightTheme.textShadow(), matrix, buffers, mode, background, light)

    override fun drawInBatch(text: FormattedCharSequence, x: Float, y: Float, color: Int, shadow: Boolean,
        matrix: org.joml.Matrix4f, buffers: net.minecraft.client.renderer.MultiBufferSource,
        mode: Font.DisplayMode, background: Int, light: Int): Int =
        font.drawInBatch(text, x, y, color, shadow && SpotlightTheme.textShadow(), matrix, buffers, mode, background, light)
}

internal fun GuiGraphics.themeText(font: Font, text: String, x: Int, y: Int, color: Int) =
    drawString(font, text, x, y, color, SpotlightTheme.textShadow())

internal fun GuiGraphics.themeText(font: Font, text: Component, x: Int, y: Int, color: Int) =
    drawString(font, text, x, y, color, SpotlightTheme.textShadow())

internal fun GuiGraphics.themeText(font: Font, text: FormattedCharSequence, x: Int, y: Int, color: Int) =
    drawString(font, text, x, y, color, SpotlightTheme.textShadow())

internal fun GuiGraphics.themeCenteredText(font: Font, text: Component, x: Int, y: Int, color: Int) =
    themeText(font, text, x - font.width(text) / 2, y, color)

internal fun GuiGraphics.themeCenteredText(font: Font, text: FormattedCharSequence, x: Int, y: Int, color: Int) =
    themeText(font, text, x - font.width(text) / 2, y, color)
