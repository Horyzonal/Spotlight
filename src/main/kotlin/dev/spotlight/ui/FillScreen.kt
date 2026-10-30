package dev.spotlight.ui

import dev.spotlight.commands.regionCommandUnavailable
import dev.spotlight.core.Entry
import dev.spotlight.core.Fuzzy
import dev.spotlight.platform.RegionSelection
import dev.spotlight.platform.Spotlight
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

internal fun fillBlockIcon(id: String): ItemStack {
    val key = ResourceLocation.tryParse(id) ?: return ItemStack.EMPTY
    if (!BuiltInRegistries.BLOCK.containsKey(key)) return ItemStack.EMPTY
    return ItemStack(requireNotNull(BuiltInRegistries.BLOCK.get(key)).asItem())
}

internal fun fillModes(advertised: List<String>): List<String> {
    val vanilla = listOf("replace", "destroy", "keep", "hollow", "outline")
    return vanilla.filter { it in advertised }.ifEmpty { vanilla }
}

internal fun fillModeHelp(mode: String): String = when (mode) {
    "destroy" -> "Break existing blocks and drop their items."
    "keep" -> "Fill air only; preserve existing blocks."
    "hollow" -> "Fill boundary; clear interior to air."
    "outline" -> "Fill boundary; preserve interior."
    else -> "Replace all blocks in region."
}

class FillScreen(private val parent: Screen?) : Screen(catalogText("fill")) {
    private var entries = emptyList<Entry>()
    private var results = emptyList<Entry>()
    private val rows = mutableListOf<Button>()
    private lateinit var search: EditBox
    private var query = ""
    private var filterTarget = false
    private var chosenBlock: String? = null
    private var replaceBlock: String? = null
    private val queries = arrayOf("", "")
    private val selections = intArrayOf(0, 0)
    private val offsets = intArrayOf(0, 0)
    private lateinit var fillTargetButton: Button
    private lateinit var filterTargetButton: Button
    private lateinit var anyBlockButton: Button
    private var selected = 0
    private var offset = 0
    private var count = 0
    private var left = 0
    private var panelWidth = 1
    private var mode = "replace"
    private var modes = fillModes(emptyList())
    private var error: String? = null
    private var ticks = 0
    private val rowStep get() = SpotlightTheme.metric("compactRowHeight", 24, 22, 80)

    override fun isPauseScreen() = false

    override fun init() {
        rows.clear()
        panelWidth = minOf(SpotlightTheme.metric("selectorWidth", 520, 160, 4096), (width - 16).coerceAtLeast(1))
        left = (width - panelWidth) / 2
        count = ((height - 218) / rowStep).coerceIn(0, 12)
        modes = fillModes(minecraft!!.connection?.commands?.root?.getChild("fill")?.getChild("from")
            ?.getChild("to")?.getChild("block")?.children.orEmpty().map { it.name })
        if (mode !in modes) mode = modes.first()
        entries = Spotlight.blocks()
        fillTargetButton = addRenderableWidget(catalogButton("Fill with", left, 28, panelWidth / 2 - 2) { target(false) })
        filterTargetButton = addRenderableWidget(catalogButton("Replace only", left + panelWidth / 2 + 2, 28,
            panelWidth / 2 - 2) { target(true) })
        search = addRenderableWidget(SpotlightEditBox(font, left, 56, panelWidth, 20, catalogText("Search blocks or registry IDs")))
        search.setMaxLength(32768)
        search.setHint(catalogText("Search blocks or registry IDs"))
        search.value = query
        search.setResponder { query = it; selected = 0; offset = 0; error = null; refresh() }
        lateinit var modeButton: Button
        modeButton = addRenderableWidget(catalogButton("Mode: $mode", left, height - 128, panelWidth * 2 / 3 - 2) {
            val next = modes[(modes.indexOf(mode) + 1) % modes.size]
            if (replaceBlock != null && next != "replace") {
                error = "Clear Replace only with Any block before changing mode"
                refresh()
                triggerImmediateNarration(true)
            } else {
                mode = next
                if (mode != "replace" && filterTarget) target(false)
                modeButton.message = catalogText("Mode: $mode. ${fillModeHelp(mode)}")
                refresh()
            }
        })
        anyBlockButton = addRenderableWidget(catalogButton("Any block", left + panelWidth * 2 / 3 + 2,
            height - 128, panelWidth / 3 - 2) {
            replaceBlock = null
            error = null
            refresh()
        })
        addRenderableWidget(catalogButton("Back (Esc)", left, height - 54, panelWidth / 3 - 2) { onClose() })
        addRenderableWidget(catalogButton("Start selection (Enter / $primaryModifierLabel+Enter)", left + panelWidth / 3 + 2,
            height - 54, panelWidth * 2 / 3 - 2) { start() })
        refresh()
        setInitialFocus(search)
    }

    override fun tick() {
        super.tick()
        if (++ticks % 20 != 0) return
        val current = Spotlight.blocks()
        if (current != entries) {
            val id = results.getOrNull(selected)?.id
            entries = current
            refresh(id)
        }
    }

