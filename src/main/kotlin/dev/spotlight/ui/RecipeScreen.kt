package dev.spotlight.ui

import dev.spotlight.platform.CraftingRecipeView
import dev.spotlight.platform.CraftingRecipes
import dev.spotlight.platform.commandMatch
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.world.item.ItemStack


internal fun recipeIngredientAt(cell: Int, width: Int, height: Int, shapeless: Boolean, size: Int): Int {
    if (cell !in 0..8 || width !in 1..3 || height !in 1..3) return -1
    val index = if (shapeless) cell else {
        if (cell % 3 >= width || cell / 3 >= height) return -1
        cell / 3 * width + cell % 3
    }
    return if (index < size) index else -1
}

class RecipeScreen(private val parent: Screen?) : Screen(catalogText("Crafting recipes")) {
    private var opened = false
    private var recipes = emptyList<CraftingRecipeView>()
    private var matches = emptyList<CraftingRecipeView>()
    private var selected = 0
    private var query = ""
    private var ticks = 0L
    private var missing = emptyList<Boolean>()
    private var missingCount = 0
    private var sourceStatus = ""
    private lateinit var search: EditBox
    private lateinit var copy: Button
    private val rows = mutableListOf<Button>()
    private var listX = 0
    private var listWidth = 0
    private var rowCount = 4
    private var detailX = 0
    private var detailY = 0
    private var detailWidth = 0
    private var footerY = 0
    private val current get() = matches.getOrNull(selected)

    override fun isPauseScreen() = false

    override fun init() {
        rows.clear()
        val margin = minOf(8, width / 8, height / 8)
        val available = (width - margin * 2).coerceAtLeast(1)
        val panelWidth = minOf(760, available)
        val left = (width - panelWidth) / 2
        footerY = (height - margin - 20).coerceAtLeast(0)
        search = addRenderableWidget(SpotlightEditBox(font, left, margin, panelWidth, minOf(20, height.coerceAtLeast(1)), catalogText("Search crafting recipes")))
        search.value = query
        search.setResponder { query = it; filter() }
        val top = margin + 26
        val wide = panelWidth >= 480
        listX = left
        listWidth = if (wide) panelWidth / 2 - 4 else panelWidth
        rowCount = if (wide) ((footerY - 48 - top) / 22).coerceIn(1, 12) else 4
        detailX = if (wide) left + listWidth + 8 else left
        detailY = if (wide) top else top + rowCount * 22 + 4
        detailWidth = if (wide) panelWidth - listWidth - 8 else panelWidth
        val rowStep = if (wide) 22 else if (height < 270) 14 else 18
        if (!wide) detailY = top + rowCount * rowStep + 4
        repeat(rowCount) { row ->
            rows += addRenderableWidget(catalogButton("", listX, top + row * rowStep, listWidth, rowStep - 1) {
                select(selected / rowCount * rowCount + row)
            })
        }
        val buttonWidth = (panelWidth / 3).coerceAtLeast(1)
        copy = addRenderableWidget(catalogButton("Copy recipe ID", left, footerY, buttonWidth) {
            current?.recipeIds?.minOrNull()?.let { minecraft.keyboardHandler.clipboard = it }
        })
        addRenderableWidget(catalogButton("Reload", left + buttonWidth, footerY, buttonWidth) { reload() })
        addRenderableWidget(catalogButton("Back (Esc)", left + buttonWidth * 2, footerY, (panelWidth - buttonWidth * 2).coerceAtLeast(1)) { onClose() })
        updateRows()
        if (!opened) {
            opened = true
            reload()
        }
        setInitialFocus(search)
    }

    private fun reload() {
        reloadList()
        CraftingRecipes.refresh {
            if (minecraft.screen === this) reloadList()
        }
        sourceStatus = CraftingRecipes.status()
    }

    private fun reloadList() {
        val previous = current
        recipes = CraftingRecipes.list()
        sourceStatus = CraftingRecipes.status()
        filter(previous)
    }

    private fun filter(previous: CraftingRecipeView? = current) {
        matches = recipes.map { recipe ->
            minOf(commandMatch(query, recipe.title), commandMatch(query, recipe.id),
                recipe.recipeIds.minOfOrNull { commandMatch(query, it) } ?: Int.MAX_VALUE) to recipe
        }.filter { it.first != Int.MAX_VALUE }.sortedBy { it.first }.map { it.second }
        selected = matches.indexOfFirst { it.id == previous?.id && ItemStack.isSameItemSameComponents(it.result, previous.result) }
            .takeIf { it >= 0 } ?: matches.indexOfFirst { recipe ->
                previous != null && recipe.recipeIds.any { it in previous.recipeIds } &&
                    ItemStack.isSameItemSameComponents(recipe.result, previous.result)
            }.takeIf { it >= 0 } ?: matches.indexOfFirst { recipe ->
                previous != null && recipe.width == previous.width && recipe.height == previous.height &&
                    recipe.shapeless == previous.shapeless && recipe.result.count == previous.result.count &&
                    ItemStack.isSameItemSameComponents(recipe.result, previous.result) &&
                    recipe.ingredients.size == previous.ingredients.size && recipe.ingredients.indices.all { index ->
                        val before = previous.ingredients[index]
                        val after = recipe.ingredients[index]
                        before.size == after.size && before.all { stack -> after.any { ItemStack.isSameItemSameComponents(stack, it) } }
                    }
            }.coerceAtLeast(0)
        updateRows()
        updateInventory()
    }

