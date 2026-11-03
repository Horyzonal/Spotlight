package dev.spotlight.ui

import com.mojang.blaze3d.platform.InputConstants

import dev.spotlight.platform.HeadDatabase
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.world.item.ItemStack
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class HeadDatabaseScreen(private val parent: Screen? = null, private val online: Boolean = true) : Screen(catalogText(if (online) "Head database" else "Server heads")) {
    companion object {
        private val searches = Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "spotlight-head-search").apply { isDaemon = true } }
    }
    private var query = ""
    private var results = emptyList<HeadDatabase.Head>()
    @Volatile private var searchGeneration = 0
    private var pendingSearch: Future<*>? = null
    private var searching = false
    private var loadingOnline = false
    private val previews = object : LinkedHashMap<HeadDatabase.Head, ItemStack>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<HeadDatabase.Head, ItemStack>) = size > 256
    }

    private var category: String? = null
    private var page = 0
    private var notice = "Click head to give · Creative mode required"
    private var source = emptyList<HeadDatabase.Head>()
    private val cells = mutableListOf<AbstractWidget>()
    private var generation = 0
    private var left = 0
    private var panelWidth = 0
    private var columns = 1
    private var capacity = 1
    private var matches = 0

    override fun isPauseScreen() = false
    override fun onClose() { generation++; minecraft.setScreen(parent) }
    override fun removed() {
        generation++
        loadingOnline = false
        searchGeneration++
        pendingSearch?.cancel(true)
        super.removed()
    }
    override fun init() {
        cells.clear()
        panelWidth = minOf(1000, (width - 16).coerceAtLeast(1))
        left = (width - panelWidth) / 2
        val search = addRenderableWidget(SpotlightEditBox(font, left, 26, panelWidth, 20, catalogText("Search loaded head names and tags")))
        search.setMaxLength(256)
        search.value = query
        search.setHint(catalogText("Search loaded head names and tags"))
        search.setResponder { query = it; page = 0; searchSource(150) }
        val buttonWidth = panelWidth / 4
        addRenderableWidget(catalogButton(if (online) "Refresh online" else "Refresh server heads", left, 50, panelWidth) {
            if (online) loadOnline(true) else loadSource()
        })
        val categories = if (online) listOf<String?>(null) + HeadDatabase.categories else emptyList()
        val categoryColumns = minOf(6, maxOf(1, panelWidth / 110))
        categories.forEachIndexed { index, key ->
            val label = key?.replace('-', ' ')?.replaceFirstChar { it.uppercase() } ?: "All heads"
            val button = addRenderableWidget(catalogButton(label, left + index % categoryColumns * (panelWidth / categoryColumns), 74 + index / categoryColumns * 22, panelWidth / categoryColumns - 2) {
                category = key; page = 0; loadOnline(false)
            })
            button.setTooltip(Tooltip.create(catalogText(if (key == null) "All provider categories; online loads run sequentially" else "Provider category: $key")))
        }
        val top = 80 + ((categories.size + categoryColumns - 1) / categoryColumns) * 22
        columns = maxOf(1, panelWidth / 64)
        capacity = columns * maxOf(0, (height - top - 64) / 56)
        repeat(capacity) { index ->
            val cell = HeadCell(left + index % columns * (panelWidth / columns), top + index / columns * 56, panelWidth / columns - 2)
            cells += addRenderableWidget(cell)
        }
        listOf("Previous", "Next", "Player skull", "Back").forEachIndexed { index, label ->
            addRenderableWidget(catalogButton(label, left + index * buttonWidth, height - 54, buttonWidth - 2) {
                when (index) {
                    0 -> { page = (page - 1).coerceAtLeast(0); refreshGrid() }
                    1 -> { if ((page + 1) * capacity < matches) page++; refreshGrid() }
                    2 -> minecraft.setScreen(PlayerSkullScreen(this))
                    3 -> onClose()
                }
            })
        }
        if (online) loadOnline(false) else loadSource()
        setInitialFocus(search)
    }
    private fun loadSource() {
        if (!online) runCatching { source = HeadDatabase.entries(false) }.onFailure { source = emptyList(); notice = it.message ?: "Heads unavailable" }
        searchSource(0)
    }
    private fun searchSource(delay: Long) {
        val token = ++searchGeneration
        pendingSearch?.cancel(true)
        val selectedCategory = category
        val selectedQuery = query
        val snapshot = source
        val connection = minecraft.connection
        searching = true
        pendingSearch = searches.schedule({
            val outcome = runCatching {
                val entries = if (online) HeadDatabase.entries(true, selectedCategory) else snapshot
                HeadDatabase.search(selectedQuery, entries) { searchGeneration != token || Thread.currentThread().isInterrupted }
            }
            if (searchGeneration == token) minecraft.execute {
                if (searchGeneration != token || minecraft.screen !== this || minecraft.connection !== connection) return@execute
                searching = false
                outcome.onSuccess { results = it }.onFailure { results = emptyList(); notice = it.message ?: "Search failed" }
                refreshGrid()
            }
        }, delay, TimeUnit.MILLISECONDS)
    }
    private fun refreshGrid() {
        matches = results.size
        page = page.coerceIn(0, (matches - 1).coerceAtLeast(0) / capacity.coerceAtLeast(1))
        cells.forEachIndexed { index, widget -> (widget as HeadCell).setHead(results.getOrNull(page * capacity + index)) }
    }
    override fun tick() {
        super.tick()
        val deadline = System.nanoTime() + 3_000_000
        var decoded = 0
        for (widget in cells) {
            if ((widget as HeadCell).loadPreview()) decoded++
            if (decoded >= 8 || System.nanoTime() >= deadline) break
        }
    }
    override fun mouseScrolled(x: Double, y: Double, horizontal: Double, vertical: Double): Boolean {
        val top = cells.firstOrNull()?.y ?: return super.mouseScrolled(x, y, horizontal, vertical)
        if (x < left || x >= left + panelWidth || y < top || y >= height - 64 || vertical == 0.0)
            return super.mouseScrolled(x, y, horizontal, vertical)
        page = (page + if (vertical < 0) 1 else -1).coerceIn(0, (matches - 1).coerceAtLeast(0) / capacity.coerceAtLeast(1))
        refreshGrid()
        return true
    }
    private fun loadOnline(force: Boolean) {
        if (loadingOnline) { loadSource(); return }
        loadingOnline = true
        generation++

        val connection = minecraft.connection
        val selectedCategory = category
        val token = generation
        HeadDatabase.refreshRemote(selectedCategory, force).whenComplete { _, error -> minecraft.execute {
            if (minecraft.screen !== this || minecraft.connection !== connection || generation != token || !online) return@execute
            loadingOnline = false
            notice = if (error == null) "Loaded ${selectedCategory ?: "all categories"}" else "Load failed: ${dev.spotlight.platform.RemoteHeads.failureMessage(error)}; cached heads retained"
            if (category != selectedCategory) loadOnline(false) else loadSource()
        } }
        loadSource()
    }
    override fun extractRenderState(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        g.fill(0, 0, width, height, SpotlightTheme.color(0xF0101722))
        super.extractRenderState(g, mouseX, mouseY, delta)
        g.themeText(font, "${if (online) "Head database" else "Server heads"} · ${if (online) category ?: "All heads" else "Client-visible heads"} · $matches matches${if (searching) " · Searching…" else ""} · Page ${page + 1}", left, 10, SpotlightTheme.color(0xFFF2F5FC))
        g.themeText(font, catalogClip(font, if (online) HeadDatabase.remoteStatus else "Only client-visible profiles, equipment and inventory; no hidden storage", panelWidth), left, height - 29, SpotlightTheme.color(0xFFF2F5FC))
        g.themeText(font, catalogClip(font, notice, panelWidth), left, height - 15, SpotlightTheme.color(0xFFFFB4A9))
    }
    private inner class HeadCell(x: Int, y: Int, width: Int) : AbstractWidget(x, y, width, 54, catalogText("Head")) {
        private var head: HeadDatabase.Head? = null
        private var stack = ItemStack.EMPTY
        private var previewPending = false
        fun setHead(value: HeadDatabase.Head?) {
            if (head === value && visible == (value != null)) return
            head = value
            stack = previews[value] ?: ItemStack.EMPTY
            previewPending = value != null && stack.isEmpty
            visible = value != null
            message = catalogText(value?.let { "Give ${it.title} · ${it.source} · ${it.tags.joinToString(", ")}" } ?: "Empty")
        }
        fun loadPreview(): Boolean {
            if (!previewPending) return false
            previewPending = false
            val value = head ?: return false
            runCatching { previews[value] ?: value.stack().also { previews[value] = it } }
                .onSuccess { stack = it }
                .onFailure { notice = it.message ?: "Head preview unavailable" }
            return true
        }
        private fun give() {
            runCatching { HeadDatabase.give(requireNotNull(head)) }
                .onSuccess { notice = "Creative copy requested; server may reject" }
                .onFailure { notice = it.message ?: "Cannot give head"; triggerImmediateNarration(true) }
        }
        override fun extractWidgetRenderState(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
            if (isHoveredOrFocused) g.fill(x, y, right, bottom, SpotlightTheme.color(0x40667788))
            val pose = g.pose()
            pose.pushMatrix()
            pose.translate((x + (width - 32) / 2).toFloat(), (y + 2).toFloat())
            pose.scale(2f, 2f)
            g.fakeItem(stack, 0, 0)
            pose.popMatrix()
            g.themeText(font, catalogClip(font, head?.title.orEmpty(), width - 4), x + 2, y + 39, SpotlightTheme.color(0xFFF2F5FC))
            if (isHoveredOrFocused) g.setTooltipForNextFrame(message, mouseX, mouseY)
        }
        override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
            if (active && visible && event.button() == InputConstants.MOUSE_BUTTON_LEFT && isMouseOver(event.x(), event.y())) { give(); return true }
            return false
        }
        override fun keyPressed(event: KeyEvent): Boolean {
            if (active && visible && isFocused && (event.isConfirmation || event.key() == InputConstants.KEY_SPACE)) { give(); return true }
            return false
        }
        override fun updateWidgetNarration(output: NarrationElementOutput) { defaultButtonNarrationText(output) }
    }
}
