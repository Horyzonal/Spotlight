package dev.spotlight.ui

import dev.spotlight.core.Calculator
import dev.spotlight.core.SavedLocation

import dev.spotlight.core.Entry
import dev.spotlight.core.Fuzzy
import dev.spotlight.core.Preferences
import dev.spotlight.core.parseQuantity
import com.mojang.brigadier.StringReader
import dev.spotlight.platform.CommandSuggestion
import dev.spotlight.platform.CommandSuggestions
import dev.spotlight.platform.SelectionKind
import net.minecraft.client.gui.components.PlayerFaceRenderer
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation
import dev.spotlight.platform.CommandContext
import dev.spotlight.platform.CommandInput
import dev.spotlight.platform.Spotlight
import dev.spotlight.platform.SpotlightCommand
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import com.mojang.blaze3d.platform.InputConstants

class SpotlightScreen(private val parent: Screen?) : Screen(Component.literal("Spotlight")) {
    private val flow = PaletteFlow()
    private val history = HistoryNavigation()
    private var changingInput = false
    private var namedSubmission: String? = null
    private var commands = emptyList<SpotlightCommand>()
    private var items = emptyList<Entry>()
    private var results = emptyList<Entry>()
    private val rows = mutableListOf<Button>()
    private val stacks = mutableMapOf<String, ItemStack>()
    private var preferences: Preferences? = null
    private var error: String? = null
    private var themeWarning: String? = null
    private lateinit var input: EditBox

    private var selected = 0
    private var offset = 0
    private var left = 0
    private var top = 0
    private var panelWidth = 0
    private var panelHeight = 0
    private var rowHeight = 30
    private var visibleRows = 1
    private var listTop = 0
    private var bottom = 0
    private var refreshing = false
    private var requestConsumed = false
    private var suggestionGeneration = 0
    private var suggestionText: String? = null
    private var suggestions = emptyMap<String, CommandSuggestion>()
    private var footerTop = 0
    private var footerTextWidth = 0
    private var nowPlaying: NowPlayingWidget? = null
    private var footerLines = emptyList<net.minecraft.util.FormattedCharSequence>()

    override fun isPauseScreen() = false

    override fun init() {
        val selectedId = currentEntry()?.id
        themeWarning = SpotlightTheme.open().warnings.takeIf { it.isNotEmpty() }?.joinToString(" · ")
        panelWidth = minOf(SpotlightTheme.metric("layout.paletteWidth", 560, 160), (width - 16).coerceAtLeast(1))
        rowHeight = if (height < 240) SpotlightTheme.metric("layout.compactRowHeight", 24, 22, 80)
            else SpotlightTheme.metric("layout.rowHeight", 30, 22, 80)
        val footerLayout = nowPlayingLayout(panelWidth)
        footerTextWidth = footerLayout.textWidth
        val hintWidth = footerTextWidth.coerceAtLeast(1)
        val hintLimit = ((height - 132) / font.lineHeight).coerceAtLeast(1)
        val hints = font.split(Component.literal(footerHints()), hintWidth)
        footerLines = if (hints.size <= hintLimit) hints else
            hints.take(hintLimit - 1) + font.split(Component.literal("${primaryModifierLabel}+K Actions"), hintWidth).take(1)
        val footerHeight = footerLines.size * font.lineHeight + 22
        visibleRows = ((height - 90 - footerHeight) / rowHeight).coerceIn(1, 9)
        panelHeight = minOf(height, 86 + visibleRows * rowHeight + footerHeight)
        left = (width - panelWidth) / 2
        top = ((height - panelHeight) / 2).coerceAtLeast(0)
        bottom = top + panelHeight
        listTop = top + 80
        footerTop = (bottom - footerHeight).coerceAtLeast(top)
        try {
            commands = Spotlight.allCommands().distinctBy { it.id }
        } catch (failure: Exception) {
            commands = (Spotlight.commands.toList() + Spotlight.serverCommands()).distinctBy { it.id }
            error = "Cannot load macros: ${failure.message ?: failure.javaClass.simpleName}"
        }
        try {
            items = Spotlight.items()
        } catch (failure: Exception) {
            error = "Cannot load items: ${failure.message ?: failure.javaClass.simpleName}"
        }
        try {
            preferences = Spotlight.preferences()
        } catch (failure: Exception) {
            error = "Cannot load preferences: ${failure.message ?: failure.javaClass.simpleName}"
        }
        input = addRenderableWidget(spotlightEditBox(font, left + 10, top + 29, (panelWidth - 20).coerceAtLeast(1), 20,
            Component.literal("Search commands and items")))
        input.setMaxLength(32768)
        input.setHint(Component.literal(when (flow.state.stage) {
            PaletteStage.QUANTITY -> "Quantity: 1–2304"
            PaletteStage.NUMBER -> "Enter a number"
            else -> "Search commands, items, IDs…"
        }))
        input.value = flow.state.query
        input.setResponder { text ->
            if (!changingInput) {
                history.edited()
                val cursor = input.cursorPosition
                val expanded = if (flow.state.stage == PaletteStage.ROOT && text.startsWith('/') &&
                    cursor > 0 && text[cursor - 1] == ' ') CommandSuggestions.completeOnSpace(text, cursor, suggestions.values.toList()) else null
                if (expanded != null) {
                    setQuery(expanded.first, expanded.second)
                } else {
                    flow.state.query = text
                    selected = 0
                    offset = 0
                    refresh()
                }
            }
        }
        val tabWidth = ((panelWidth - 28) / 3).coerceAtLeast(1)
        val locations = flow.state.stage == PaletteStage.LOCATIONS
        addRenderableWidget(spotlightButton("${if (!locations) "• " else ""}All · ${primaryModifierLabel}+1", left + 10, top + 54, tabWidth) {
            switchTab(false)
        })
        addRenderableWidget(spotlightButton("${if (locations) "• " else ""}Locations · ${primaryModifierLabel}+2", left + 14 + tabWidth, top + 54, tabWidth) {
            switchTab(true)
        })
        addRenderableWidget(spotlightButton("Macros · ${primaryModifierLabel}+3", left + 18 + 2 * tabWidth, top + 54, tabWidth) {
            openMacroTab(2)
        })
        nowPlaying = NowPlayingWidget(font, left + panelWidth - 12 - footerLayout.widgetWidth,
            footerTop, footerLayout.widgetWidth) { minecraft!!.setScreen(CurrentSongScreen(this)) }.also {
            addRenderableWidget(it.button)
        }
        refresh()
        results.indexOfFirst { it.id == selectedId }.takeIf { it >= 0 }?.let {
            selected = it
            keepVisible()
            rebuildRows()
            updateSelection()
        }
        setInitialFocus(input)
        if (!requestConsumed) {
            requestConsumed = true
            val id = Spotlight.requestedCommandId
            val query = Spotlight.requestedQuery
            Spotlight.requestedCommandId = null
            Spotlight.requestedQuery = null
            if (query != null) input.value = query
            else if (id != null) command(id)?.let { startCommand(it) } ?: showError("Unknown requested command: $id")
        }
    }

