package dev.spotlight.ui

import com.mojang.authlib.GameProfile
import dev.spotlight.platform.ChatHistory
import dev.spotlight.platform.ChatMessage
import dev.spotlight.platform.chatPlayerKey
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.PlayerFaceExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import net.minecraft.world.item.component.ResolvableProfile
import java.util.UUID

class ChatScreen(private val parent: Screen?) : Screen(catalogText("Chat")) {
    private data class Row(val message: ChatMessage, val lines: List<FormattedCharSequence>, val height: Int)

    private var selected: String? = null
    private var draft = ""
    private var status = ""
    private var left = 0
    private var top = 0
    private var panelWidth = 0
    private var panelHeight = 0
    private var sidebarWidth = 0
    private var contentLeft = 0
    private var contentWidth = 0
    private var messagesTop = 0
    private var messagesBottom = 0
    private var tabOffset = 0
    private var scroll = 0
    private var followLatest = true
    private var layoutWidth = -1
    private var rows = emptyList<Row>()
    private var revision = -1
    private val tabButtons = mutableListOf<Button>()
    private val avatars = mutableMapOf<UUID, ResolvableProfile>()
    private lateinit var input: EditBox

    override fun isPauseScreen() = false
    override fun onClose() { minecraft.gui.setScreen(parent) }

    override fun init() {
        panelWidth = minOf(640, (width - 16).coerceAtLeast(1))
        panelHeight = minOf(400, (height - 16).coerceAtLeast(1))
        left = (width - panelWidth) / 2
        top = (height - panelHeight) / 2
        sidebarWidth = minOf(144, panelWidth / 3)
        contentLeft = left + sidebarWidth
        contentWidth = (panelWidth - sidebarWidth).coerceAtLeast(1)
        messagesTop = top + 48
        messagesBottom = top + panelHeight - 48

        addRenderableWidget(catalogButton("Public", left + 10, top + 12, sidebarWidth - 20, 32) { select(null) })
        addRenderableWidget(catalogButton("+ Create chat", left + 10, top + 52, sidebarWidth - 20) { createChat() })
        addRenderableWidget(catalogButton("×", left + panelWidth - 34, top + 12, 24) { onClose() })
        input = addRenderableWidget(SpotlightEditBox(font, contentLeft + 12, top + panelHeight - 34,
            contentWidth - 74, 22, catalogText("Message")))
        input.setMaxLength(256)
        input.value = draft
        input.setResponder { draft = it }
        addRenderableWidget(catalogButton("Send", left + panelWidth - 54, top + panelHeight - 34, 44, 22) { send() })
        updateInputHint()
        revision = ChatHistory.revision
        rebuildTabs()
        setInitialFocus(input)
    }

    private fun select(player: String?) {
        selected = player
        status = ""
        followLatest = true
        layoutWidth = -1
        rebuildTabs()
        updateInputHint()
        setFocused(input)
    }

    private fun createChat() {
        val players = minecraft.connection?.onlinePlayers.orEmpty().map { it.profile.name }
            .filterNot { it.equals(minecraft.player?.name?.string, ignoreCase = true) }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
        minecraft.gui.setScreen(CreateChatScreen(this, players) { select(ChatHistory.open(it)) })
    }

    private fun rebuildTabs() {
        tabButtons.forEach(::removeWidget)
        tabButtons.clear()
        val conversations = ChatHistory.conversations()
        if (selected != null && conversations.none { chatPlayerKey(it) == chatPlayerKey(selected!!) }) selected = null
        val visible = ((top + panelHeight - 12 - (top + 88)) / 32).coerceAtLeast(0)
        tabOffset = tabOffset.coerceIn(0, (conversations.size - visible).coerceAtLeast(0))
        conversations.drop(tabOffset).take(visible).forEachIndexed { index, player ->
            val button = addRenderableWidget(catalogButton(player, left + 10, top + 88 + index * 32,
                sidebarWidth - 20, 28) { select(player) })
            if (selected?.equals(player, ignoreCase = true) == true) button.active = false
            tabButtons += button
        }
        layoutWidth = -1
    }

    private fun updateInputHint() {
        if (::input.isInitialized) input.setHint(catalogText(selected?.let { "Message $it" } ?: "Message public chat"))
    }

    private fun send() {
        val text = draft.trim()
        if (text.isEmpty()) return
        try {
            require(text.length <= 256 && text.none { it < ' ' }) { "Message must be 1..256 characters without control characters" }
            val connection = requireNotNull(minecraft.connection) { "Join a world first" }
            val player = selected
            if (player == null) connection.sendChat(text) else connection.sendCommand("msg $player $text")
            input.value = ""
            status = ""
            followLatest = true
        } catch (failure: Exception) {
            status = failure.message ?: failure.javaClass.simpleName
        }
    }

