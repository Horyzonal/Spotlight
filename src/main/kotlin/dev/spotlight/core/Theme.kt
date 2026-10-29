package dev.spotlight.core

import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.math.pow


enum class ThemeColor(val key: String, val defaultArgb: Long) {
    OVERLAY("overlay", 0x99060A12),
    LOCATION_BACKGROUND("locationBackground", 0xEE101722),
    SCREEN_BACKGROUND("screenBackground", 0xF0101722),
    PANEL_BACKGROUND("panelBackground", 0xFA101722),
    BUTTON_BACKGROUND("buttonBackground", 0xFF161D29),
    BUTTON_DISABLED("buttonDisabled", 0xFF19202B),
    ROW_SELECTED("rowSelected", 0xFF22334B),
    SEPARATOR("separator", 0xFF243146),
    BUTTON_HOVER("buttonHover", 0xFF293F5B),
    ROW_HOVER("rowHover", 0xFF2B3F59),
    BORDER("border", 0xFF415877),
    ACCENT("accent", 0xFF81B8FF),
    BUTTON_TEXT_DISABLED("buttonTextDisabled", 0xFF91A0B5),
    ROW_TEXT_DISABLED("rowTextDisabled", 0xFF999FA9),
    TEXT_MUTED("textMuted", 0xFF9DADC3),
    UNAVAILABLE("unavailable", 0xFFE0AA85),
    TEXT("text", 0xFFF2F5FC),
    STATUS_ERROR("statusError", 0xFFFFA49A),
    ERROR("error", 0xFFFFB4A9),
    SELECTOR_HINT("selectorHint", 0xFFFFC0A0),
    FAVORITE("favorite", 0xFFFFD27D),
}

enum class ThemeMetric(val key: String, val default: Int, val min: Int, val max: Int) {
    PALETTE_WIDTH("paletteWidth", 560, 160, 4096),
    MENU_WIDTH("menuWidth", 360, 160, 4096),
    EDITOR_WIDTH("editorWidth", 620, 160, 4096),
    SELECTOR_WIDTH("selectorWidth", 520, 160, 4096),
    CONFIRM_WIDTH("confirmWidth", 420, 160, 4096),
    LOCATION_WIDTH("locationWidth", 360, 160, 4096),
    PROMPT_WIDTH("promptWidth", 440, 160, 4096),
    ROW_HEIGHT("rowHeight", 30, 22, 80),
    COMPACT_ROW_HEIGHT("compactRowHeight", 24, 22, 80),
    WIDGET_HEIGHT("widgetHeight", 20, 16, 40),
    BORDER_THICKNESS("borderThickness", 1, 1, 4),
}

data class ThemeConfig(
    val colors: Map<Long, Int> = ThemeColor.entries.associate { it.defaultArgb to it.defaultArgb.toInt() },
    val metrics: Map<String, Int> = ThemeMetric.entries.associate { it.key to it.default },
    val font: String? = null,
) {
    fun color(defaultArgb: Long): Int = colors[defaultArgb and 0xFFFFFFFFL] ?: defaultArgb.toInt()

    
    fun textShadow(): Boolean {
        val background = color(ThemeColor.PANEL_BACKGROUND.defaultArgb)
        fun linear(shift: Int): Double {
            val channel = (background ushr shift and 0xFF) / 255.0
            return if (channel <= 0.04045) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linear(16) + 0.7152 * linear(8) + 0.0722 * linear(0) <= 0.179
    }

    fun metric(name: String, default: Int, min: Int = 1, max: Int = 4096): Int =
        (metrics[name.removePrefix("layout.")] ?: default).coerceIn(min, max)
}

data class ThemeReport(val config: ThemeConfig, val warnings: List<String> = emptyList(), val created: Boolean = false)

object Theme {
    val defaults = ThemeConfig()

    fun template(): String = buildString {
        appendLine("# Spotlight theme. Reloaded when a new palette opens; no restart needed.")
        appendLine("# Colors require #AARRGGBB (alpha included). Invalid entries use original defaults.")
        appendLine("# Optional resource-pack font identifier; blank uses Minecraft's current default font.")
        appendLine("font=")
        ThemeColor.entries.forEach { appendLine("colors.${it.key}=#${it.defaultArgb.toString(16).uppercase()}") }
        appendLine("# Layout values are pixels; viewport limits still apply.")
        ThemeMetric.entries.forEach { appendLine("# ${it.min}..${it.max}\nlayout.${it.key}=${it.default}") }
    }

    fun parse(text: String): ThemeReport {
        val properties = Properties()
        try {
            properties.load(text.reader())
        } catch (failure: IllegalArgumentException) {
            return ThemeReport(defaults, listOf("Malformed theme properties: ${failure.message}"))
        }
        val warnings = mutableListOf<String>()
        val colors = defaults.colors.toMutableMap()
        val metrics = defaults.metrics.toMutableMap()
        val colorKeys = ThemeColor.entries.associateBy { "colors.${it.key}" }
        val metricKeys = ThemeMetric.entries.associateBy { "layout.${it.key}" }
        var font: String? = null
        for (key in properties.stringPropertyNames().sorted()) {
            val value = properties.getProperty(key).trim()
            val color = colorKeys[key]
            val metric = metricKeys[key]
            when {
                color != null -> {
                    if (value.matches(Regex("#[0-9a-fA-F]{8}"))) {
                        colors[color.defaultArgb] = value.drop(1).toLong(16).toInt()
                    } else warnings += "$key: expected #AARRGGBB; using default"
                }
                metric != null -> {
                    val number = value.toIntOrNull()
                    if (number != null && number in metric.min..metric.max) metrics[metric.key] = number
                    else warnings += "$key: expected ${metric.min}..${metric.max}; using default"
                }
                key == "font" -> {
                    if (value.isEmpty()) continue
                    if (value.matches(Regex("[a-z0-9_.-]+:[a-z0-9/._-]+"))) font = value
                    else warnings += "font: expected namespace:path; using Minecraft default"
                }
                else -> warnings += "Unknown theme key: $key"
            }
        }
        return ThemeReport(ThemeConfig(colors.toMap(), metrics.toMap(), font), warnings.toList())
    }

    
    fun load(path: Path): ThemeReport {
        var created = false
        return try {
            if (Files.notExists(path)) {
                val parent = path.toAbsolutePath().parent
                Files.createDirectories(parent)
                val temporary = Files.createTempFile(parent, ".spotlight-theme-", ".tmp")
                try {
                    Files.writeString(temporary, template())
                    try {
                        
                        Files.createLink(path.toAbsolutePath(), temporary)
                        created = true
                    } catch (_: FileAlreadyExistsException) {
                        
                    }
                } finally {
                    Files.deleteIfExists(temporary)
                }
            }
            parse(Files.readString(path)).copy(created = created)
        } catch (failure: Exception) {
            ThemeReport(defaults, listOf("Cannot load theme $path: ${failure.message ?: failure.javaClass.simpleName}"), created)
        }
    }
}