    private fun setQuery(text: String, cursor: Int = text.length) {
        changingInput = true
        try {
            input.value = text
            input.setCursorPosition(cursor)
            input.setHighlightPos(cursor)
        } finally {
            changingInput = false
        }
        flow.state.query = text
        selected = 0
        offset = 0
        refresh()
    }

    private fun argumentHint(): String? = if (flow.state.stage == PaletteStage.ROOT && input.value.startsWith('/'))
        CommandSuggestions.argumentHint(input.value) else null

    private fun command(id: String?) = commands.firstOrNull { it.id == id }
    private fun commandEntry(command: SpotlightCommand): Entry {
        val settings = Spotlight.settings()
        val alias = Spotlight.commandAliases(command.id).takeIf { it.isNotEmpty() }?.joinToString(" / ")
        val hotkey = settings.hotkeys[command.id]
        return Entry(command.id, if (alias == null || command.id.startsWith("macro:")) command.title else "$alias · ${command.title}",
            listOfNotNull(alias?.takeIf { command.id.startsWith("macro:") }?.let { "Command: $it" }, command.description, hotkey).joinToString(" · "))
    }
    private fun selectedCommand(): SpotlightCommand? {
        if (flow.state.stage == PaletteStage.ROOT && input.value.startsWith('/')) {
            val name = slashCommandName(input.value) ?: return null
            val suggested = currentEntry()?.let { suggestions[it.id] }
            val selectedName = suggested?.takeIf { it.start == 1 }?.value
            return command("server:$name") ?: selectedName?.let { command("server:$it") }
        }
        return command(flow.state.commandId) ?: currentEntry()?.let { command(it.id) }
    }
    private fun currentEntry(): Entry? = results.getOrNull(selected)
    private fun unavailable(command: SpotlightCommand): String? = try {
        command.unavailable()
    } catch (failure: Exception) {
        "Availability check failed: ${failure.message ?: failure.javaClass.simpleName}"
    }

    private fun refresh() {
        if (!::input.isInitialized || refreshing) return
        refreshing = true
        try {
            val state = flow.state
            if (state.stage == PaletteStage.ROOT && state.query.startsWith('/')) {
                refreshSlash(state.query)
                return
            }
            suggestionGeneration++
            suggestionText = null
            suggestions = emptyMap()
            val entries = when (state.stage) {
                PaletteStage.ROOT -> commands.map(::commandEntry) + items
                PaletteStage.ITEMS -> items
                PaletteStage.ACTIONS -> commands.filter { it.input == CommandInput.ITEM || it.input == CommandInput.ITEM_QUANTITY }.map(::commandEntry)
                PaletteStage.QUANTITY -> listOf(command(state.commandId)?.let(::commandEntry) ?: Entry("give:run", "Give ${state.itemId}", "Target: ${state.give?.target}"))
                PaletteStage.NUMBER -> listOfNotNull(command(state.commandId)?.let(::commandEntry))
                PaletteStage.ENTITY -> Spotlight.entities()
                PaletteStage.BLOCK -> Spotlight.blocks()
                PaletteStage.LOCATION, PaletteStage.LOCATIONS -> Spotlight.locationEntries()
            }

            val naturalTp = state.stage == PaletteStage.ROOT && state.query.startsWith("tp ")
            val calculation = if (state.stage == PaletteStage.ROOT) Calculator.evaluate(state.query) else null
            results = when {

                naturalTp -> Fuzzy.search(state.query.removePrefix("tp "), Spotlight.locationEntries(), limit = 4096)
                calculation != null -> Fuzzy.search(
                                    state.query, entries, preferences?.favorites.orEmpty(), preferences?.recent.orEmpty(), entries.size,
                                ) + Entry("calculator:copy", calculation.display, "${calculation.stacks} · Enter to copy answer")
                state.stage == PaletteStage.QUANTITY || state.stage == PaletteStage.NUMBER -> entries
                else -> Fuzzy.search(
                state.query, entries, preferences?.favorites.orEmpty(), preferences?.recent.orEmpty(), entries.size,
            )
            }
            selected = selected.coerceIn(0, (results.size - 1).coerceAtLeast(0))
            offset = offset.coerceIn(0, (results.size - visibleRows).coerceAtLeast(0))
            keepVisible()
            rebuildRows()
            updateSelection()
        } catch (failure: Exception) {
            error = failure.message ?: failure.javaClass.simpleName
            results = emptyList()
            rebuildRows()
            updateSelection()
        } finally {
            refreshing = false
        }
    }

