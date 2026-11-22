package dev.spotlight.ui

import com.mojang.blaze3d.platform.InputConstants
import dev.spotlight.core.Entry
import dev.spotlight.core.Fuzzy
import dev.spotlight.platform.notifySpotlight
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import kotlin.math.ceil
import kotlin.math.sign

private val emojis = listOf(
    "double_exclamation_mark" to "‼", "exclamation_question_mark" to "⁉", "trade_mark_sign" to "™", "information_source" to "ℹ",
    "left_right_arrow" to "↔", "watch" to "⌚", "hourglass" to "⌛", "eject_symbol" to "⏏",
    "black_right-pointing_double_triangle" to "⏩", "black_left-pointing_double_triangle" to "⏪", "black_right-pointing_double_triangle_with_vertical_bar" to "⏭", "black_left-pointing_double_triangle_with_vertical_bar" to "⏮",
    "black_right-pointing_triangle_with_double_vertical_bar" to "⏯", "hourglass_with_flowing_sand" to "⏳", "double_vertical_bar" to "⏸", "black_square_for_stop" to "⏹",
    "black_circle_for_record" to "⏺", "circled_latin_capital_letter_m" to "Ⓜ", "black_right-pointing_triangle" to "▶", "black_left-pointing_triangle" to "◀",
    "black_sun_with_rays" to "☀", "cloud" to "☁", "umbrella" to "☂", "snowman" to "☃",
    "comet" to "☄", "ballot_box_with_check" to "☑", "umbrella_with_rain_drops" to "☔", "skull_and_crossbones" to "☠",
    "peace_symbol" to "☮", "yin_yang" to "☯", "white_frowning_face" to "☹", "white_smiling_face" to "☺",
    "female_sign" to "♀", "male_sign" to "♂", "black_spade_suit" to "♠", "black_club_suit" to "♣",
    "black_heart_suit" to "♥", "black_diamond_suit" to "♦", "anchor" to "⚓", "crossed_swords" to "⚔",
    "alembic" to "⚗", "warning_sign" to "⚠", "high_voltage_sign" to "⚡", "snowman_without_snow" to "⛄",
    "thunder_cloud_and_rain" to "⛈", "pick" to "⛏", "black_scissors" to "✂", "envelope" to "✉",
    "heavy_check_mark" to "✔", "snowflake" to "❄", "cross_mark" to "❌", "heavy_heart_exclamation_mark_ornament" to "❣",
    "heavy_black_heart" to "❤", "white_medium_star" to "⭐", "water_wave" to "🌊", "cloud_with_rain" to "🌧",
    "meat_on_bone" to "🍖", "fishing_pole_and_fish" to "🎣", "bow_and_arrow" to "🏹", "bell" to "🔔",
    "fire" to "🔥", "trident_emblem" to "🔱", "dagger_knife" to "🗡", "shield" to "🛡",
    "test_tube" to "🧪", "axe" to "🪓", "bucket" to "🪣",
)
internal fun emojiEntries(): List<Entry> = emojis.map { (name, emoji) -> Entry(name, name.replace('_', ' '), emoji) }

class EmojiScreen(private val parent: Screen?, private val onSelect: ((String) -> Unit)? = null) : Screen(catalogText("Emoji")) {
    private val entries = emojiEntries()
    private var results = emptyList<Entry>()
    private val cells = mutableListOf<Button>()
    private lateinit var search: EditBox
    private var query = ""
    private var selected = 0
    private var offset = 0
    private var columns = 1
    private var visibleRows = 1
    private var cellWidth = 1
    private var left = 0
    private var top = 0
    private var panelWidth = 0
    private var panelHeight = 0
    private val rowStep = 42
    private val visibleCount get() = columns * visibleRows

    override fun isPauseScreen() = false

    override fun init() {
        cells.clear()
        panelWidth = minOf(440, (width - 16).coerceAtLeast(96))
        panelHeight = minOf(320, (height - 16).coerceAtLeast(160))
        left = (width - panelWidth) / 2
        top = (height - panelHeight) / 2
        columns = ((panelWidth - 24) / 88).coerceAtLeast(1)
        cellWidth = (panelWidth - 24) / columns
        visibleRows = ((panelHeight - 106) / rowStep).coerceAtLeast(1)
        search = addRenderableWidget(SpotlightEditBox(font, left + 12, top + 12, panelWidth - 24, 20, catalogText("Search emoji names")))
        search.setMaxLength(128)
        search.setHint(catalogText("Search emoji names"))
        search.value = query
        search.setResponder { query = it; selected = 0; offset = 0; refresh() }
        addRenderableWidget(catalogButton("Back", left + 12, top + panelHeight - 32, panelWidth - 24) { onClose() })
        refresh()
        setInitialFocus(search)
    }

