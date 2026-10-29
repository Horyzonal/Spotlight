package dev.spotlight.ui

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import dev.spotlight.platform.CommandSuggestions
import dev.spotlight.platform.SelectionKind
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.commands.arguments.selector.EntitySelectorParser
import net.minecraft.commands.arguments.selector.options.EntitySelectorOptions
import net.minecraft.network.chat.Component


class SelectorScreen(
    private val parent: Screen,
    private val original: String,
    private val accept: (String) -> Unit
) : Screen(catalogText("Entity selector")) {
    private var model = runCatching { SelectorModel.parse(original) }.getOrElse {
        SelectorModel(original.take(2), emptyList())
    }
    private var draft = original
    private var flag = "type"
    private var error: String? = null
    private lateinit var selector: EditBox
    private lateinit var value: EditBox
    private val choices = mutableListOf<Button>()
    private var page = 0
    private var totalChoices = 0
    private var valueDraft = ""
    private var left = 0
    private var top = 0
    private var panelWidth = 0
    private val rowStep get() = SpotlightTheme.metric("compactRowHeight", 24, 22, 80)
    private var choiceCount = 1

    init {
        
        val slot = Regex("(?:\\[|,)([a-z_]+)=$").find(original)
        if (slot != null) {
            flag = slot.groupValues[1]
            val prefix = original.substring(0, slot.range.first)
            val repaired = if (original[slot.range.first] == '[') prefix else "$prefix]"
            runCatching { SelectorModel.parse(repaired) }.onSuccess {
                model = it
                draft = it.text()
            }
        } else if (original.length == 3 && original.endsWith('[')) draft = original.take(2)
    }

    override fun isPauseScreen() = false

    override fun init() {
        choices.clear()
        panelWidth = minOf(SpotlightTheme.metric("selectorWidth", 520, 160, 4096), (width - 16).coerceAtLeast(1))
        left = (width - panelWidth) / 2
        top = 12
        choiceCount = ((height - 222) / rowStep).coerceIn(1, 8)
        selector = addRenderableWidget(SpotlightEditBox(font, left, top + 22, panelWidth, 20, catalogText("Selector")))
        selector.setMaxLength(32768)
        selector.value = draft
        selector.setResponder { draft = it }
        val flags = SuggestionsBuilder("", 0)
        EntitySelectorOptions.suggestNames(EntitySelectorParser(StringReader("@e"), true), flags)
        val names = flags.build().list.map { it.text.removeSuffix("=") }.distinct()
        lateinit var flagButton: Button
        flagButton = addRenderableWidget(catalogButton("Flag: $flag", left, top + 48, panelWidth / 2 - 2) {
            flag = names.getOrElse((names.indexOf(flag) + 1) % names.size.coerceAtLeast(1)) { "type" }
            flagButton.message = catalogText("Flag: $flag")
            value.value = ""
            page = 0
            rebuildChoices()
        })
        addRenderableWidget(catalogButton("Add / replace flag", left + panelWidth / 2 + 2, top + 48, panelWidth / 2 - 2) { setFlag() })
        value = addRenderableWidget(SpotlightEditBox(font, left, top + 74, panelWidth, 20, catalogText("Flag value / fuzzy search")))
        value.setMaxLength(32768)
        value.setHint(catalogText("Flag value / fuzzy search · ! negates"))
        value.value = valueDraft
        value.setResponder { valueDraft = it; page = 0; rebuildChoices() }
        addRenderableWidget(catalogButton("Previous", left, height - 106, panelWidth / 2 - 2) { page = (page - 1).coerceAtLeast(0); rebuildChoices() })
        addRenderableWidget(catalogButton("Next", left + panelWidth / 2 + 2, height - 106, panelWidth / 2 - 2) { page++; rebuildChoices() })
        addRenderableWidget(catalogButton("Cancel", left, height - 58, panelWidth / 2 - 2) { onClose() })
        addRenderableWidget(catalogButton("Apply selector", left + panelWidth / 2 + 2, height - 58, panelWidth / 2 - 2) { apply() })
        rebuildChoices()
        setInitialFocus(selector)
    }

    private fun rebuildChoices() {
        val hadChoiceFocus = choices.any { it.isFocused }
        choices.forEach(::removeWidget)
        choices.clear()
        val kind = when (flag) {
            "type" -> SelectionKind.ENTITY_TYPES
            "gamemode" -> SelectionKind.GAMEMODES
            "predicate" -> SelectionKind.PREDICATES
            "name" -> SelectionKind.PLAYERS
            else -> null
        }
        val values = kind?.let { CommandSuggestions.catalog(it).map { row -> row.value to row.title } }
            ?: if (flag == "sort") listOf("nearest", "furthest", "random", "arbitrary").map { it to it } else emptyList()
        val query = value.value.removePrefix("!").lowercase()
        val filtered = values.filter { (id, title) ->
            dev.spotlight.platform.commandMatch(query, id) != Int.MAX_VALUE ||
                dev.spotlight.platform.commandMatch(query, title) != Int.MAX_VALUE
        }
        totalChoices = filtered.size
        page = page.coerceIn(0, ((filtered.size - 1) / choiceCount).coerceAtLeast(0))
        filtered.drop(page * choiceCount).take(choiceCount).forEachIndexed { index, (id, title) ->
            choices += addRenderableWidget(catalogButton("$title · $id", left, top + 100 + index * rowStep, panelWidth) {
                value.value = (if (value.value.startsWith('!')) "!" else "") + id
                setFlag()
                setFocused(value)
            })
        }
        if (hadChoiceFocus) setFocused(value)
    }

    private fun setFlag() {
        try {
            model = SelectorModel.parse(selector.value).set(flag, value.value)
            selector.value = model.text()
            error = null
        } catch (failure: IllegalArgumentException) { showError(failure.message ?: "Invalid flag") }
        catch (failure: IllegalStateException) { showError(failure.message ?: "Invalid flag") }
    }

    private fun apply() {
        try {
            val text = selector.value
            SelectorModel.parse(text)
            val reader = StringReader(text)
            EntitySelectorParser(reader, true).parse()
            require(!reader.canRead()) { "Unexpected selector suffix" }
            minecraft.gui.setScreen(parent)
            accept(text)
        } catch (failure: Exception) { showError(failure.message ?: "Invalid selector") }
    }

    private fun showError(message: String) {
        error = message
        selector.message = catalogText("Selector. $message")
        setFocused(selector)
        triggerImmediateNarration(true)
    }

    override fun onClose() { minecraft.gui.setScreen(parent) }
    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.isEscape) { onClose(); return true }
        if (event.isConfirmation && primaryModifier(event)) { apply(); return true }
        val selected = choices.indexOfFirst { it.isFocused }
        if ((value.isFocused || selected >= 0) && !primaryModifier(event) && !event.hasAltDown()) {
            if (event.isDown || event.isUp) {
                val step = if (event.isDown) 1 else -1
                if (choices.isNotEmpty()) {
                    val target = if (selected < 0) {
                        page * choiceCount + if (step > 0) 0 else choices.lastIndex
                    } else (page * choiceCount + selected + step).coerceIn(0, totalChoices - 1)
                    page = target / choiceCount
                    rebuildChoices()
                    setFocused(choices[target % choiceCount])
                }
                return true
            }
            if (event.isConfirmation && value.isFocused) { setFlag(); return true }
        }
        return super.keyPressed(event)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, SpotlightTheme.color(0xF0101722))
        graphics.themeText(font, catalogText("Entity selector · edit flags, then apply"), left, top + 5, SpotlightTheme.color(0xFFF2F5FC))
        val message = error ?: "Preserves other flags · type, distance, limit, sort, name, team, tag…"
        graphics.themeText(font, catalogClip(font, message, panelWidth), left, height - 80, SpotlightTheme.color(0xFFFFC0A0))
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        catalogHints(graphics, font, width, height, "↑↓ Choices · Enter Choose / Set · ${primaryModifierLabel}+Enter Apply · Tab Next · Esc Back")
    }
}