    private fun refreshSlash(text: String) {
        if (suggestionText != text) {
            suggestionText = text
            val generation = ++suggestionGeneration
            suggestions = emptyMap()
            results = emptyList()
            CommandSuggestions.request(text) { values ->
                if (generation != suggestionGeneration || minecraft!!.screen !== this || input.value != text) return@request
                val selectedId = currentEntry()?.id
                suggestions = values.associateBy { "slash:${it.start}:${it.end}:${it.value}" }
                results = suggestions.map { (id, value) -> Entry(id, value.title, value.detail.ifBlank { value.value }) }
                selected = results.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
                keepVisible()
                rebuildRows()
                updateSelection()
            }
        }
        rebuildRows()
        updateSelection()
    }

    private fun validSlash(text: String): Boolean {
        val connection = minecraft!!.connection ?: return false
        val parsed = connection.commands.parse(StringReader(CommandSuggestions.executionText(text)).also { it.cursor = 1 }, connection.suggestionsProvider)
        return !parsed.reader.canRead() && parsed.context.lastChild.command != null
    }

    private fun openSelector(): Boolean {
        val text = input.value
        if (!text.startsWith('/')) return false
        val selector = selectorSpan(text, input.cursorPosition) ?: return false
        minecraft!!.setScreen(SelectorScreen(this, text.substring(selector.first, selector.last + 1)) { value ->
            input.value = text.replaceRange(selector.first, selector.last + 1, value)
            input.setCursorPosition(selector.first + value.length)
            setFocused(input)
        })
        return true
    }

    private fun completeSlash(suggestion: CommandSuggestion? = currentEntry()?.let { suggestions[it.id] }) {
        val text = input.value
        val span = selectorSpan(text, input.cursorPosition)
        if (span != null && '[' in text.substring(span.first, span.last + 1) && openSelector()) return
        if (suggestion == null) {
            if (validSlash(text)) {
                input.value = CommandSuggestions.executionText(text) + " "
                setFocused(input)
            } else showError("Incomplete command · complete argument or correct syntax")
            return
        }
        val completed = CommandSuggestions.apply(text, suggestion)
        val advance = suggestion.end == text.length && !suggestion.value.endsWith('=') && !suggestion.value.endsWith('[')
        input.value = completed + if (advance) " " else ""
        input.setCursorPosition(suggestion.start + suggestion.value.length + if (advance) 1 else 0)
        setFocused(input)
    }

    private fun renderSuggestionIcon(graphics: GuiGraphics, suggestion: CommandSuggestion, x: Int, y: Int): Boolean {
        return try {
            when (suggestion.kind) {
                SelectionKind.PLAYERS -> {
                    val player = minecraft!!.connection?.onlinePlayers?.firstOrNull { it.profile.id.toString() == suggestion.iconId } ?: return false
                    PlayerFaceRenderer.draw(graphics, player.skin, x, y, 16)
                    true
                }
                SelectionKind.EFFECTS -> {
                    val id = ResourceLocation.tryParse(suggestion.iconId ?: suggestion.value) ?: return false
                    val sprite = ResourceLocation.fromNamespaceAndPath(id.namespace, "mob_effect/${id.path}")
                    if (minecraft!!.resourceManager.getResource(ResourceLocation.fromNamespaceAndPath(id.namespace, "textures/gui/sprites/mob_effect/${id.path}.png")).isEmpty) return false
                    graphics.blitSprite(RenderType::guiTextured, sprite, x, y, 16, 16)
                    true
                }
                else -> false
            }
        } catch (_: IllegalArgumentException) { false }
    }

    private fun keepVisible() {
        if (selected < offset) offset = selected
        if (selected >= offset + visibleRows) offset = selected - visibleRows + 1
    }

    private fun detail(entry: Entry): String {
        val cmd = command(entry.id)
        return cmd?.let(::unavailable) ?: if (flow.state.stage == PaletteStage.QUANTITY) {
            if (parseQuantity(input.value) == null) "Enter a whole number from 1 to 2304"
            else "${flow.state.itemId} × ${input.value}${flow.state.give?.let { " · Target: ${it.target}" }.orEmpty()} · Enter to run"
        } else if (input.value.trim() == "/seed") "Custom seed copies known full seed · singleplayer only"
        else entry.detail.ifBlank { "Enter to choose" }
    }