    override fun tick() {
        super.tick()
        if (revision != ChatHistory.revision) {
            revision = ChatHistory.revision
            rebuildTabs()
        }
    }

    private fun rebuildRows() {
        val wrapWidth = (contentWidth - 76).coerceAtLeast(1)
        rows = ChatHistory.messages(selected).map { message ->
            val lines = font.split(message.text, wrapWidth)
            Row(message, lines, if (message.profile == null) lines.size * font.lineHeight + 10
                else maxOf(38, lines.size * font.lineHeight + 31))
        }
        layoutWidth = contentWidth
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, SpotlightTheme.color(0xC0101722))
        graphics.fill(left, top, left + panelWidth, top + panelHeight, SpotlightTheme.color(0xFF202630))
        graphics.fill(left, top, contentLeft, top + panelHeight, SpotlightTheme.color(0xFF161D29))
        graphics.verticalLine(contentLeft, top, top + panelHeight, SpotlightTheme.color(0xFF415877))
        graphics.horizontalLine(contentLeft, top + 42, left + panelWidth, SpotlightTheme.color(0xFF415877))
        graphics.horizontalLine(contentLeft, top + panelHeight - 42, left + panelWidth, SpotlightTheme.color(0xFF415877))
        graphics.themeText(font, selected ?: "Public", contentLeft + 12, top + 20, SpotlightTheme.color(0xFFF2F5FC))
        if (layoutWidth != contentWidth) rebuildRows()

        val viewportHeight = (messagesBottom - messagesTop).coerceAtLeast(0)
        val contentHeight = rows.sumOf { it.height }
        val maxScroll = (contentHeight - viewportHeight).coerceAtLeast(0)
        scroll = if (followLatest) maxScroll else scroll.coerceIn(0, maxScroll)
        graphics.enableScissor(contentLeft, messagesTop, left + panelWidth, messagesBottom)
        var y = messagesTop - scroll
        rows.forEach { row ->
            if (y + row.height >= messagesTop && y < messagesBottom) drawMessage(graphics, row, y)
            y += row.height
        }
        graphics.disableScissor()
        if (maxScroll > 0 && viewportHeight > 0) {
            val barHeight = maxOf(12, viewportHeight * viewportHeight / contentHeight)
            val barY = messagesTop + (viewportHeight - barHeight) * scroll / maxScroll
            graphics.fill(left + panelWidth - 4, barY, left + panelWidth - 2, barY + barHeight, SpotlightTheme.color(0xFF81B8FF))
        }
        if (status.isNotEmpty()) graphics.themeText(font, catalogClip(font, status, contentWidth - 24),
            contentLeft + 12, top + panelHeight - 58, SpotlightTheme.color(0xFFFFB4A9))
        super.extractRenderState(graphics, mouseX, mouseY, delta)
    }

    private fun drawMessage(graphics: GuiGraphicsExtractor, row: Row, y: Int) {
        val message = row.message
        val profile = message.profile
        if (profile == null) {
            row.lines.forEachIndexed { index, line ->
                graphics.themeText(font, line, contentLeft + (contentWidth - font.width(line)) / 2,
                    y + index * font.lineHeight, SpotlightTheme.color(0xFF9DADC3))
            }
            return
        }
        val own = profile.id() == minecraft.player?.uuid
        val textWidth = row.lines.maxOfOrNull(font::width) ?: 0
        val bubbleWidth = (textWidth + 16).coerceIn(24, (contentWidth - 64).coerceAtLeast(24))
        val bubbleX = if (own) left + panelWidth - 46 - bubbleWidth else contentLeft + 46
        val headX = if (own) left + panelWidth - 40 else contentLeft + 12
        val online = minecraft.connection?.getPlayerInfo(profile.id())
        if (online != null) PlayerFaceExtractor.extractRenderState(graphics, online.skin, headX, y, 28)
        else PlayerFaceExtractor.extractRenderState(graphics,
            avatars.getOrPut(profile.id()) { ResolvableProfile.createResolved(profile) }, headX, y, 28)
        val sender = message.sender?.string ?: profile.name()
        graphics.themeText(font, catalogClip(font, sender, contentWidth - 64), bubbleX + 2, y,
            SpotlightTheme.color(if (own) 0xFF9DCCFF else 0xFF9DADC3))
        val bubbleY = y + 13
        val bubbleBottom = bubbleY + row.lines.size * font.lineHeight + 12
        graphics.fill(bubbleX, bubbleY, bubbleX + bubbleWidth, bubbleBottom,
            SpotlightTheme.color(if (own) 0xFF28588C else 0xFF29333F))
        graphics.outline(bubbleX, bubbleY, bubbleWidth, bubbleBottom - bubbleY,
            SpotlightTheme.color(if (own) 0xFF4C83BD else 0xFF41505F))
        row.lines.forEachIndexed { index, line ->
            graphics.themeText(font, line, bubbleX + 8, bubbleY + 6 + index * font.lineHeight, SpotlightTheme.color(0xFFF2F5FC))
        }
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        if (mouseX < contentLeft && mouseY >= top + 88) {
            tabOffset = (tabOffset - vertical.toInt()).coerceIn(0,
                (ChatHistory.conversations().size - (panelHeight - 100) / 32).coerceAtLeast(0))
            rebuildTabs()
            return true
        }
        if (mouseX >= contentLeft && mouseY in messagesTop.toDouble()..messagesBottom.toDouble()) {
            val maxScroll = (rows.sumOf { it.height } - (messagesBottom - messagesTop)).coerceAtLeast(0)
            scroll = (scroll - (vertical * 24).toInt()).coerceIn(0, maxScroll)
            followLatest = scroll == maxScroll
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.isConfirmation && input.isFocused) { send(); return true }
        if (event.isEscape) { onClose(); return true }
        return super.keyPressed(event)
    }
}