    private fun select(index: Int) {
        selected = index.coerceIn(0, (matches.size - 1).coerceAtLeast(0))
        updateRows()
        updateInventory()
    }

    private fun updateRows() {
        val start = selected / rowCount * rowCount
        rows.forEachIndexed { row, button ->
            val index = start + row
            val recipe = matches.getOrNull(index)
            button.visible = recipe != null && button.y + button.height <= minOf(detailY.takeIf { detailX == listX } ?: footerY - 28, footerY - 28)
            button.active = recipe != null
            button.message = catalogText(if (recipe == null) "" else "${if (index == selected) "> " else ""}${recipe.sourceTag} ${recipe.title}")
        }
        copy.active = current?.recipeIds?.isNotEmpty() == true
    }

    private fun updateInventory() {
        missing = current?.let(CraftingRecipes::inventoryStatus) ?: emptyList()
        missingCount = missing.count { it }
    }

    override fun tick() {
        ticks++
        if (ticks % 5L == 0L) updateInventory()
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.isEscape) { onClose(); return true }
        if (!primaryModifier(event) && !event.hasAltDown() && (event.isUp || event.isDown)) {
            select(selected + if (event.isDown) 1 else -1)
            return true
        }
        return super.keyPressed(event)
    }

    override fun onClose() { minecraft.setScreen(parent) }

    private fun text(graphics: GuiGraphics, value: String, x: Int, y: Int, available: Int, color: Long = 0xFFF2F5FC) {
        graphics.themeText(font, catalogClip(font, value, available), x, y, SpotlightTheme.color(color))
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, SpotlightTheme.color(0xF0101722))
        graphics.enableScissor(0, 0, width.coerceAtLeast(1), height.coerceAtLeast(1))
        super.render(graphics, mouseX, mouseY, delta)
        graphics.disableScissor()
        val contentBottom = (footerY - 28).coerceAtLeast(0)
        graphics.enableScissor(detailX, minOf(detailY, contentBottom), detailX + detailWidth, contentBottom)
        val recipe = current
        if (recipe == null) {
            text(graphics, if (recipes.isEmpty()) "No crafting recipes available" else "No matching recipes", detailX, detailY, detailWidth)
        } else {
            text(graphics, recipe.result.hoverName.string, detailX, detailY, detailWidth)
            text(graphics, "${recipe.sourceTag} ${if (recipe.shapeless) "Shapeless" else "Shaped ${recipe.width}×${recipe.height}"} · ${selected + 1}/${matches.size}", detailX, detailY + 13, detailWidth, 0xFF9DADC3)
            val slot = if (height < 290 || detailWidth < 160) 18 else 22
            val gridY = detailY + 26
            fun item(stack: ItemStack, x: Int, y: Int, absent: Boolean) {
                graphics.fill(x, y, x + slot - 1, y + slot - 1, SpotlightTheme.color(0xFF161D29))
                graphics.renderOutline(x, y, slot - 1, slot - 1, SpotlightTheme.color(0xFF415877))
                if (!stack.isEmpty) graphics.renderItem(stack, x + (slot - 16) / 2, y + (slot - 16) / 2)
                if (absent) graphics.fill(x + 1, y + 1, x + slot - 2, y + slot - 2,
                    (SpotlightTheme.color(0xFFFFB4A9) and 0x00FFFFFF) or 0x55000000)
                if (!stack.isEmpty && mouseX in x until x + slot - 1 && mouseY in y until y + slot - 1 &&
                    mouseX in detailX until detailX + detailWidth && mouseY in detailY until contentBottom)
                    graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY)
            }
            repeat(9) { cell ->
                val index = recipeIngredientAt(cell, recipe.width, recipe.height, recipe.shapeless, recipe.ingredients.size)
                val alternatives = recipe.ingredients.getOrNull(index)
                val stack = if (alternatives.isNullOrEmpty()) ItemStack.EMPTY else alternatives[((ticks / 20) % alternatives.size).toInt()]
                item(stack, detailX + cell % 3 * slot, gridY + cell / 3 * slot, missing.getOrNull(index) == true)
            }
            text(graphics, "→", detailX + slot * 3 + 4, gridY + slot + 6, 16)
            item(recipe.result, detailX + slot * 3 + 24, gridY + slot, false)
            text(graphics, "×${recipe.result.count}", detailX + slot * 4 + 26, gridY + slot + 6, (detailWidth - slot * 4 - 26).coerceAtLeast(0))
            text(graphics, if (missingCount == 0) "Ready to craft (one recipe)" else "Missing $missingCount ingredients", detailX, gridY + slot * 3 + 3, detailWidth,
                if (missingCount == 0) 0xFFF2F5FC else 0xFFFFB4A9)
            text(graphics, "Inventory estimate only · No auto-crafting", detailX, gridY + slot * 3 + 16, detailWidth, 0xFF9DADC3)
            text(graphics, recipe.recipeIds.minOrNull() ?: "Recipe ID unavailable in client display", detailX, gridY + slot * 3 + 29, detailWidth, 0xFF9DADC3)
        }
        graphics.disableScissor()
        val footerWidth = (width - listX * 2).coerceAtLeast(1)
        text(graphics, "↑↓ Results · [C] client index, not origin · [S] integrated", listX, footerY - 25, footerWidth, 0xFF9DADC3)
        text(graphics, sourceStatus, listX, footerY - 12, footerWidth, 0xFF9DADC3)
        if (mouseY in footerY - 12 until footerY - 1 && mouseX in listX until width - listX)
            graphics.setTooltipForNextFrame(catalogText(sourceStatus), mouseX, mouseY)
    }
}
