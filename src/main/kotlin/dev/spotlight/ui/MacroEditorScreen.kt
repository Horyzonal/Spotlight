package dev.spotlight.ui

import com.mojang.blaze3d.platform.InputConstants
import dev.spotlight.core.Macro
import dev.spotlight.core.MacroCompletion
import dev.spotlight.core.MacroTemplates
import dev.spotlight.platform.MacroRuntime
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.MultilineTextField
import net.minecraft.client.gui.components.MultiLineEditBox
import net.minecraft.client.gui.components.FittingMultiLineTextWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import java.util.UUID

class MacroEditorScreen(private val parent: Screen?, private val macro: Macro? = null) : Screen(catalogText("Macro editor")) {
    private var name = macro?.name.orEmpty()
    private var template = macro?.template ?: "/say {Argument=message:text}"
    private var error: String? = null
    private var validation = ""
    private lateinit var editor: MultiLineEditBox
    private var summary: FittingMultiLineTextWidget? = null
    private var left = 0
    private var panelWidth = 1
    private var detailY = 0
    private class CursorMirror(font: net.minecraft.client.gui.Font, width: Int) : MultilineTextField(font, width) {
        fun lineStart(): Int = getLineView(getLineAtCursor()).beginIndex()
        fun atLineEnd(): Boolean = getLineAtCursor() >= 0 && cursor() == getLineView(getLineAtCursor()).endIndex()
    }

    private lateinit var cursorState: CursorMirror
    private val completionRows = mutableListOf<Button>()
    private var completion: MacroCompletion.Match? = null
    private var selected = 0
    private var first = 0
    private var dismissed: Pair<String, Int>? = null
    private var tracking = true
    private var listOpen = false
    private var popupX = 0
    private var popupY = 0
    private var popupWidth = 0
    private var visibleRows = 1
    private val rowHeight = 24

    override fun isPauseScreen() = false
    override fun init() {
        panelWidth = minOf(SpotlightTheme.metric("editorWidth", 620, 160, 4096), (width - 24).coerceAtLeast(1))
        left = (width - panelWidth) / 2
        val field = addRenderableWidget(SpotlightEditBox(font, left, 30, panelWidth, 20, catalogText("Macro name")))
        field.setMaxLength(128)
        field.value = name
        field.setResponder { name = it; validate() }
        val editorHeight = (height - 190).coerceAtLeast(40)
        editor = addRenderableWidget(MultiLineEditBox.builder().setX(left).setY(66)
            .setTextShadow(SpotlightTheme.textShadow())
            .setTextColor(catalogNativeColor(-2039584, 0xFFF2F5FC))
            .setCursorColor(catalogNativeColor(-3092272, 0xFFF2F5FC))
            .setShowBackground(!catalogCustomFrame())
            .build(font, panelWidth, editorHeight, catalogText("Command template")))
        editor.setCharacterLimit(32768)
        editor.value = template
        editor.setValueListener { template = it; validate() }
        
        cursorState = CursorMirror(font, panelWidth - 8)
        cursorState.setCharacterLimit(32768)
        cursorState.setValue(template)
        completion = null
        dismissed = null
        listOpen = false
        completionRows.clear()
        repeat(8) { row ->
            completionRows += addWidget(macroButton("", left, 0, panelWidth) { acceptCompletion(first + row) }).also {
                it.height = rowHeight
                it.visible = false
                it.active = false
            }
        }
        detailY = editor.bottom + 6
        val buttonWidth = (panelWidth - 8) / 3
        val buttonsY = height - 55
        addRenderableWidget(macroButton("Save · ${primaryModifierLabel}+Enter", left, buttonsY, buttonWidth) { save() })
        addRenderableWidget(macroButton("Cancel · Esc", left + buttonWidth + 4, buttonsY, buttonWidth) { onClose() })
        addRenderableWidget(macroButton("Delete… · ${primaryModifierLabel}+Shift+D", left + 2 * (buttonWidth + 4), buttonsY, buttonWidth) { delete() }).active = macro != null
        summary = addRenderableWidget(catalogDetails(left, detailY, panelWidth,
            (buttonsY - detailY - 4).coerceAtLeast(12), Component.empty(), font))
        validate()
        setInitialFocus(field)
    }

