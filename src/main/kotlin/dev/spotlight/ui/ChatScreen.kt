package dev.spotlight.ui

import com.mojang.authlib.GameProfile
import dev.spotlight.platform.ChatHistory
import dev.spotlight.platform.ChatMessage
import dev.spotlight.platform.EncryptedChat
import dev.spotlight.platform.chatTimestampNeeded
import dev.spotlight.platform.formatChatTime
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.PlayerFaceRenderer
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence

import java.util.UUID

class ChatScreen(private val parent: Screen?) : Screen(catalogText("Chat")) {
    private data class Row(
        val message: ChatMessage,
        val lines: List<FormattedCharSequence>,
        val height: Int,
        val timestamp: String? = null,
    )

    private var selected: UUID? = ChatHistory.lastChat()
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
    private var supportTicks = 0
    private val tabButtons = mutableListOf<Button>()


    private lateinit var input: EditBox
    private lateinit var encryptButton: Button

    override fun isPauseScreen() = false
    override fun onClose() { minecraft.setScreen(parent) }

    override fun init() {
        selected?.let { id -> ChatHistory.conversation(id)?.let { selected = ChatHistory.open(it.name) } }
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
        encryptButton = addRenderableWidget(catalogButton("Encrypted", left + panelWidth - 142, top + 14, 100) {
            val player = selected ?: return@catalogButton
            ChatHistory.toggleEncrypted(player)
            updateEncryptionButton()
        })
        updateEncryptionButton()
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

    private fun select(player: UUID?) {
        selected = player
        ChatHistory.rememberChat(player)
        status = ""
        followLatest = true
        layoutWidth = -1
        rebuildTabs()
        updateInputHint()
        updateEncryptionButton()
        setFocused(input)
    }

    private fun createChat() {
        val players = minecraft.connection?.onlinePlayers.orEmpty().map { it.profile.name }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
        minecraft.setScreen(CreateChatScreen(this, players) { select(ChatHistory.open(it)) })
    }

    private fun rebuildTabs() {
        tabButtons.forEach(::removeWidget)
        tabButtons.clear()
        val conversations = ChatHistory.conversations()
        if (selected != null && conversations.none { it.id == selected }) selected = null
        val visible = ((top + panelHeight - 12 - (top + 88)) / 32).coerceAtLeast(0)
        tabOffset = tabOffset.coerceIn(0, (conversations.size - visible).coerceAtLeast(0))
        conversations.drop(tabOffset).take(visible).forEachIndexed { index, chat ->
            val button = addRenderableWidget(catalogButton(chat.name, left + 10, top + 88 + index * 32,
                sidebarWidth - 20, 28) { select(chat.id) })
            if (selected == chat.id) button.active = false
            tabButtons += button
        }
        layoutWidth = -1
    }

    private fun updateEncryptionButton() {
        if (!::encryptButton.isInitialized) return
        encryptButton.visible = selected != null
        encryptButton.message = catalogText(if (selected?.let(ChatHistory::isEncrypted) == true)
            "Encrypted: on" else "Encrypted: off")
        val unavailable = selected?.let(EncryptedChat::unavailable)
        encryptButton.active = selected != null && unavailable == null
        encryptButton.setTooltip(Tooltip.create(catalogText(unavailable
            ?: if (selected?.let { it == minecraft.player?.uuid } == true)
                "Self-chat can use a local encryption key" else "Recipient has a Minecraft chat public key")))
    }

    private fun updateInputHint() {
        if (::input.isInitialized) input.setHint(catalogText(selected?.let {
            "Message ${ChatHistory.conversation(it)?.name}" } ?: "Message public chat"))
    }

    private fun send() {
        val text = draft.trim()
        if (text.isEmpty()) return
        try {
            require(text.length <= 256 && text.none { it < ' ' }) { "Message must be 1..256 characters without control characters" }
            val connection = requireNotNull(minecraft.connection) { "Join a world first" }
            val player = selected?.let { ChatHistory.conversation(it) }
            when {
                selected == null -> connection.sendChat(text)
                player == null -> error("Chat unavailable")
                ChatHistory.isEncrypted(player.id) -> EncryptedChat.send(player.name, text)
                else -> connection.sendCommand("msg ${player.name} $text")
            }
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
        if (++supportTicks % 20 == 0) updateEncryptionButton()
    }

    private fun rebuildRows() {
        val wrapWidth = (contentWidth - 76).coerceAtLeast(1)
        var previous: Long? = null
        rows = ChatHistory.messages(selected).map { message ->
            val timestamp = if (chatTimestampNeeded(previous, message.timestamp)) formatChatTime(message.timestamp) else null
            previous = message.timestamp
            val lines = font.split(message.text, wrapWidth)
            val height = if (message.profile == null) lines.size * font.lineHeight + 10
                else maxOf(38, lines.size * font.lineHeight + 31)
            Row(message, lines, height + if (timestamp == null) 0 else 16, timestamp)
        }
        layoutWidth = contentWidth
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, SpotlightTheme.color(0xC0101722))
        graphics.fill(left, top, left + panelWidth, top + panelHeight, SpotlightTheme.color(0xFF202630))
        graphics.fill(left, top, contentLeft, top + panelHeight, SpotlightTheme.color(0xFF161D29))
        graphics.vLine(contentLeft, top, top + panelHeight, SpotlightTheme.color(0xFF415877))
        graphics.hLine(contentLeft, top + 42, left + panelWidth, SpotlightTheme.color(0xFF415877))
        graphics.hLine(contentLeft, top + panelHeight - 42, left + panelWidth, SpotlightTheme.color(0xFF415877))
        graphics.themeText(font, selected?.let { ChatHistory.conversation(it)?.name } ?: "Public",
            contentLeft + 12, top + 20, SpotlightTheme.color(0xFFF2F5FC))
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
        super.render(graphics, mouseX, mouseY, delta)
    }

    private fun drawMessage(graphics: GuiGraphics, row: Row, y: Int) {
        row.timestamp?.let {
            graphics.themeCenteredText(font, Component.literal(it), contentLeft + contentWidth / 2, y,
                SpotlightTheme.color(0xFF9DADC3))
        }
        val messageY = y + if (row.timestamp == null) 0 else 16
        val message = row.message
        val profile = message.profile
        if (profile == null) {
            row.lines.forEachIndexed { index, line ->
                graphics.themeText(font, line, contentLeft + (contentWidth - font.width(line)) / 2,
                    messageY + index * font.lineHeight, SpotlightTheme.color(0xFF9DADC3))
            }
            return
        }
        val own = profile.id() == minecraft.player?.uuid
        val textWidth = row.lines.maxOfOrNull(font::width) ?: 0
        val bubbleWidth = (textWidth + 16).coerceIn(24, (contentWidth - 64).coerceAtLeast(24))
        val bubbleX = if (own) left + panelWidth - 46 - bubbleWidth else contentLeft + 46
        val headX = if (own) left + panelWidth - 40 else contentLeft + 12
        val online = minecraft.connection?.getPlayerInfo(profile.id())
        val skin = online?.skin ?: minecraft.skinManager.createLookup(profile, false).get()
        PlayerFaceRenderer.draw(graphics, skin, headX, messageY, 28)
        val sender = message.sender?.string ?: profile.name()
        graphics.themeText(font, catalogClip(font, sender, contentWidth - 64), bubbleX + 2, messageY,
            SpotlightTheme.color(if (own) 0xFF9DCCFF else 0xFF9DADC3))
        val bubbleY = messageY + 13
        val bubbleBottom = bubbleY + row.lines.size * font.lineHeight + 12
        if (message.encrypted) graphics.themeText(font, "🔒", bubbleX + bubbleWidth - 12, messageY,
            SpotlightTheme.color(0xFF81B8FF))
        graphics.fill(bubbleX, bubbleY, bubbleX + bubbleWidth, bubbleBottom,
            SpotlightTheme.color(if (own) 0xFF28588C else 0xFF29333F))
        graphics.renderOutline(bubbleX, bubbleY, bubbleWidth, bubbleBottom - bubbleY,
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
    override fun onClose() { minecraft.setScreen(parent) }

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
        minecraft.setScreen(parent)
        select(player)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.isEscape) { onClose(); return true }
        if (event.isConfirmation && search.isFocused) {
            matches().firstOrNull()?.let { choose(it); return true }
        }
        return super.keyPressed(event)
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, SpotlightTheme.color(0xF0101722))
        graphics.themeText(font, catalogText("Create chat · choose an online player"), left, 20, SpotlightTheme.color(0xFFF2F5FC))
        if (matches().isEmpty()) graphics.themeCenteredText(font, catalogText("No matching online players"),
            left + panelWidth / 2, 86, SpotlightTheme.color(0xFF9DADC3))
        graphics.themeText(font, catalogText("${matches().size} players · page ${page + 1}"), left, height - 94,
            SpotlightTheme.color(0xFF9DADC3))
        super.render(graphics, mouseX, mouseY, delta)
        catalogHints(graphics, font, width, height, "Enter First match · Tab Next · Esc Back")
    }
}
