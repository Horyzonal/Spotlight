package dev.spotlight.ui

import dev.spotlight.platform.ClientItemStorage
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation
import net.minecraft.client.gui.screens.Screen
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.level.GameType
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.world.item.ItemStack

class ClientChestScreen private constructor(private val parent: Screen?, private val local: ClientChestMenu) :
    dev.spotlight.LocalContainerScreen(local, catalogText("Client chest · LOCAL")) {
    constructor(parent: Screen? = null) : this(parent, createMenu())
    private var page = 1
    private var query = ""
    private var offset = 0
    private var notice = "Real inventory unchanged · cursor saved on close"
    private var matches = emptyList<Pair<String, CompoundTag>>()
    private var loadedRevision = -1L
    private lateinit var search: EditBox
    private lateinit var layout: ClientChestLayout
    companion object {
        private fun createMenu(): ClientChestMenu {
            val client = Minecraft.getInstance()
            val actor = object : Player(requireNotNull(client.level) { "Join a world first" }, net.minecraft.core.BlockPos.ZERO, 0f, requireNotNull(client.player).gameProfile) {
                override fun isSpectator() = false
                override fun isCreative() = false
            }
            return ClientChestMenu(actor, ClientItemStorage::decode, ClientItemStorage::encode) { _, _, _ -> error("Screen not initialized") }
        }
    }
    private fun attempt(action: () -> Unit) {
        try { action() } catch (e: Exception) { notice = e.message ?: e.javaClass.simpleName; triggerImmediateNarration(true) }
    }
    override fun init() {
        super.init()
        layout = ClientChestLayout(width, height)
        leftPos = layout.left
        topPos = layout.top
        search = addRenderableWidget(EditBox(font, leftPos, topPos - 18, imageWidth, 16, catalogText("Search all saved pages")))
        search.setHint(catalogText("Search all saved pages"))
        search.setTooltip(Tooltip.create(catalogText("Live search across saved pages · results open source page")))
        search.setMaxLength(200)
        search.value = query
        search.setResponder { value -> attempt { query = value; offset = 0; reload() } }
        fun button(label: String, row: Int, action: () -> Unit) {
            val paired = row <= 1 || row >= 10
            val buttonWidth = if (paired) (layout.sidebarWidth - 2) / 2 else layout.sidebarWidth
            val x = layout.sidebarLeft + if (row == 1 || row == 11) buttonWidth + 2 else 0
            val y = topPos + (if (row <= 1) 0 else if (row >= 10) 9 else row - 1) * 18
            val caption = when (row) { 0 -> "Prev"; 1 -> "Next"; 8 -> "Copy held"; 9 -> "Obtain cursor"; 10 -> "Clear"; else -> label }
            addRenderableWidget(Button.builder(catalogText(caption)) { attempt(action) }
                .bounds(x, y, buttonWidth, 18).tooltip(Tooltip.create(catalogText(label))).build())
        }
        button("Previous", 0) { if (query.isBlank()) { if (page > 1) page-- } else offset = (offset - 54).coerceAtLeast(0); reload() }
        button("Next", 1) { if (query.isBlank()) { if (page < Int.MAX_VALUE) page++ } else if (offset + 54 < matches.size) offset += 54; reload() }
        button("Go to page", 2) { prompt("Go to page", page.toString()) { page = it.toInt().also { n -> require(n > 0) }; query = "" } }
        button("Find page", 3) { prompt("Search page names") { value ->
            val pages = ClientItemStorage.pages(value)
            require(pages.isNotEmpty()) { "No saved page matches" }
            minecraft!!.setScreen(PageMatchesScreen(this, pages) { page = it; query = "" })
        } }
        button("Rename", 4) { requirePage(); prompt("Page name", ClientItemStorage.page(page).getString("Name")) { ClientItemStorage.renamePage(page, it) } }
        button("Export .nbt", 5) { requirePage(); prompt("Export vanilla chest NBT", "page-$page.nbt", "Local exports folder · never overwrites") { ClientItemStorage.exportPage(page, it) } }
        button("Import .nbt", 6) { requirePage(); prompt("Import vanilla chest NBT", "page-$page.nbt", "Local exports folder · empty page required") { ClientItemStorage.importPage(page, it) } }
        button("Revisions", 7) { minecraft!!.setScreen(RevisionScreen(this)) }
        button("Copy held (real → local)", 8) { if (loadedRevision != ClientItemStorage.revision()) reload(); local.copySource(requireNotNull(minecraft!!.player).mainHandItem); notice = "Held item copied; real inventory unchanged" }
        button("Obtain cursor (Creative)", 9) {
            val raw = requireNotNull(ClientItemStorage.cursor()) { "Pick up a local item first" }
            ClientItemStorage.give(listOf(raw))
            notice = "Creative copy requested; local cursor retained"
        }
        button("Clear search", 10) { query = ""; search.value = ""; reload() }
        button("Close", 11) { onClose() }
        attempt { reload() }
    }
    private fun requirePage() = check(query.isBlank()) { "Clear search to edit a page" }
    private fun prompt(title: String, value: String = "", hint: String = "", action: (String) -> Unit) {
        minecraft!!.setScreen(PromptScreen(this, title, value, hint, action))
    }
    private fun reload() {
        local.persist = { items, inventory, cursor ->
            ClientItemStorage.atomicReplacePageItems(page, items, cursor, null, inventory)
            loadedRevision = ClientItemStorage.revision()
        }
        local.readOnly = true
        val items = if (query.isNotBlank()) {
            matches = searchVaultItems(query, ClientItemStorage.pages().flatMap { (number, name) ->
                ClientItemStorage.items(ClientItemStorage.page(number)).map { (slot, raw) -> "$number:$slot $name" to raw }
            }) { runCatching { ClientItemStorage.decode(it).hoverName.string }.getOrDefault("") }
            offset = offset.coerceAtMost(((matches.size - 1).coerceAtLeast(0) / 54) * 54)
            matches.drop(offset).take(54).mapIndexed { index, entry -> index to entry.second }.toMap()
        } else ClientItemStorage.items(ClientItemStorage.page(page))
        local.load(items, ClientItemStorage.localInventory(), ClientItemStorage.cursor())
        local.readOnly = query.isNotBlank()
        isQuickCrafting = false
        quickCraftSlots.clear()
        loadedRevision = ClientItemStorage.revision()
    }
    override fun localSlotClicked(slotId: Int, button: Int, input: ClickType) {
        attempt {
            if (local.readOnly) {
                if (slotId in 0..53 && input == ClickType.PICKUP) {
                    val target = matches.getOrNull(offset + slotId) ?: return@attempt
                    val address = target.first.substringBefore(' ').split(':')
                    page = address[0].toInt()
                    query = ""
                    search.value = ""
                    reload()
                    notice = "Opened source page $page · slot ${address[1].toInt() + 1}; no item moved"
                }
            } else {
                            if (loadedRevision != ClientItemStorage.revision()) { reload(); notice = "Storage changed; click again"; return@attempt }
                            local.clicked(slotId, button, input, local.actor)
                        }
        }
    }
    override fun handleSlotStateChanged(slotId: Int, containerId: Int, newState: Boolean) {}
    override fun mouseScrolled(x: Double, y: Double, horizontal: Double, vertical: Double) = false
    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val event = KeyEvent(keyCode, scanCode, modifiers)
        if (search.isFocused) {
            if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) { onClose(); return true }
            return search.keyPressed(event.key(), event.scancode(), event.modifiers())
        }
        return super.keyPressed(event.key(), event.scancode(), event.modifiers())
    }
    override fun containerTick() { attempt { if (loadedRevision != ClientItemStorage.revision()) reload() } }
    override fun removed() { local.removed(local.actor) }
    override fun onClose() { minecraft!!.setScreen(parent) }
    override fun renderLabels(g: GuiGraphics, mouseX: Int, mouseY: Int) {
        g.drawString(font, title, titleLabelX, titleLabelY, 0xFF404040.toInt(), false)
        g.drawString(font, catalogText("Local inventory"), inventoryLabelX, inventoryLabelY, 0xFF404040.toInt(), false)
    }
    override fun renderBg(g: GuiGraphics, delta: Float, mouseX: Int, mouseY: Int) {
        val texture = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png")
        g.blit(RenderType::guiTextured, texture, leftPos, topPos, 0f, 0f, imageWidth, 125, 256, 256)
        g.blit(RenderType::guiTextured, texture, leftPos, topPos + 125, 0f, 126f, imageWidth, 96, 256, 256)
        g.fill(layout.sidebarLeft - 2, topPos - 2, layout.sidebarLeft + layout.sidebarWidth + 2, topPos + imageHeight, 0xB0101010.toInt())
        local.unavailable.filter { it in local.slots.indices }.forEach { index ->
            val slot = local.slots[index]
            val x = leftPos + slot.x; val y = topPos + slot.y
            g.fill(x, y, x + 16, y + 16, 0x99893C46.toInt())
            g.drawString(font, "?", x + 5, y + 4, 0xFFFFFFFF.toInt(), false)
        }
    }
    override fun render(g: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(g, mouseX, mouseY, delta)
        val label = if (query.isBlank()) "Page $page · ${runCatching { ClientItemStorage.page(page).getString("Name") }.getOrDefault("")}" else "${matches.size} matches · ${offset / 54 + 1} · click opens source"
        g.drawString(font, catalogClip(font, label, layout.sidebarWidth), layout.sidebarLeft, topPos - 13, 0xFFFFFFFF.toInt(), false)
        g.drawString(font, catalogClip(font, notice, layout.sidebarWidth), layout.sidebarLeft, topPos + 184, 0xFFFFCC99.toInt(), false)
        g.drawString(font, "LOCAL only", layout.sidebarLeft, topPos + 198, 0xFFCCCCCC.toInt(), false)
        g.drawString(font, catalogClip(font, "Cursor saved on close", layout.sidebarWidth), layout.sidebarLeft, topPos + 210, 0xFFCCCCCC.toInt(), false)
        if (mouseX >= layout.sidebarLeft && mouseX < layout.sidebarLeft + layout.sidebarWidth) {
            if (mouseY in topPos - 18 until topPos) g.setTooltipForNextFrame(catalogText(label), mouseX, mouseY)
            if (mouseY in topPos + 182 until topPos + imageHeight) g.setTooltipForNextFrame(catalogText("$notice · Real inventory unchanged · cursor saved on close"), mouseX, mouseY)
        }
        hoveredSlot?.takeIf { it.index in local.unavailable }?.let { g.setTooltipForNextFrame(catalogText("Unavailable item · raw NBT preserved · slot locked"), mouseX, mouseY) }
    }
}