    private fun rebuildRows() {
        val hadRowFocus = rows.any { it.isFocused }
        rows.forEach(::removeWidget)
        rows.clear()
        results.drop(offset).take(visibleRows).forEachIndexed { index, entry ->
            if (listTop + (index + 1) * rowHeight > footerTop - 4) return@forEachIndexed
            val absolute = offset + index
            val reason = command(entry.id)?.let(::unavailable)
            val caption = if (flow.state.stage == PaletteStage.QUANTITY) "Run ${entry.title}" else entry.title
            val narration = Component.literal("$caption. ${detail(entry)}" + if (entry.id in preferences?.favorites.orEmpty()) ". Favorite" else "")
            val button = object : Button(left + 10, listTop + index * rowHeight, (panelWidth - 24).coerceAtLeast(1), rowHeight - 2,
                narration, OnPress { activateRow(entry.id) }, CreateNarration { supplier -> supplier.get() }) {
                override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
                    val chosen = absolute == selected
                    graphics.fill(x, y, right, bottom, when {
                        isHoveredOrFocused -> SpotlightTheme.color(0xFF2B3F59)
                        chosen -> SpotlightTheme.color(0xFF22334B)
                        else -> SpotlightTheme.color(0xFF161D29)
                    })
                    if (chosen) graphics.fill(x, y, x + 2, bottom, SpotlightTheme.color(0xFF81B8FF))
                    graphics.enableScissor(x + 3, y, right - 3, bottom)
                    val suggestion = suggestions[entry.id]
                    val hasIcon = suggestion?.let { renderSuggestionIcon(graphics, it, x + 6, y + (height - 16) / 2) } == true
                    val itemId = if (suggestion?.kind == SelectionKind.ITEMS) suggestion.iconId ?: suggestion.value else if (entry.id.startsWith("item:") || flow.state.stage == PaletteStage.QUANTITY) entry.itemId ?: flow.state.itemId else null
                    if (itemId != null) {
                        try {
                            val stack = stacks.getOrPut(itemId) { Spotlight.stack(itemId) }
                            graphics.renderItem(stack, x + 6, y + (height - 16) / 2)
                        } catch (failure: Exception) {
                            error = "Cannot render item: ${failure.message ?: failure.javaClass.simpleName}"
                        }
                    }
                    val textX = x + if (itemId != null || hasIcon) 28 else 8
                    val availableWidth = (right - textX - 20).coerceAtLeast(0)
                    val titleColor = if (active) SpotlightTheme.color(0xFFF2F5FC) else SpotlightTheme.color(0xFF999FA9)
                    graphics.themeText(font, clipped(caption, availableWidth), textX, y + 4, titleColor)
                    if (height >= 22) graphics.themeText(font, clipped(detail(entry), availableWidth), textX, y + 15,
                        if (reason == null) SpotlightTheme.color(0xFF9DADC3) else SpotlightTheme.color(0xFFE0AA85))
                    if (entry.id in preferences?.favorites.orEmpty()) graphics.themeText(font, "★", right - 14, y + 5, SpotlightTheme.color(0xFFFFD27D))
                    graphics.disableScissor()
                }
            }
            button.active = reason == null && (flow.state.stage != PaletteStage.QUANTITY || parseQuantity(input.value) != null)
            button.setTooltip(Tooltip.create(narration))
            rows.add(addRenderableWidget(button))
        }
        if (hadRowFocus) setFocused(input)
    }

    private fun updateSelection() {
        val entry = currentEntry()

        input.message = Component.literal((when (flow.state.stage) {
            PaletteStage.QUANTITY -> "Quantity, 1 to 2304"
            PaletteStage.NUMBER -> "Enter a number"
            else -> argumentHint() ?: "Search commands and items"
        }) +
            (entry?.let { ". Selected ${it.title}. ${detail(it)}. ${selected + 1} of ${results.size}" } ?: ". No results") +
            (error?.let { ". $it" } ?: "") + ". ${footerHints()}")
    }

    private fun activateRow(id: String) {
        selected = results.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return
        setFocused(input)
        updateSelection()
        routePaletteRowClick(id, id in suggestions,
            complete = { completeSlash(suggestions[it]) },
            activate = { activate(it) })
    }

    private fun activate(clickedId: String? = null) {
        val state = flow.state
        if (clickedId == null && state.stage == PaletteStage.ROOT) {
            val aliasId = Spotlight.resolveAlias(state.query)
            val aliased = aliasId?.let(::command)
            if (aliased != null) { startCommand(aliased); return }
        }
        if (clickedId == null && state.stage == PaletteStage.ROOT && state.query.startsWith('/')) {
            if (validSlash(state.query)) runSlash(state.query) else completeSlash()
            return
        }
        if (state.stage == PaletteStage.NUMBER) {
            if (state.query.toDoubleOrNull()?.isFinite() != true) { showError("Enter a finite number"); return }
            command(state.commandId)?.let { execute(it, text = state.query) }
            return
        }
        val entry = (if (clickedId == null) currentEntry() else results.firstOrNull { it.id == clickedId }) ?: return
        if (entry.id == "calculator:copy") {
            minecraft!!.keyboardHandler.clipboard = entry.title
            error = "Copied ${entry.title}"
            updateSelection()
            return
        }
        if (state.stage == PaletteStage.QUANTITY) {
            val quantity = parseQuantity(input.value)
            if (quantity == null) { showError("Quantity must be a whole number from 1 to 2304"); return }
            if (state.give != null) runSlash("/give ${state.give.target} ${state.itemId} $quantity")
            else command(state.commandId)?.let { execute(it, quantity) }
            return
        }
        if (state.stage == PaletteStage.LOCATIONS) {
            openLocationMenu(entry)
            return
        }
        if (state.stage in listOf(PaletteStage.ENTITY, PaletteStage.BLOCK, PaletteStage.LOCATION) ||
            (state.stage == PaletteStage.ROOT && state.query.startsWith("tp "))) {
            val cmd = command(state.commandId) ?: commands.firstOrNull { it.id == "spotlight:tp" && it.input == CommandInput.LOCATION }
            if (cmd == null) showError("Location teleport command unavailable")
            else execute(cmd, text = entry.itemId ?: entry.id)
            return
        }
        if (entry.id.startsWith("item:")) {
            val cmd = command(state.commandId)
            flow.chooseItem(requireNotNull(entry.itemId), cmd?.input == CommandInput.ITEM_QUANTITY || state.give != null)
            if (cmd?.input == CommandInput.ITEM) execute(cmd) else enterStage()
        } else command(entry.id)?.let(::startCommand)
    }

    private fun startCommand(cmd: SpotlightCommand) {
        unavailable(cmd)?.let { showError(it); return }
        if (flow.state.stage == PaletteStage.ROOT) namedSubmission = flow.state.query
        when (cmd.input) {
            CommandInput.NONE -> execute(cmd)
            CommandInput.ITEM, CommandInput.ITEM_QUANTITY -> {
                if (flow.state.itemId != null && cmd.input == CommandInput.ITEM) execute(cmd)
                else { flow.chooseCommand(cmd.id, true, cmd.input == CommandInput.ITEM_QUANTITY); enterStage() }
            }
            else -> {
                flow.start(PaletteStage.valueOf(cmd.input.name), cmd.id)
                enterStage()
            }
        }
    }

    private fun runSlash(text: String) = attempt {
        Spotlight.executeSlash(text)
        CommandHistory.record(text)
        minecraft!!.setScreen(parent)
    }

    private fun enterStage() {
        if (flow.state.stage == PaletteStage.ROOT) namedSubmission = null
        error = null
        selected = 0
        offset = 0
        rebuildWidgets()
    }

    private fun execute(cmd: SpotlightCommand, quantity: Int = 1, text: String = "") {
        unavailable(cmd)?.let { showError(it); return }
        val itemId = flow.state.itemId
        if (cmd.input in listOf(CommandInput.ITEM, CommandInput.ITEM_QUANTITY) && itemId == null) {
            showError("Choose an item first")
            return
        }
        try {
            minecraft!!.setScreen(parent)
            cmd.execute(CommandContext(itemId, quantity, text))
        } catch (failure: Exception) {
            error = "${cmd.title}: ${failure.message ?: failure.javaClass.simpleName}"
            minecraft!!.setScreen(this)
            triggerImmediateNarration(true)
            return
        }
        CommandHistory.record(namedSubmission ?: flow.state.query)
        namedSubmission = null
        try {
            val saved = preferences ?: error("Preferences unavailable")
            if (itemId != null) saved.recordRecent("item:$itemId")
            saved.recordRecent(cmd.id)
        } catch (failure: Exception) {
            error = "Action completed; history not saved: ${failure.message ?: failure.javaClass.simpleName}"

            SystemToast.add(minecraft!!.toastManager, SystemToast.SystemToastId(),
                Component.literal("Spotlight: history not saved"), Component.literal(error!!))
        }
    }

    private fun attempt(action: () -> Unit) {
        try { action() } catch (failure: Exception) {
            showError(failure.message ?: failure.javaClass.simpleName)
        }
    }

    private fun editBinding(hotkey: Boolean, selectedCommand: SpotlightCommand? = selectedCommand()) = attempt {
        val cmd = selectedCommand ?: return@attempt
        val settings = Spotlight.settings()
        val current = (if (hotkey) settings.hotkeys else settings.aliases)[cmd.id].orEmpty()
        minecraft!!.setScreen(PromptScreen(this, "${if (hotkey) "Hotkey" else "Alias"}: ${cmd.title}", current,
            if (hotkey) "Type ${primaryModifierLabel}+G, ${primaryModifierLabel}+SHIFT+G, etc. Empty clears binding. CTRL stays Control; SUPER is Command." else "One command token; empty clears alias.") { value ->
            if (hotkey) settings.setHotkey(cmd.id, value) else settings.setAlias(cmd.id, value)
        })
    }

    private fun selectedLocation(entry: Entry? = currentEntry()): SavedLocation? {
        val id = entry?.itemId ?: return null
        return Spotlight.locations().list(Spotlight.scope()).firstOrNull { it.id == id }
    }

    private fun addLocation() = attempt {
        minecraft!!.setScreen(AddLocationScreen(this))
    }

    private fun editLocation(entry: Entry? = currentEntry()) = attempt {
        val location = selectedLocation(entry) ?: error("Select a location first")
        minecraft!!.setScreen(LocationEditScreen(this, location))
    }

    private fun deleteLocation(entry: Entry? = currentEntry()) = attempt {
        val location = selectedLocation(entry) ?: error("Select a location first")
        minecraft!!.setScreen(ConfirmSpotlightScreen(this,
            "Delete location ${location.name}?",
            "This permanently deletes this saved location. Other worlds are not changed.") {
            attempt { Spotlight.locations().remove(location.scope, location.id); refresh() }
        })
    }

    private fun openLocationMenu(entry: Entry) = attempt {
        val location = selectedLocation(entry) ?: error("Select a location first")
        val teleport = commands.firstOrNull { it.id == "spotlight:tp" && it.input == CommandInput.LOCATION }
        val actions = listOf(
            PaletteMenuAction("Teleport", minecraft!!.player != null && teleport != null && unavailable(teleport) == null,
                "Enter", "Location") {
                if (teleport != null) execute(teleport, text = location.id)
            },
            PaletteMenuAction("Edit location", shortcut = "${primaryModifierLabel}+E", group = "Location") { editLocation(entry) },
            PaletteMenuAction("Copy coordinates", group = "More") {
                minecraft!!.keyboardHandler.clipboard = "${location.x} ${location.y} ${location.z}"
                SystemToast.add(minecraft!!.toastManager, SystemToast.SystemToastId(),
                    Component.literal("Spotlight: coordinates copied"), Component.literal(location.name))
            },
            PaletteMenuAction("Delete location (confirm)", shortcut = "${primaryModifierLabel}+Shift+D", group = "More") {
                deleteLocation(entry)
            },
        )
        minecraft!!.setScreen(CommandMenuScreen(this, actions))
    }

    private fun canFavorite(): Boolean = currentEntry()?.let {
        preferences != null && !it.id.startsWith("calculator:") && !it.id.startsWith("slash:")
    } == true

    private fun toggleFavorite() {
        if (!canFavorite()) return
        val entry = currentEntry() ?: return
        try {
            val saved = preferences ?: error("Preferences unavailable")
            saved.toggleFavorite(entry.id)
            val id = entry.id
            refresh()
            selected = results.indexOfFirst { it.id == id }.coerceAtLeast(0)
            keepVisible()
            rebuildRows()
            updateSelection()
        } catch (failure: Exception) {
            showError("Favorite not saved: ${failure.message ?: failure.javaClass.simpleName}")
        }
    }

    private fun showError(message: String) {
        error = message
        updateSelection()
        triggerImmediateNarration(true)
    }

    override fun onClose() {
        if (flow.back()) enterStage() else minecraft!!.setScreen(parent)
    }

    override fun tick() {
        super.tick()
        nowPlaying?.tick(font)
        val focusedRow = rows.indexOfFirst { it.isFocused }
        if (focusedRow >= 0 && selected != offset + focusedRow) {
            selected = offset + focusedRow
            updateSelection()
        }
    }

    internal fun openMacroTab(index: Int) {
        if (index == 2) minecraft!!.setScreen(MacroListScreen(this))
        else switchTab(index == 1)
    }

    private fun switchTab(locations: Boolean) {
        flow.start(if (locations) PaletteStage.LOCATIONS else PaletteStage.ROOT)
        enterStage()
    }

    private fun footerHints(): String =
        "${if (flow.state.stage == PaletteStage.ROOT && !flow.state.query.startsWith('/')) "↑ History · ↓ Results" else "↑↓ Navigate"} · Enter ${if (flow.state.stage == PaletteStage.LOCATIONS) "Actions" else "Run"} · " +
            (if (flow.state.query.startsWith('/')) "Tab Complete · " else "") +
            "Esc Back · Right-click / ${primaryModifierLabel}+K Actions"

    private fun handleShortcut(shortcut: PaletteShortcut) {
        when (shortcut) {
            PaletteShortcut.ALL -> switchTab(false)
            PaletteShortcut.LOCATIONS -> switchTab(true)
            PaletteShortcut.MACROS -> openMacroTab(2)
            PaletteShortcut.CYCLE_TABS -> openMacroTab(cyclePaletteTab(if (flow.state.stage == PaletteStage.LOCATIONS) 1 else 0, false))
            PaletteShortcut.MENU -> openMenu()
            PaletteShortcut.ALIAS -> editBinding(false)
            PaletteShortcut.HOTKEY -> editBinding(true)
            PaletteShortcut.FAVORITE -> toggleFavorite()
            PaletteShortcut.CATALOGS -> minecraft!!.setScreen(CatalogScreen(this))
            PaletteShortcut.SELECTOR -> if (!openSelector()) showError("Place cursor on a selector in a /command first")
            PaletteShortcut.ADD -> addLocation()
            PaletteShortcut.EDIT -> editLocation()
            PaletteShortcut.DELETE -> deleteLocation()
            PaletteShortcut.SYNC -> attempt { error = Spotlight.syncLocations(); refresh() }
            PaletteShortcut.CLOSE -> minecraft!!.setScreen(parent)
        }
    }

    private fun openMenu() {
        val cmd = selectedCommand()
        val entry = currentEntry()
        val actions = mutableListOf(
            PaletteMenuAction("All", shortcut = "${primaryModifierLabel}+1", group = "Navigation") { switchTab(false) },
            PaletteMenuAction("Locations", shortcut = "${primaryModifierLabel}+2", group = "Navigation") { switchTab(true) },
            PaletteMenuAction("Macros", shortcut = "${primaryModifierLabel}+3", group = "Navigation") { openMacroTab(2) },
            PaletteMenuAction("Cycle tabs", shortcut = "${primaryModifierLabel}+Tab / ${primaryModifierLabel}+Shift+Tab", group = "Navigation") { handleShortcut(PaletteShortcut.CYCLE_TABS) },
            PaletteMenuAction("Focus search", group = "Navigation") { setFocused(input) },
            PaletteMenuAction("Back", shortcut = "Esc", group = "Navigation") { onClose() },
            PaletteMenuAction("Close Spotlight", shortcut = "${primaryModifierLabel}+W", group = "Navigation") { minecraft!!.setScreen(parent) },
            PaletteMenuAction(if (currentEntry()?.id in preferences?.favorites.orEmpty()) "Unfavorite" else "Favorite",
                canFavorite(), "${primaryModifierLabel}+F", "Command") { toggleFavorite() },
            PaletteMenuAction("Set alias", cmd != null, "${primaryModifierLabel}+Shift+A", "Command") { editBinding(false, cmd) },
            PaletteMenuAction("Set hotkey", cmd != null, "${primaryModifierLabel}+Shift+H", "Command") { editBinding(true, cmd) },
        )
        if (flow.state.stage == PaletteStage.LOCATIONS) {
            val location = selectedLocation(entry) != null
            actions += listOf(
                PaletteMenuAction("Add location", minecraft!!.player != null, "${primaryModifierLabel}+N", "Locations") { addLocation() },
                PaletteMenuAction("Edit location", location, "${primaryModifierLabel}+E", "Locations") { editLocation(entry) },
                PaletteMenuAction("Delete location (confirm)", location, "${primaryModifierLabel}+Shift+D", "Locations") { deleteLocation(entry) },
                PaletteMenuAction("Sync locations", shortcut = "${primaryModifierLabel}+S", group = "Locations") { handleShortcut(PaletteShortcut.SYNC) },
            )
        } else actions += PaletteMenuAction("Edit selector",
            selectorSpan(input.value, input.cursorPosition) != null && input.value.startsWith('/'), "${primaryModifierLabel}+E", "Command") {
            handleShortcut(PaletteShortcut.SELECTOR)
        }
        actions += PaletteMenuAction("Data lists", shortcut = "${primaryModifierLabel}+L", group = "Tools") { handleShortcut(PaletteShortcut.CATALOGS) }
        minecraft!!.setScreen(CommandMenuScreen(this, actions))
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val event = MouseButtonEvent(mouseX, mouseY, button)

        if (nowPlaying?.button?.let { it.visible && it.isMouseOver(event.x(), event.y()) } == true)
            return super.mouseClicked(event.x(), event.y(), event.button())
        if (opensPaletteMenu(event.button(),
                event.x() >= left && event.x() < left + panelWidth && event.y() >= top && event.y() < bottom,
                input.isMouseOver(event.x(), event.y()))) {
            val index = rows.indexOfFirst { it.isMouseOver(event.x(), event.y()) }
            if (index >= 0) selected = offset + index
            setFocused(input)
            updateSelection()
            openMenu()
            return true
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            val index = rows.indexOfFirst { it.isMouseOver(event.x(), event.y()) }
            if (index >= 0) {
                if (rows[index].active) results.getOrNull(offset + index)?.let { activateRow(it.id) }
                return true
            }
        }
        return super.mouseClicked(event.x(), event.y(), event.button())
    }

    internal fun handlePaletteShortcut(event: KeyEvent): Boolean {
        if (event.key() == InputConstants.KEY_TAB && primaryModifier(event) && !event.hasAltDown()) {
            openMacroTab(cyclePaletteTab(if (flow.state.stage == PaletteStage.LOCATIONS) 1 else 0, event.hasShiftDown()))
            return true
        }
        val shortcut = paletteShortcut(event.key(), primaryModifier(event), event.hasShiftDown(), event.hasAltDown(),
            flow.state.stage == PaletteStage.LOCATIONS) ?: return false
        if (minecraft!!.screen !== this) minecraft!!.setScreen(this)
        handleShortcut(shortcut)
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val event = KeyEvent(keyCode, scanCode, modifiers)
        if (handlePaletteShortcut(event)) return true
        when {
            event.key() == InputConstants.KEY_TAB && !event.hasShiftDown() && !event.hasAltDown() &&
                input.isFocused && input.value.startsWith('/') -> {
                completeSlash(); return true
            }
            event.key() == InputConstants.KEY_TAB && !event.hasAltDown() -> {
                if (results.isNotEmpty()) {
                    val focusedRow = rows.indexOfFirst { it.isFocused }
                    selected = if (focusedRow < 0) selected else
                        Math.floorMod(offset + focusedRow + if (event.hasShiftDown()) -1 else 1, results.size)
                    keepVisible()
                    rebuildRows()
                    updateSelection()
                    rows.getOrNull(selected - offset)?.let { setFocused(it) }
                    triggerImmediateNarration(true)
                }
                return true
            }
            event.isEscape -> { onClose(); return true }

            (event.isUp || event.isDown) && (input.isFocused || rows.any { it.isFocused }) -> {
                if (flow.state.stage == PaletteStage.ROOT) {
                    if (event.isUp) {
                        history.up(input.value, CommandHistory::snapshot)?.let {
                            setQuery(it)
                            setFocused(input)
                            return true
                        }
                    } else {
                        history.down()?.let { setQuery(it) }
                    }
                }
                if (results.isNotEmpty()) {
                    selected = Math.floorMod(selected + if (event.isDown) 1 else -1, results.size)
                    keepVisible()
                    rebuildRows()
                    updateSelection()
                    setFocused(input)
                    triggerImmediateNarration(true)
                }
                return true
            }

            event.isConfirmation && input.isFocused -> { activate(); return true }
            event.isConfirmation && rows.any { it.isFocused } -> {
                selected = offset + rows.indexOfFirst { it.isFocused }
                activate()
                return true
            }
        }
        return super.keyPressed(event.key(), event.scancode(), event.modifiers())
    }

    override fun charTyped(character: Char, modifiers: Int): Boolean {
        val event = CharacterEvent(character.code, modifiers)
        if (!input.isFocused) setFocused(input)
        return super.charTyped(event.codepoint().toChar(), event.modifiers())
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        if (mouseX >= left && mouseX < left + panelWidth && mouseY >= listTop && mouseY < listTop + visibleRows * rowHeight) {
            val next = (offset - if (vertical > 0) 1 else if (vertical < 0) -1 else 0)
                .coerceIn(0, (results.size - visibleRows).coerceAtLeast(0))
            if (offset != next) {
                offset = next
                selected = selected.coerceIn(offset, minOf(results.lastIndex, offset + visibleRows - 1))
                rebuildRows()
                updateSelection()
            }
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical)
    }

    private fun clipped(text: String, maxWidth: Int): String =
        if (font.width(text) <= maxWidth) text else font.plainSubstrByWidth(text, (maxWidth - font.width("…")).coerceAtLeast(0)) + "…"

    override fun getNarrationMessage(): Component = Component.literal("Spotlight. " +
        if (::input.isInitialized) input.message.string else "Search commands and items")

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, SpotlightTheme.color(0x99060A12))
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(left, top, left + panelWidth, bottom, SpotlightTheme.color(0xFA101722))
        graphics.renderOutline(left, top, panelWidth, panelHeight, SpotlightTheme.color(0xFF415877))
        graphics.enableScissor(left + 4, top, left + panelWidth - 4, bottom)
        val heading = when (flow.state.stage) {
            PaletteStage.ROOT -> "Spotlight"
            PaletteStage.ITEMS -> "${command(flow.state.commandId)?.title.orEmpty()} › Choose item"
            PaletteStage.ACTIONS -> "${items.firstOrNull { it.itemId == flow.state.itemId }?.title ?: flow.state.itemId} › Choose action"
            PaletteStage.QUANTITY -> "${command(flow.state.commandId)?.title ?: "/give ${flow.state.give?.target}"} › Quantity"
            PaletteStage.NUMBER -> "${command(flow.state.commandId)?.title} › Number"
            PaletteStage.ENTITY -> "${command(flow.state.commandId)?.title} › Entity"
            PaletteStage.BLOCK -> "${command(flow.state.commandId)?.title} › Block"
            PaletteStage.LOCATION -> "${command(flow.state.commandId)?.title} › Location"
            PaletteStage.LOCATIONS -> "Locations"
        }
        graphics.themeText(font, clipped(heading, (panelWidth - 24).coerceAtLeast(0)), left + 12, top + 12, SpotlightTheme.color(0xFFF2F5FC))
        super.render(graphics, mouseX, mouseY, delta)
        if (results.isEmpty() && listTop + 20 < footerTop) graphics.themeText(font, argumentHint() ?: if (input.value.trim() == "/seed") "Custom seed copies known full seed · singleplayer only" else if (input.value.startsWith('/')) "Enter runs valid command · Tab completes · ${primaryModifierLabel}+E edits target" else "No matches. Try an item ID or shorter query.", left + 15, listTop + 8, SpotlightTheme.color(0xFF9DADC3))
        val status = error ?: themeWarning ?: argumentHint() ?: (if (flow.state.stage == PaletteStage.LOCATIONS) Spotlight.locationSyncStatus() else null) ?: currentEntry()?.let(::detail) ?: "Search commands and every loaded item"
        graphics.fill(left + 8, footerTop - 3, left + panelWidth - 8, footerTop - 2, SpotlightTheme.color(0xFF243146))
        graphics.enableScissor(left + 12, footerTop, left + 12 + footerTextWidth, bottom)
                graphics.themeText(font, clipped(status, footerTextWidth), left + 12, footerTop,
            if (error != null || themeWarning != null) SpotlightTheme.color(0xFFFFA49A) else SpotlightTheme.color(0xFF9DADC3))
        font.split(Component.literal(footerHints()), footerTextWidth.coerceAtLeast(1)).take(footerLines.size).forEachIndexed { index, line ->
            graphics.themeText(font, line, left + 12, footerTop + 13 + index * font.lineHeight, SpotlightTheme.color(0xFF9DADC3))
        }
        graphics.disableScissor()
        if (results.size > visibleRows) {
            val track = visibleRows * rowHeight - 2
            val thumb = (track * visibleRows / results.size).coerceAtLeast(6).coerceAtMost(track)
            val y = listTop + (track - thumb) * offset / (results.size - visibleRows)
            graphics.fill(left + panelWidth - 9, listTop, left + panelWidth - 7, listTop + track, SpotlightTheme.color(0xFF243146))
            graphics.fill(left + panelWidth - 9, y, left + panelWidth - 7, y + thumb, SpotlightTheme.color(0xFF81B8FF))
        }
        graphics.disableScissor()
        if (error != null && mouseX >= left + 12 && mouseX < left + 12 + footerTextWidth && mouseY >= footerTop && mouseY < footerTop + 12)
            graphics.setTooltipForNextFrame(font, Component.literal(error!!), mouseX, mouseY)
    }
}
