package dev.spotlight.ui

import dev.spotlight.platform.WikiSearch
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.ConfirmLinkScreen
import net.minecraft.client.gui.screens.Screen
import java.util.concurrent.CompletableFuture

class WikiScreen(private val parent: Screen?) : Screen(catalogText("Minecraft Wiki")) {
    private lateinit var query: EditBox
    private val rows = mutableListOf<Button>()
    private var draft = ""
    private var results = emptyList<WikiSearch.Result>()
    private var request: CompletableFuture<List<WikiSearch.Result>>? = null
    private var generation = 0
    private var status = "Enter search terms, then Search"
    private var page = 0
    private var left = 0
    private var panelWidth = 0
    private var rowCount = 1

    override fun isPauseScreen() = false
    override fun init() {
        rows.clear()
        panelWidth = minOf(580, (width - 24).coerceAtLeast(1))
        left = (width - panelWidth) / 2
        rowCount = ((height - 190) / 24).coerceIn(1, 10)
        query = addRenderableWidget(SpotlightEditBox(font, left, 48, panelWidth, 20, catalogText("Wiki search terms")))
        query.setMaxLength(200)
        query.value = draft
        query.setResponder { draft = it; cancelRequest(); results = emptyList(); page = 0; status = "Search terms changed; press Search"; rebuildRows() }
        addRenderableWidget(catalogButton("Search in browser", left, 100, panelWidth) {
            try {
                confirmExternalLink(WikiSearch.browserSearchUrl(query.value))
            } catch (failure: IllegalArgumentException) { status = failure.message ?: "Invalid search" }
        })
        addRenderableWidget(catalogButton("Search", left, 74, panelWidth / 2 - 2) { search() })
        addRenderableWidget(catalogButton("Cancel request", left + panelWidth / 2 + 2, 74, panelWidth / 2 - 2) { cancelRequest(); status = "Search cancelled" })
        addRenderableWidget(catalogButton("Previous", left, height - 54, panelWidth / 3 - 2) { page = (page - 1).coerceAtLeast(0); rebuildRows() })
        addRenderableWidget(catalogButton("Next", left + panelWidth / 3, height - 54, panelWidth / 3 - 2) { page++; rebuildRows() })
        addRenderableWidget(catalogButton("Back", left + panelWidth * 2 / 3, height - 54, panelWidth / 3) { onClose() })
        rebuildRows()
        setInitialFocus(query)
    }

    private fun cancelRequest() { generation++; request?.cancel(true); request = null }

    private fun search() {
        cancelRequest()
        val token = generation
        results = emptyList()
        page = 0
        status = "Searching minecraft.wiki…"
        rebuildRows()
        try {
            val pending = WikiSearch.search(query.value)
            request = pending
            pending.whenComplete { values, failure ->
                minecraft!!.execute {
                    if (token != generation || minecraft!!.screen !== this) return@execute
                    request = null
                    results = values ?: emptyList()
                    status = if (failure != null) "Search failed: ${failure.cause?.message ?: failure.message ?: failure.javaClass.simpleName}"
                        else if (results.isEmpty()) "No suggestions; use Search in browser for full wiki search"
                                                else "${results.size} results; select title to confirm browser link"
                    rebuildRows()
                    triggerImmediateNarration(true)
                }
            }
        } catch (failure: IllegalArgumentException) { status = failure.message ?: "Invalid search" }
    }

    private fun rebuildRows() {
        rows.forEach(::removeWidget)
        rows.clear()
        page = page.coerceIn(0, ((results.size - 1) / rowCount).coerceAtLeast(0))
        results.drop(page * rowCount).take(rowCount).forEachIndexed { index, result ->
            rows += addRenderableWidget(catalogButton(result.title, left, 128 + index * 24, panelWidth) {
                WikiSearch.articleUrl(result.url.toString())?.let { confirmExternalLink(it) }
            })
        }
    }

    override fun removed() { cancelRequest(); super.removed() }
    override fun onClose() { cancelRequest(); minecraft!!.setScreen(parent) }
    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val event = KeyEvent(keyCode, scanCode, modifiers)
        if (event.isConfirmation && query.isFocused) { search(); return true }
        return super.keyPressed(event.key(), event.scancode(), event.modifiers())
    }
    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, SpotlightTheme.color(0xF0101722))
        graphics.themeText(font, title, left, 12, SpotlightTheme.color(0xFFF2F5FC))
        graphics.themeText(font, catalogClip(font, "Search sends only typed terms to minecraft.wiki; no inventory or coordinates.", panelWidth), left, 28, SpotlightTheme.color(0xFF9DADC3))
        graphics.themeText(font, catalogClip(font, status, panelWidth), left, height - 72, SpotlightTheme.color(0xFFF2F5FC))
        super.render(graphics, mouseX, mouseY, delta)
        if (mouseY in 26..40) graphics.setTooltipForNextFrame(catalogText("Only Search / Enter sends typed terms to https://minecraft.wiki/api.php. No keystroke requests, API key, inventory or coordinates. Links require confirmation."), mouseX, mouseY)
        if (mouseY in height - 74..height - 60) graphics.setTooltipForNextFrame(catalogText(status), mouseX, mouseY)
    }
}
