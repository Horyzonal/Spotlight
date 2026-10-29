package dev.spotlight.ui


import dev.spotlight.core.Entry
import dev.spotlight.core.Fuzzy
import dev.spotlight.core.Macro
import dev.spotlight.core.MacroArgument
import dev.spotlight.core.MacroArgumentType
import dev.spotlight.core.MacroTemplates
import dev.spotlight.platform.CommandSuggestions
import dev.spotlight.platform.MacroRuntime
import dev.spotlight.platform.SelectionKind
import dev.spotlight.platform.Spotlight
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.FittingMultiLineTextWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component

internal fun macroButton(label: String, x: Int, y: Int, width: Int, onPress: () -> Unit): Button =
    catalogButton(label, x, y, width, onPress = onPress).also { it.setTooltip(Tooltip.create(catalogText(label))) }

internal fun macroSubmissionAction(argumentIndex: Int, argumentCount: Int): String =
    if (argumentIndex + 1 < argumentCount) "Next" else "Run"

internal class MacroExecutionState {
    private var attempted = false
    var executing = false
        private set
    var completed = false
        private set

    fun begin(automatic: Boolean): Boolean {
        if (completed || executing || (automatic && attempted)) return false
        attempted = true
        executing = true
        return true
    }

    fun finish(sent: Boolean) {
        completed = sent
        executing = false
    }
}

internal fun macroLocationSelection(draft: String, results: List<Entry>, selected: Int): Entry? {
    val coordinates = runCatching {
        MacroTemplates.expand("/say {Argument=location}", mapOf("location" to draft), emptyMap())
    }.isSuccess
    if (coordinates || draft.trimStart().firstOrNull()?.let { it.isDigit() || it in "+-.~^" } == true) return null
    return results.firstOrNull { it.id == draft || it.title.equals(draft, ignoreCase = true) }
        ?: results.getOrNull(selected)
}

class MacroRunScreen(private val parent: Screen?, private val macro: Macro) : Screen(catalogText(macro.name)) {
    private val drafts = linkedMapOf<String, String>()
    private val locationDimensions = mutableMapOf<String, String>()
    private var arguments = emptyList<MacroArgument>()
    private var index = 0
    private var error: String? = null
    private var command: String? = null
    private var previewScope: String? = null
    private val execution = MacroExecutionState()
    private var left = 0
    private var panelWidth = 1
    private val rowStep get() = SpotlightTheme.metric("compactRowHeight", 24, 22, 80)
    private var count = 0
    private var selected = 0
    private var offset = 0
    private var options = emptyList<Entry>()
    private var results = emptyList<Entry>()
    private val optionValues = mutableMapOf<String, String>()
    private val optionDimensions = mutableMapOf<String, String>()
    private val rows = mutableListOf<Button>()
    private var fields = emptyList<EditBox>()
    private var form: MacroFormLayout? = null
    private val input get() = fields.getOrNull(index)
    private var livePreview: FittingMultiLineTextWidget? = null
    private var liveText = ""
    private var optionsY = 0
    private var runtimeVariables: Map<String, String>? = null
    private var variablesAttempted = false
    private var variableError: String? = null

    override fun isPauseScreen() = false
    override fun init() {
        if (execution.completed || execution.executing) return
        rows.clear()
        form = null
        livePreview = null
        panelWidth = minOf(SpotlightTheme.metric("editorWidth", 620, 160, 4096), (width - 24).coerceAtLeast(1))
        left = (width - panelWidth) / 2
        try { arguments = MacroTemplates.arguments(macro.template) }
        catch (failure: Exception) { error = failure.message ?: failure.javaClass.simpleName }
        if (arguments.isEmpty()) {
            run(automatic = true)
            if (execution.completed) return
            command?.let {
                addRenderableWidget(catalogDetails(left, 48, panelWidth, (height - 156).coerceAtLeast(24), catalogText(it), font))
            }
        } else {
            val previewHeight = (height - 260).coerceIn(12, 72)
            livePreview = addRenderableWidget(catalogDetails(left, 42, panelWidth, previewHeight, Component.empty(), font))
            val top = 42 + previewHeight + 8
            val available = (height - 70 - top).coerceAtLeast(1)
            count = ((available - macroFieldStep - 16) / rowStep).coerceIn(0, 3)
            val formHeight = (available - count * rowStep - if (count > 0) 16 else 0).coerceAtLeast(1)
            optionsY = top + formHeight + 16
            fields = arguments.map { argument ->
                SpotlightEditBox(font, left, top, panelWidth - 10, 20,
                    catalogText("${argument.name}: ${argument.type.name.lowercase()}")).also { field ->
                    field.setMaxLength(32768)
                    field.value = drafts[argument.name].orEmpty()
                    field.setResponder {
                        drafts[argument.name] = it
                        locationDimensions.remove(argument.name)
                        error = null
                        if (arguments.getOrNull(index)?.name == argument.name) {
                            selected = 0
                            offset = 0
                            rebuildRows()
                        } else updateLivePreview()
                    }
                }
            }
            form = addRenderableWidget(MacroFormLayout(left, top, panelWidth, formHeight, font, fields,
                arguments.map { "${it.name}: ${it.type.name.lowercase()}" }, ::activate))
            index = index.coerceIn(arguments.indices)
            form!!.setFocused(fields[index])
            setInitialFocus(form!!)
        }
        controls(if (arguments.isEmpty()) "Retry Run" else "Run", ::submit)
    }