    private fun validate() {
        error = null
        validation = try {
            require(name.isNotBlank()) { "Enter a macro name" }
            val arguments = MacroTemplates.arguments(template)
            "Arguments (repeated names prompt once): " + arguments.joinToString { "${it.name}:${it.type.name.lowercase()}" }.ifEmpty { "none" }
        } catch (failure: Exception) {
            error = failure.message ?: failure.javaClass.simpleName
            ""
        }
        summary?.message = catalogText(listOf(error ?: validation,
            "Type { for preview · Tab opens list · Tab/Enter insert · ↑/↓/PgUp/PgDn browse · Esc dismiss").joinToString("\n")).withStyle { it.withColor(catalogNativeColor(-1, if (error != null) 0xFFFFB4A9 else 0xFFF2F5FC)) }
    }

    private fun save() {
        validate()
        if (error != null) return
        try {
            MacroRuntime.store().put(Macro(macro?.id ?: UUID.randomUUID().toString(), name.trim(), template))
            onClose()
        } catch (failure: Exception) { error = failure.message ?: failure.javaClass.simpleName }
    }

    private fun delete() {
        val saved = macro ?: return
        minecraft.setScreen(ConfirmSpotlightScreen(this, "Delete ${saved.name}?", "This removes the saved macro. This cannot be undone.") {
            try { MacroRuntime.store().remove(saved.id); onClose() }
            catch (failure: Exception) { error = failure.message ?: failure.javaClass.simpleName }
        })
    }

    private fun refreshCompletion() {
        if (!tracking) return
        val previous = completion
        completion = if (editor.isFocused && cursorState.value() == editor.value && !cursorState.hasSelection() &&
            dismissed != (template to cursorState.cursor())) MacroCompletion.find(template, cursorState.cursor()) else null
        if (completion != previous) { selected = 0; first = 0 }
        if (completion == null) listOpen = false
        updateCompletionRows()
    }