    private fun maxOffset() = ((ceil(results.size.toDouble() / columns).toInt() - visibleRows).coerceAtLeast(0)) * columns

    private fun refresh() {
        cells.forEach(::removeWidget)
        cells.clear()
        results = Fuzzy.search(query, entries, limit = entries.size)
        selected = selected.coerceIn(0, (results.size - 1).coerceAtLeast(0))
        offset = offset.coerceIn(0, maxOffset())
        results.drop(offset).take(visibleCount).forEachIndexed { index, entry ->
            val x = left + 12 + index % columns * cellWidth
            val y = top + 44 + index / columns * rowStep
            val name = (if (offset + index == selected) "• " else "") + entry.title
            cells += addRenderableWidget(object : Button(x, y, cellWidth - 4, 36,
                catalogText("${entry.title} ${entry.detail}"), OnPress { copy(entry.id) }, CreateNarration { it.get() }) {
                override fun extractContents(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
                    val pose = graphics.pose()
                    pose.pushMatrix()
                    pose.translate(x + this.width / 2f, y + 3f)
                    pose.scale(1.5f, 1.5f)
                    graphics.themeCenteredText(font, net.minecraft.network.chat.Component.literal(entry.detail), 0, 0, SpotlightTheme.color(0xFFF2F5FC))
                    pose.popMatrix()
                    graphics.themeCenteredText(font, catalogClip(font, name, this.width - 6), x + this.width / 2, y + 24,
                        SpotlightTheme.color(if (isHovered || isFocused) 0xFF81B8FF else 0xFF9DADC3))
                }
            })
        }
        search.message = catalogText("Search emoji names. ${results.size} matches. ${results.getOrNull(selected)?.let { "${it.title} ${it.detail}" } ?: "No matches"}")
        setFocused(search)
        search.isFocused = true
    }

    private fun move(step: Int, cycle: Boolean = false) {
        if (results.isEmpty()) return
        selected = if (cycle) Math.floorMod(selected + step, results.size) else (selected + step).coerceIn(0, results.lastIndex)
        if (selected < offset) offset = selected / columns * columns
        if (selected >= offset + visibleCount) offset = (selected / columns - visibleRows + 1) * columns
        refresh()
    }

    private fun copy(id: String? = results.getOrNull(selected)?.id) {
        val entry = entries.find { it.id == id } ?: return
        if (onSelect != null) {
            onSelect(entry.detail)
            onClose()
        } else {
            minecraft.keyboardHandler.clipboard = entry.detail
            notifySpotlight("Copied ${entry.detail} to clipboard")
        }
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.isEscape) { onClose(); return true }
        if (event.isConfirmation && !event.hasAltDown()) { copy(); return true }
        if (!primaryModifier(event) && !event.hasAltDown()) {
            if (event.isDown || event.isUp) { move(if (event.isDown) columns else -columns); return true }
            if (event.isLeft || event.isRight) { move(if (event.isRight) 1 else -1); return true }
            if (event.key() == InputConstants.KEY_TAB) { move(if (event.hasShiftDown()) -1 else 1, cycle = true); return true }
        }
        return super.keyPressed(event)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        if (vertical != 0.0 && mouseX >= left && mouseX < left + panelWidth && mouseY >= top + 44 && mouseY < top + panelHeight - 38) {
            offset = (offset - sign(vertical).toInt() * columns).coerceIn(0, maxOffset())
            refresh()
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical)
    }

    override fun onClose() { minecraft.gui.setScreen(parent) }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(left, top, left + panelWidth, top + panelHeight, SpotlightTheme.color(0xFF161D29))
        graphics.outline(left, top, panelWidth, panelHeight, SpotlightTheme.color(0xFF415877))
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        if (results.isEmpty()) graphics.themeText(font, catalogText("No matching emoji"), left + 12, top + 52, SpotlightTheme.color(0xFF9DADC3))
        graphics.themeCenteredText(font, catalogText("↑↓←→ Select · Enter Copy · Scroll Browse"), width / 2, top + panelHeight - 48, SpotlightTheme.color(0xFF9DADC3))
    }
}