private class CreateChatScreen(
    private val parent: Screen,
    private val players: List<String>,
    private val select: (String) -> Unit,
) : Screen(catalogText("Create chat")) {
    private var draft = ""
    private var page = 0
    private var left = 0
    private var panelWidth = 0
    private var count = 1
    private val rows = mutableListOf<Button>()
    private lateinit var search: EditBox

    override fun isPauseScreen() = false
    override fun onClose() { minecraft.gui.setScreen(parent) }

    override fun init() {
        rows.clear()
        panelWidth = minOf(360, (width - 24).coerceAtLeast(1))
        left = (width - panelWidth) / 2
        count = ((height - 172) / 24).coerceIn(1, 10)
        search = addRenderableWidget(SpotlightEditBox(font, left, 44, panelWidth, 20, catalogText("Search players")))
        search.setMaxLength(16)
        search.setHint(catalogText("Search online players"))
        search.value = draft
        search.setResponder { draft = it; page = 0; rebuildRows() }
        addRenderableWidget(catalogButton("Previous", left, height - 72, panelWidth / 2 - 2) {
            page = (page - 1).coerceAtLeast(0); rebuildRows()
        })
        addRenderableWidget(catalogButton("Next", left + panelWidth / 2 + 2, height - 72, panelWidth / 2 - 2) {
            page++; rebuildRows()
        })
        addRenderableWidget(catalogButton("Back", left, height - 44, panelWidth) { onClose() })
        rebuildRows()
        setInitialFocus(search)
    }

    private fun matches() = players.filter { it.contains(draft.trim(), ignoreCase = true) }

    private fun rebuildRows() {
        rows.forEach(::removeWidget)
        rows.clear()
        val matches = matches()
        page = page.coerceIn(0, ((matches.size - 1) / count).coerceAtLeast(0))
        matches.drop(page * count).take(count).forEachIndexed { index, player ->
            rows += addRenderableWidget(catalogButton(player, left, 76 + index * 24, panelWidth) { choose(player) })
        }
    }

    private fun choose(player: String) {
        minecraft.gui.setScreen(parent)
        select(player)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.isEscape) { onClose(); return true }
        if (event.isConfirmation && search.isFocused) {
            matches().firstOrNull()?.let { choose(it); return true }
        }
        return super.keyPressed(event)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, SpotlightTheme.color(0xF0101722))
        graphics.themeText(font, catalogText("Create chat · choose an online player"), left, 20, SpotlightTheme.color(0xFFF2F5FC))
        if (matches().isEmpty()) graphics.themeCenteredText(font, catalogText("No matching online players"),
            left + panelWidth / 2, 86, SpotlightTheme.color(0xFF9DADC3))
        graphics.themeText(font, catalogText("${matches().size} players · page ${page + 1}"), left, height - 94,
            SpotlightTheme.color(0xFF9DADC3))
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        catalogHints(graphics, font, width, height, "Enter First match · Tab Next · Esc Back")
    }
}