    private fun updateCompletionRows() {
        val options = completion?.options.orEmpty()
        
        val bottom = (height - 59).coerceAtLeast(4)
        visibleRows = minOf(8, ((bottom - 4 - 32) / rowHeight).coerceAtLeast(0), options.size)
        first = first.coerceIn(0, (options.size - visibleRows).coerceAtLeast(0))
        popupWidth = maxOf(panelWidth, (options.maxOfOrNull { font.width(it) } ?: 0) + 48)
            .coerceAtMost((width - 8).coerceAtLeast(1))
        popupX = left.coerceIn(0, (width - popupWidth).coerceAtLeast(0))
        val popupHeight = visibleRows * rowHeight + 32
        popupY = (caretPosition()?.second?.plus(font.lineHeight + 2) ?: editor.bottom)
            .coerceIn(4, (bottom - popupHeight).coerceAtLeast(4))
        completionRows.forEachIndexed { row, button ->
            val index = first + row
            button.visible = listOpen && row < visibleRows
            button.active = button.visible
            button.x = popupX + 4
            button.y = popupY + 17 + row * rowHeight
            button.width = (popupWidth - 8).coerceAtLeast(1)
            button.message = catalogText(options.getOrNull(index)?.let { "${if (index == selected) "› " else ""}$it" }.orEmpty())
            button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(catalogText(
                "${options.getOrNull(index).orEmpty()} · ${index + 1}/${options.size} · Tab or Enter inserts")))
        }
    }

    private fun acceptCompletion(index: Int) {
        val option = completion?.options?.getOrNull(index) ?: return
        if (cursorState.hasSelection() || cursorState.value() != editor.value) return
        val edit = MacroCompletion.accept(template, cursorState.cursor(), option) ?: return
        tracking = false
        setFocused(editor)
        editor.value = edit.text
        cursorState.setValue(edit.text)
        
        val leftKey = KeyEvent(InputConstants.KEY_LEFT, 0, 0)
        repeat(edit.text.length - edit.cursor) { editor.keyPressed(leftKey); cursorState.keyPressed(leftKey) }
        tracking = true
        listOpen = false
        dismissed = null
        refreshCompletion()
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (editor.isFocused && event.isAllowedChatCharacter) cursorState.insertText(event.codepointAsString())
        val handled = super.charTyped(event)
        refreshCompletion()
        return handled
    }

    private fun trackMouse(event: MouseButtonEvent, doubleClick: Boolean, dragging: Boolean = false) {
        if (doubleClick) cursorState.selectWordAtCursor()
        else {
            cursorState.setSelecting(dragging || event.hasShiftDown())
            cursorState.seekCursorToPoint(event.x() - editor.x - 4, event.y() - editor.y - 4 + editor.scrollAmount())
            if (dragging) cursorState.setSelecting(event.hasShiftDown())
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        completionRows.firstOrNull { it.visible && it.isMouseOver(event.x(), event.y()) }?.let {
            return it.mouseClicked(event, doubleClick)
        }
        scrollbarDrag = editor.isMouseOver(event.x(), event.y()) && event.button() == InputConstants.MOUSE_BUTTON_LEFT && event.x() >= editor.right
        if (editor.isMouseOver(event.x(), event.y()) && event.button() == InputConstants.MOUSE_BUTTON_LEFT && !scrollbarDrag) {
            trackMouse(event, doubleClick)
        }
        val handled = super.mouseClicked(event, doubleClick)
        refreshCompletion()
        return handled
    }

    override fun mouseDragged(event: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
        
        if (editor.isFocused && event.button() == InputConstants.MOUSE_BUTTON_LEFT && !scrollbarDrag) trackMouse(event, false, true)
        val handled = super.mouseDragged(event, deltaX, deltaY)
        refreshCompletion()
        return handled
    }

    private var scrollbarDrag = false

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        scrollbarDrag = false
        val handled = super.mouseReleased(event)
        refreshCompletion()
        return handled
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        if (listOpen && visibleRows > 0 && mouseX >= popupX && mouseX < popupX + popupWidth &&
            mouseY >= popupY && mouseY < popupY + 32 + visibleRows * rowHeight && vertical != 0.0) {
            val options = completion?.options.orEmpty()
            first = (first + if (vertical > 0) -1 else 1).coerceIn(0, (options.size - visibleRows).coerceAtLeast(0))
            selected = selected.coerceIn(first, minOf(first + visibleRows - 1, options.lastIndex))
            updateCompletionRows()
            triggerImmediateNarration(true)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        refreshCompletion()
        if (completion != null) {
            if (event.isEscape) {
                if (listOpen) listOpen = false
                else {
                    dismissed = template to cursorState.cursor()
                    completion = null
                }
                updateCompletionRows()
                return true
            }
            if (event.modifiers() == 0) {
                if (event.key() == InputConstants.KEY_TAB) {
                    if (listOpen) acceptCompletion(selected)
                    else { listOpen = true; updateCompletionRows(); triggerImmediateNarration(true) }
                    return true
                }
                if (listOpen && event.isConfirmation) { acceptCompletion(selected); return true }
                val step = when (event.key()) {
                    InputConstants.KEY_UP -> -1
                    InputConstants.KEY_DOWN -> 1
                    InputConstants.KEY_PAGEUP -> -visibleRows.coerceAtLeast(1)
                    InputConstants.KEY_PAGEDOWN -> visibleRows.coerceAtLeast(1)
                    else -> 0
                }
                if (listOpen && step != 0) {
                    selected = (selected + step).coerceIn(0, completion!!.options.lastIndex)
                    first = first.coerceIn((selected - visibleRows + 1).coerceAtLeast(0), selected)
                    updateCompletionRows()
                    triggerImmediateNarration(true)
                    return true
                }
            }
        }
        if (event.isEscape) { onClose(); return true }
        if (primaryModifier(event) && event.hasShiftDown() && !event.hasAltDown() && event.key() == InputConstants.KEY_D) { delete(); return true }
        if (event.isConfirmation && primaryModifier(event) && !event.hasAltDown() && !event.hasShiftDown()) { save(); return true }
        if (editor.isFocused) {
            
            when {
                event.isCopy -> Unit
                event.isCut -> cursorState.insertText("")
                else -> cursorState.keyPressed(event)
            }
        }
        val handled = super.keyPressed(event)
        refreshCompletion()
        return handled
    }
    private fun caretPosition(): Pair<Int, Int>? {
        if (cursorState.value() != editor.value || cursorState.hasSelection()) return null
        val lineIndex = cursorState.getLineAtCursor()
        if (lineIndex < 0) return null
        return (editor.x + 4 + font.width(template.substring(cursorState.lineStart(), cursorState.cursor()))) to
            (editor.y + 4 + lineIndex * font.lineHeight - editor.scrollAmount().toInt())
    }

    private fun ghostOption(): String? = if (!cursorState.atLineEnd()) null else completion?.options?.firstOrNull {
        MacroCompletion.ghostSuffix(template, cursorState.cursor(), it) != null
    }

    override fun updateNarrationState(output: NarrationElementOutput) {
        super.updateNarrationState(output)
        val option = if (listOpen) completion?.options?.getOrNull(selected) else ghostOption()
        if (option != null) output.add(NarratedElementType.HINT, catalogText(
            if (listOpen) "Placeholder ${selected + 1} of ${completion!!.options.size}: $option. Up and Down or Page Up and Page Down browse. Tab or Enter inserts. Escape closes list."
            else "Suggested placeholder: $option. Tab opens suggestions. Escape dismisses preview."))
    }

    override fun onClose() { minecraft.setScreen(parent) }
    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, SpotlightTheme.color(0xF0101722))
        graphics.themeText(font, catalogText("Macro name"), left, 17, SpotlightTheme.color(0xFFF2F5FC))
        graphics.themeText(font, catalogClip(font, error ?: "Command template · one command; line breaks rejected", panelWidth), left, 54,
                    if (error != null) SpotlightTheme.color(0xFFFFB4A9) else SpotlightTheme.color(0xFF9DADC3))

        if (catalogCustomFrame()) catalogFrame(graphics, editor)
        super.render(graphics, mouseX, mouseY, delta)
        refreshCompletion()
        if (!listOpen) {
            val option = ghostOption()
            val suffix = option?.let { MacroCompletion.ghostSuffix(template, cursorState.cursor(), it) }
            val caret = caretPosition()
            if (suffix != null && caret != null) {
                graphics.enableScissor(editor.x + 4, editor.y + 4, editor.right - 4, editor.bottom - 4)
                graphics.themeText(font, suffix, caret.first, caret.second, SpotlightTheme.color(0xFF9DADC3))
                graphics.disableScissor()
            }
        }
        if (listOpen && visibleRows > 0) {
            val bottom = popupY + 32 + visibleRows * rowHeight
            graphics.fill(popupX, popupY, popupX + popupWidth, bottom, SpotlightTheme.color(0xFA101722))
            graphics.renderOutline(popupX, popupY, popupWidth, bottom - popupY, SpotlightTheme.color(0xFF415877))
            graphics.enableScissor(popupX + 2, popupY + 2, popupX + popupWidth - 2, bottom - 2)
            graphics.themeText(font, catalogClip(font, "Placeholders · ${selected + 1}/${completion!!.options.size}", popupWidth - 12),
                popupX + 6, popupY + 5, SpotlightTheme.color(0xFFF2F5FC))
            completionRows.filter { it.visible }.forEach { it.render(graphics, mouseX, mouseY, delta) }
            graphics.themeText(font, catalogClip(font, "↑/↓ · PgUp/PgDn · Tab/Enter insert · Esc close", popupWidth - 12),
                popupX + 6, bottom - 12, SpotlightTheme.color(0xFF9DADC3))
            graphics.disableScissor()
        }
    }
}