internal class ClientChestLayout(width: Int, height: Int) {
    val sidebarWidth = minOf(144, (width - 190).coerceAtLeast(40))
    val left = ((width - 176 - 8 - sidebarWidth) / 2).coerceAtLeast(0)
    val top = maxOf(18, (height - 222) / 2)
    val sidebarLeft = left + 176 + 8
}

abstract class StorageScreen(private val parent: Screen?, title: String) : Screen(catalogText(title)) {
    protected var cursorY = 30
    protected var left = 0
    protected var notice = ""
    private var gridTop: Int? = null
    private var scrollOffset = 0
    private var contentBottom = 0
    protected abstract fun build()
    override fun isPauseScreen() = false
    override fun onClose() { minecraft!!.setScreen(parent) }
    override fun init() {
        left = maxOf(4, (width - 360) / 2)
        val savedScroll = scrollOffset
        scrollOffset = 0
        cursorY = 30
        gridTop = null
        attempt { build() }
        if (children().isEmpty()) row("Close") { onClose() }
        contentBottom = cursorY
        scrollBy(savedScroll)
    }
    protected fun contentContains(x: Double, y: Double) = storageContentContains(width, height, x, y)
    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean =
        contentContains(mouseX, mouseY) && super.mouseClicked(mouseX, mouseY, button)
    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val event = MouseButtonEvent(mouseX, mouseY, button)
        if (!contentContains(event.x(), event.y())) { isDragging = false; return false }
        return super.mouseReleased(event.x(), event.y(), event.button())
    }
    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dx: Double, dy: Double): Boolean =
        contentContains(mouseX, mouseY) && super.mouseDragged(mouseX, mouseY, button, dx, dy)
    override fun mouseMoved(x: Double, y: Double) { if (contentContains(x, y)) super.mouseMoved(x, y) }
    private fun scrollBy(amount: Int) {
        val next = (scrollOffset + amount).coerceIn(0, (contentBottom - height + 24).coerceAtLeast(0))
        val delta = next - scrollOffset
        children().filterIsInstance<AbstractWidget>().forEach { it.y -= delta }
        gridTop = gridTop?.minus(delta)
        scrollOffset = next
    }
    override fun mouseScrolled(x: Double, y: Double, horizontal: Double, vertical: Double): Boolean {
            if (!contentContains(x, y)) return false
            scrollBy((-vertical * 24).toInt()); return true
        }
    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val event = KeyEvent(keyCode, scanCode, modifiers)
        val handled = super.keyPressed(event.key(), event.scancode(), event.modifiers())
        (focused as? AbstractWidget)?.let { if (it.y < 26) scrollBy(it.y - 26) else if (it.bottom > height - 20) scrollBy(it.bottom - height + 20) }
        return handled
    }
    protected fun rebuild() { rebuildWidgets() }
    protected fun attempt(action: () -> Unit) { try { action() } catch (e: Exception) { notice = e.message ?: e.javaClass.simpleName; triggerImmediateNarration(true) } }
    protected fun prompt(title: String, value: String = "", hint: String = "", action: (String) -> Unit) { minecraft!!.setScreen(PromptScreen(this, title, value, hint, action)) }
    protected fun text(value: String) {
        val y = cursorY
        addRenderableOnly(net.minecraft.client.gui.components.Renderable { g, _, _, _ -> g.themeText(font, catalogClip(font, value, 360), left, y - scrollOffset, SpotlightTheme.color(0xFFF2F5FC)) })
        cursorY += 14
    }
    protected fun row(vararg labels: String, action: (Int) -> Unit) {
        val w = 360 / labels.size
        labels.forEachIndexed { index, label -> addRenderableWidget(catalogButton(label, left + index * w, cursorY, w - 2) { attempt { action(index) } }) }
        cursorY += 24
    }
    protected fun grid(stacks: Map<Int, CompoundTag>, choose: ((Int) -> Unit)? = null) {
        gridTop = cursorY
        repeat(54) { slot ->
            val raw = stacks[slot]
            var error: String? = null
            val stack = if (raw == null) ItemStack.EMPTY else try { ClientItemStorage.decode(raw) } catch (e: Exception) { error = e.message; ItemStack.EMPTY }
            addRenderableWidget(CopySlot(left + slot % 9 * 20, cursorY + slot / 9 * 20, stack, error ?: "Saved slot ${slot + 1}", choose != null) { attempt { choose?.invoke(slot) } })
        }
        cursorY += 124
    }
    internal fun removalSlot(event: MouseButtonEvent): Int? = storageRemovalSlot(width, height, left, gridTop, event)
        protected fun gridSlot(x: Double, y: Double): Int? = storageGridSlot(width, height, left, gridTop, x, y)
    override fun renderBackground(g: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        super.renderBackground(g, mouseX, mouseY, delta)
        g.fill(0, 0, width, height, SpotlightTheme.color(0xF0101722))
        g.enableScissor(0, 26, width, maxOf(26, height - 20))
    }

    override fun render(g: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(g, if (contentContains(mouseX.toDouble(), mouseY.toDouble())) mouseX else -1, if (contentContains(mouseX.toDouble(), mouseY.toDouble())) mouseY else -1, delta)
        g.disableScissor()
        g.themeText(font, title, left, 10, SpotlightTheme.color(0xFFF2F5FC))
        g.themeText(font, catalogClip(font, notice, (width - 20).coerceAtLeast(1)), 10, height - 12, SpotlightTheme.color(0xFFFFB4A9))
    }
}