    private fun activate(target: Int) {
        index = target
        selected = 0
        offset = 0
        try { loadOptions(arguments[index]) }
        catch (failure: Exception) { error = failure.message ?: failure.javaClass.simpleName; options = emptyList() }
        rebuildRows()
    }

    private fun focusArgument(target: Int) {
        val container = form ?: return
        setFocused(container)
        container.setFocused(fields[target])
    }

    private fun loadOptions(argument: MacroArgument) {
        optionValues.clear()
        optionDimensions.clear()
        options = when (argument.type) {
            MacroArgumentType.ITEM, MacroArgumentType.BLOCK, MacroArgumentType.ENTITY -> {
                val kind = when (argument.type) {
                    MacroArgumentType.ITEM -> SelectionKind.ITEMS
                    MacroArgumentType.BLOCK -> SelectionKind.BLOCKS
                    else -> SelectionKind.ENTITIES
                }
                CommandSuggestions.catalog(kind).map { Entry(it.value, it.title, it.value) }
            }
            MacroArgumentType.PLAYER -> requireNotNull(minecraft.connection) { "Join a world first" }.onlinePlayers.map {
                val name = it.profile.name
                Entry(name, name, "Online player")
            }
            MacroArgumentType.LOCATION -> {
                val saved = Spotlight.locations().list(Spotlight.scope()).associateBy { it.id }
                Spotlight.locationEntries().mapNotNull { entry ->
                    val location = saved[entry.id.removePrefix("location:")] ?: return@mapNotNull null
                    optionValues[entry.id] = "${location.x} ${location.y} ${location.z}"
                    optionDimensions[entry.id] = location.dimension
                    entry
                } + runCatching {
                    val look = MacroRuntime.variables("/say {Look}").getValue("Look")
                    optionValues["look"] = look
                    optionDimensions["look"] = Spotlight.dimension()
                    listOf(Entry("look", "Looked-at block", look))
                }.getOrDefault(emptyList())
            }
            else -> emptyList()
        }
    }

    private fun rebuildRows() {
        val field = input ?: return
        val rowFocused = rows.any { it.isFocused }
        rows.forEach(::removeWidget)
        rows.clear()
        results = Fuzzy.search(field.value, options, limit = options.size)
        selected = selected.coerceIn(0, (results.size - 1).coerceAtLeast(0))
        offset = offset.coerceIn((selected - count.coerceAtLeast(1) + 1).coerceAtLeast(0), selected)
        results.drop(offset).take(count).forEachIndexed { rowIndex, entry ->
            rows += addRenderableWidget(macroButton("${if (selected == offset + rowIndex) "• " else ""}${entry.title} · ${entry.detail}", left, optionsY + rowIndex * rowStep, panelWidth) {
                accept(entry)
            })
        }
        if (rowFocused) focusArgument(index)
        updateLivePreview()
    }

    private fun captureVariables(): Map<String, String>? {
        if (!variablesAttempted) {
            variablesAttempted = true
            try {
                val scope = Spotlight.scope()
                runtimeVariables = MacroRuntime.variables(macro.template).toMap()
                previewScope = scope
                variableError = null
            } catch (failure: Exception) {
                variableError = failure.message ?: failure.javaClass.simpleName
            }
        }
        return runtimeVariables
    }

    private fun updateLivePreview() {
        val variables = captureVariables().orEmpty()
        try {
            liveText = MacroTemplates.preview(macro.template, drafts, variables)
            livePreview?.message = catalogText(liveText)
        } catch (failure: Exception) { fail(failure) }
    }

    private fun accept(entry: Entry) {
        val argument = arguments.getOrNull(index) ?: return
        val dimension = optionDimensions[entry.id]
        try {
            if (dimension != null) require(dimension == Spotlight.dimension()) { "Location is in $dimension; switch dimensions first" }
            val value = optionValues[entry.id] ?: entry.id
            macroFormSelect(drafts, argument.name, value)
            input?.value = value
            if (dimension == null) locationDimensions.remove(argument.name) else locationDimensions[argument.name] = dimension
            error = null
            focusArgument(index)
        } catch (failure: Exception) { fail(failure) }
    }

