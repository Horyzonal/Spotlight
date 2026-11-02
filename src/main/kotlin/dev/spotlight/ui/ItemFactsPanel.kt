package dev.spotlight.ui

import dev.spotlight.platform.ItemFacts
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.FittingMultiLineTextWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

internal class ItemFactsPanel(font: Font, x: Int, y: Int, width: Int, height: Int, private val compact: Boolean = false) :
    FittingMultiLineTextWidget(x, y, width, height, Component.empty(), font) {
    private var stack = ItemStack.EMPTY

    fun select(value: ItemStack) {
        if (value === stack) return
        stack = value
        visible = !value.isEmpty
        message = if (value.isEmpty) Component.empty() else Component.empty()
            .append(if (compact) "" else "\n\n\n\n").append(value.hoverName).append("\n${BuiltInRegistries.ITEM.getKey(value.item)}\n")
            .also { text -> ItemFacts.describe(value).forEach { text.append(it).append("\n") } }
            .withColor(SpotlightTheme.color(0xFFF2F5FC))
        setScrollAmount(0.0)
    }

    override fun extractContents(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractContents(graphics, mouseX, mouseY, delta)
        if (compact || stack.isEmpty) return
        graphics.enableScissor(x + 1, y + 1, right - 1, bottom - 1)
        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate((x + 8).toFloat(), (y + 8 - scrollAmount()).toFloat())
        pose.scale(2f, 2f)
        graphics.item(stack, 0, 0)
        pose.popMatrix()
        graphics.disableScissor()
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor) {
        graphics.fill(x, y, right, bottom, SpotlightTheme.color(0xFA101722))
        graphics.outline(x, y, width, height, SpotlightTheme.color(0xFF415877))
    }
}

internal class ItemDetailsScreen(private val parent: Screen, private val stack: ItemStack) :
    Screen(Component.literal("Item details")) {
    override fun isPauseScreen() = false

    override fun init() {
        val panelWidth = minOf(480, (width - 16).coerceAtLeast(1))
        val panelHeight = (height - 56).coerceAtLeast(1)
        val panel = addRenderableWidget(ItemFactsPanel(font, (width - panelWidth) / 2, 8, panelWidth, panelHeight))
        panel.select(stack)
        addRenderableWidget(spotlightButton("Back", (width - minOf(120, panelWidth)) / 2,
            height - 28, minOf(120, panelWidth)) { onClose() })
        setInitialFocus(panel)
    }

    override fun onClose() { minecraft.gui.setScreen(parent) }
}