internal fun storageContentContains(width: Int, height: Int, x: Double, y: Double) =
    x >= 0 && x < width && y >= 26 && y < height - 20

internal fun storageGridSlot(width: Int, height: Int, left: Int, top: Int?, x: Double, y: Double): Int? =
    if (storageContentContains(width, height, x, y) && top != null && x >= left && x < left + 180 && y >= top && y < top + 120)
        ((y - top).toInt() / 20) * 9 + (x - left).toInt() / 20 else null

internal fun storageRemovalSlot(width: Int, height: Int, left: Int, top: Int?, event: MouseButtonEvent): Int? =
    if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) storageGridSlot(width, height, left, top, event.x(), event.y()) else null

internal class CopySlot(x: Int, y: Int, private val stack: ItemStack, label: String, private val interactive: Boolean = true, private val choose: () -> Unit) : AbstractWidget(x, y, 20, 20, catalogText(if (stack.isEmpty) label else "$label: ${stack.hoverName.string} ×${stack.count}")) {
    override fun renderWidget(g: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val client = net.minecraft.client.Minecraft.getInstance()
        g.blitSprite(net.minecraft.client.renderer.RenderType::guiTextured, net.minecraft.resources.ResourceLocation.withDefaultNamespace("container/slot"), x, y, 20, 20)
        if (isHoveredOrFocused) g.fill(x + 1, y + 1, right - 1, bottom - 1, 0x40667788)
        if (!stack.isEmpty) { g.renderFakeItem(stack, x + 2, y + 2); g.renderItemDecorations(client.font, stack, x + 2, y + 2) }
        if (isHoveredOrFocused) {
            if (stack.isEmpty) g.setTooltipForNextFrame(message, mouseX, mouseY)
            else g.setTooltipForNextFrame(client.font, stack, mouseX, mouseY)
        }
    }
    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
            val event = MouseButtonEvent(mouseX, mouseY, button)
        if (interactive && active && visible && event.button() == InputConstants.MOUSE_BUTTON_LEFT && isMouseOver(event.x(), event.y())) { choose(); return true }
        return false
    }
    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val event = KeyEvent(keyCode, scanCode, modifiers)
            if (interactive && active && visible && isFocused && (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER || event.key() == InputConstants.KEY_SPACE)) { choose(); return true }
            return false
        }
    override fun updateWidgetNarration(output: NarrationElementOutput) { defaultButtonNarrationText(output) }
}

internal class PageMatchesScreen(parent: Screen, private val matches: List<Pair<Int, String>>, private val choose: (Int) -> Unit) : StorageScreen(parent, "Matching pages") {
    private var offset = 0
    override fun build() {
        matches.drop(offset).take(8).forEach { (page, name) -> row("$page · $name") { choose(page); onClose() } }
        row("Previous", "Next", "Back") { when (it) { 0 -> offset = (offset - 8).coerceAtLeast(0); 1 -> if (offset + 8 < matches.size) offset += 8; 2 -> onClose() }; rebuild() }
    }
}
internal class RevisionScreen(parent: Screen) : StorageScreen(parent, "Vault revisions · restore saved copies only") {
    private var offset = 0
    override fun build() {
        val history = ClientItemStorage.history().reversed()
        history.drop(offset).take(8).forEach { name -> row(name) { minecraft!!.setScreen(StorageConfirmationScreen(this) { attempt { ClientItemStorage.restoreRevision(name); rebuild() } }) } }
        row("Previous", "Next", "Back") { when (it) { 0 -> offset = (offset - 8).coerceAtLeast(0); 1 -> if (offset + 8 < history.size) offset += 8; 2 -> onClose() }; rebuild() }
    }
}