    private fun target(filter: Boolean) {
        if (filter == filterTarget || (filter && mode != "replace")) return
        if (!filterTarget && chosenBlock == null) chosenBlock = results.getOrNull(selected)?.detail
        val old = if (filterTarget) 1 else 0
        queries[old] = query
        selections[old] = selected
        offsets[old] = offset
        filterTarget = filter
        val next = if (filter) 1 else 0
        query = queries[next]
        selected = selections[next]
        offset = offsets[next]
        search.setResponder { }
        search.value = query
        search.setResponder { query = it; selected = 0; offset = 0; error = null; refresh() }
        error = null
        refresh()
        setFocused(search)
    }

    private fun choose() {
        val block = results.getOrNull(selected)?.detail ?: return
        if (filterTarget) replaceBlock = block else chosenBlock = block
        error = null
    }

    private fun refresh(keepId: String? = null) {
        val hadRowFocus = rows.any { it.isFocused }
        rows.forEach(::removeWidget)
        rows.clear()
        results = Fuzzy.search(query, entries, limit = entries.size)
        if (keepId != null) selected = results.indexOfFirst { it.id == keepId }.takeIf { it >= 0 } ?: selected
        selected = selected.coerceIn(0, (results.size - 1).coerceAtLeast(0))
        offset = offset.coerceIn((selected - count.coerceAtLeast(1) + 1).coerceAtLeast(0), selected)
        results.drop(offset).take(count).forEachIndexed { index, entry ->
            rows += addRenderableWidget(catalogButton("      ${if (offset + index == selected) "• " else ""}${entry.title} · ${entry.detail}",
                left, 84 + index * rowStep, panelWidth) {
                selected = offset + index
                choose()
                refresh()
                setFocused(search)
            })
        }
        fillTargetButton.message = catalogText(
            "${if (!filterTarget) "• " else ""}Fill with: ${chosenBlock ?: if (!filterTarget) results.getOrNull(selected)?.detail ?: "Choose block" else "Choose block"}")
        filterTargetButton.message = catalogText(
            "${if (filterTarget) "• " else ""}Replace only: ${replaceBlock ?: "Any block"}")
        filterTargetButton.active = mode == "replace"
        anyBlockButton.active = replaceBlock != null
        search.message = catalogText("${if (filterTarget) "Replace only" else "Fill with"}. Search blocks or registry IDs. " +
            "${results.getOrNull(selected)?.let { "${it.title}, ${it.detail}" } ?: "No matches"}. ${error.orEmpty()}")
        if (hadRowFocus) setFocused(search)
    }

    private fun move(step: Int) {
        if (results.isEmpty()) return
        selected = (selected + step).coerceIn(0, results.lastIndex)
        choose()
        refresh()
    }

    private fun start() {
        try {
            regionCommandUnavailable("fill")?.let { error(it) }
            val block = requireNotNull(chosenBlock ?: results.getOrNull(selected)?.detail.takeUnless { filterTarget }) {
                "Choose a matching fill block first"
            }
            val available = Spotlight.blocks()
            require(available.any { it.detail == block }) { "Fill block no longer available" }
            require(replaceBlock == null || available.any { it.detail == replaceBlock }) { "Replacement block no longer available" }
            RegionSelection.startFill(block, mode, replaceBlock)
        } catch (failure: Exception) {
            error = failure.message ?: "Cannot start fill selection"
            search.message = catalogText("Search blocks. $error")
            triggerImmediateNarration(true)
        }
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val event = KeyEvent(keyCode, scanCode, modifiers)
        if (event.isEscape) { onClose(); return true }
        if (event.isConfirmation && !event.hasAltDown() && (search.isFocused || primaryModifier(event))) {
            if (filterTarget && !primaryModifier(event)) { choose(); refresh() } else start()
            return true
        }
        if (search.isFocused && !primaryModifier(event) && !event.hasAltDown() && (event.isDown || event.isUp)) {
            move(if (event.isDown) 1 else -1); return true
        }
        return super.keyPressed(event.key(), event.scancode(), event.modifiers())
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        if (vertical != 0.0) { move(if (vertical > 0) -1 else 1); return true }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical)
    }

    override fun onClose() { minecraft!!.setScreen(parent) }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, SpotlightTheme.color(0xF0101722))
        graphics.themeText(font, catalogText("fill · choose block and mode"), left, 12, SpotlightTheme.color(0xFFF2F5FC))
        super.render(graphics, mouseX, mouseY, delta)
        results.drop(offset).take(count).forEachIndexed { index, entry ->
            val stack = fillBlockIcon(entry.detail)
            if (!stack.isEmpty) graphics.renderItem(stack, left + 3, 86 + index * rowStep)
        }
        val help = listOf(
            error ?: results.getOrNull(selected)?.detail ?: "No matching blocks",
            if (mode == "replace") "Replace only: ${replaceBlock ?: "Any block"} · Any block clears filter" else fillModeHelp(mode),
            "LMB first corner · RMB second · Enter send · Esc cancels",
            "Selection uses existing flight mode only; server checks permissions.",
        )
        help.forEachIndexed { index, text ->
            graphics.themeText(font, catalogClip(font, text, panelWidth), left, height - 104 + index * 11,
                SpotlightTheme.color(if (error != null && index == 0) 0xFFFFB4A9 else 0xFF9DADC3))
        }
        catalogHints(graphics, font, width, height, "↑↓ Choose · Tab Next control · Enter Choose filter / Start · $primaryModifierLabel+Enter Start · Esc Back")
    }
}
