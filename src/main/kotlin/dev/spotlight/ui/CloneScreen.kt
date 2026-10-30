package dev.spotlight.ui

import dev.spotlight.commands.regionCommandUnavailable
import dev.spotlight.platform.RegionSelection
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent

internal val cloneMasks = listOf("replace", "masked")
internal val cloneModes = listOf("normal", "force", "move")

class CloneScreen(private val parent: Screen?) : Screen(catalogText("clone")) {
    private var mask = "replace"
    private var mode = "normal"
    private var error: String? = null
    private var left = 0
    private var top = 0
    private var panelWidth = 1

    override fun isPauseScreen() = false

    override fun init() {
        panelWidth = minOf(SpotlightTheme.metric("selectorWidth", 520, 160, 4096), (width - 16).coerceAtLeast(1))
        left = (width - panelWidth) / 2
        top = ((height - 244) / 2).coerceAtLeast(8)
        lateinit var maskButton: Button
        maskButton = addRenderableWidget(catalogButton("Mask: $mask", left, top + 24, panelWidth) {
            mask = cloneMasks[(cloneMasks.indexOf(mask) + 1) % cloneMasks.size]
            maskButton.message = catalogText("Mask: $mask")
        })
        lateinit var modeButton: Button
        modeButton = addRenderableWidget(catalogButton("Behavior: $mode", left, top + 66, panelWidth) {
            mode = cloneModes[(cloneModes.indexOf(mode) + 1) % cloneModes.size]
            modeButton.message = catalogText("Behavior: $mode")
        })
        addRenderableWidget(catalogButton("Back (Esc)", left, top + 190, panelWidth / 3 - 2) { onClose() })
        val start = addRenderableWidget(catalogButton("Start selection (Enter / $primaryModifierLabel+Enter)",
            left + panelWidth / 3 + 2, top + 190, panelWidth * 2 / 3 - 2) { start() })
        setInitialFocus(start)
    }

    private fun start() {
        try {
            regionCommandUnavailable("clone")?.let { error(it) }
            RegionSelection.startClone(mask, mode)
        } catch (failure: Exception) {
            error = failure.message ?: "Cannot start clone selection"
            triggerImmediateNarration(true)
        }
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.isEscape) { onClose(); return true }
        if (event.isConfirmation && primaryModifier(event) && !event.hasAltDown()) { start(); return true }
        return super.keyPressed(event)
    }

    override fun onClose() { minecraft.setScreen(parent) }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, SpotlightTheme.color(0xF0101722))
        super.render(graphics, mouseX, mouseY, delta)
        val lines = listOf(
            4 to "clone · choose mask and behavior",
            48 to if (mask == "masked") "Copy non-air blocks only." else "Copy all blocks, including air.",
            90 to when (mode) {
                "force" -> "Allow source and destination overlap."
                "move" -> "Move blocks; clear source to air."
                else -> "Copy blocks; reject overlapping regions."
            },
            110 to "LMB first corner · RMB second · LMB destination",
            123 to "Destination is lowest X/Y/Z corner of copied region.",
            136 to "Enter sends command · Esc cancels selection",
            149 to "Existing flight mode only; server checks permissions.",
            172 to (error ?: "/clone <begin> <end> <destination> $mask $mode"),
        )
        lines.forEach { (y, text) ->
            graphics.themeText(font, catalogClip(font, text, panelWidth), left, top + y,
                SpotlightTheme.color(if (y == 172 && error != null) 0xFFFFB4A9 else 0xFFF2F5FC))
        }
        catalogHints(graphics, font, width, height, "Tab Next control · Enter Activate · $primaryModifierLabel+Enter Start · Esc Back")
    }
}