    private fun validateArguments(): Boolean {
        arguments.forEachIndexed { target, argument ->
            try {
                val value = drafts[argument.name].orEmpty()
                require(value.isNotBlank()) { "Enter ${argument.name}" }
                if (argument.type in listOf(MacroArgumentType.PLAYER, MacroArgumentType.ITEM, MacroArgumentType.BLOCK, MacroArgumentType.ENTITY)) {
                    loadOptions(argument)
                    require(options.any { it.id == value }) { "Choose a ${argument.type.name.lowercase()} from the list" }
                }
                MacroTemplates.expand("/say {Argument=value:${argument.type.name.lowercase()}}", mapOf("value" to value), emptyMap())
                locationDimensions[argument.name]?.let { dimension ->
                    require(Spotlight.dimension() == dimension) { "Location ${argument.name} is in $dimension; switch dimensions first" }
                }
            } catch (failure: Exception) {
                focusArgument(target)
                fail(failure)
                return false
            }
        }
        if (arguments.isNotEmpty()) activate(index)
        return true
    }

    private fun submit() {
        if (execution.completed || execution.executing) return
        if (validateArguments()) run()
    }

    private fun controls(label: String, action: () -> Unit) {
        val cell = (panelWidth - 8) / 3
        val y = height - 38
        val cancel = addRenderableWidget(macroButton("Cancel", left, y, cell) { onClose() })
        if (arguments.isEmpty()) setInitialFocus(cancel)
        addRenderableWidget(macroButton("Back · Esc", left + cell + 4, y, cell) { onClose() })
        addRenderableWidget(macroButton("$label · ${primaryModifierLabel}+Enter", left + 2 * (cell + 4), y, cell, onPress = action))
    }

    private fun checkDimensions() {
        for ((name, dimension) in locationDimensions) require(Spotlight.dimension() == dimension) {
            "Location $name is in $dimension; switch dimensions first"
        }
    }
    private fun run(automatic: Boolean = false) {
        if (!execution.begin(automatic)) return
        var sent = false
        try {
            if (!automatic && variableError != null) {
                variablesAttempted = false
                variableError = null
            }
            val variables = captureVariables() ?: throw IllegalStateException(variableError ?: "Runtime variables unavailable")
            require(Spotlight.scope() == previewScope) { "World changed; cancel and start the macro again" }
            checkDimensions()
            val expanded = MacroTemplates.expand(macro.template, drafts, variables)
            command = expanded
            Spotlight.executeSlash(expanded)
            sent = true
        } catch (failure: Exception) { fail(failure) }
        finally { execution.finish(sent) }
        if (sent) minecraft.setScreen(null)
    }
    private fun fail(failure: Exception) {
        error = failure.message ?: failure.javaClass.simpleName
        input?.message = catalogText("${arguments.getOrNull(index)?.name.orEmpty()}. $error")
        triggerImmediateNarration(true)
    }
    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.isEscape) { onClose(); return true }
        if (event.isConfirmation && primaryModifier(event) && !event.hasAltDown() && !event.hasShiftDown()) {
            submit()
            return true
        }
        if (input?.isFocused == true && !event.hasAltDown()) {
            if (!primaryModifier(event) && (event.isDown || event.isUp) && results.isNotEmpty()) {
                selected += if (event.isDown) 1 else -1
                rebuildRows()
                return true
            }
            if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_SPACE && event.hasControlDown() && results.isNotEmpty()) {
                accept(results[selected])
                return true
            }
            if (event.isConfirmation && !primaryModifier(event)) {
                if (index + 1 < fields.size) focusArgument(index + 1) else submit()
                return true
            }
        }
        return super.keyPressed(event)
    }
    override fun onClose() { minecraft.setScreen(parent) }
    override fun getNarrationMessage(): Component = catalogText("${macro.name}. ${arguments.getOrNull(index)?.let { "${it.name}: ${it.type}. " }.orEmpty()}${command ?: liveText}. ${error ?: variableError.orEmpty()}")
    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, SpotlightTheme.color(0xF0101722))
        val heading = arguments.getOrNull(index)?.let { "${macro.name} · ${index + 1}/${arguments.size} · ${it.name}: ${it.type.name.lowercase()}" }
            ?: "${macro.name} · Could not run"
        graphics.themeText(font, catalogClip(font, heading, panelWidth), left, 20, SpotlightTheme.color(0xFFF2F5FC))
        (error ?: variableError)?.let { font.split(catalogText(it), panelWidth).take(2).forEachIndexed { line, text ->
            graphics.themeText(font, text, left, height - 64 + line * font.lineHeight, SpotlightTheme.color(0xFFFFB4A9))
        } }
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        if (count > 0 && arguments.isNotEmpty()) graphics.themeText(font,
            catalogClip(font, "${arguments[index].name} suggestions · ↑↓ Select · Ctrl+Space Fill", panelWidth),
            left, optionsY - 12, SpotlightTheme.color(0xFF9DADC3))

    }
}
