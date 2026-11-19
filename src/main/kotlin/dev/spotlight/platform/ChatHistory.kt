package dev.spotlight.platform

import com.mojang.authlib.GameProfile
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.common.NeoForge
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.network.chat.ChatType
import net.minecraft.network.chat.Component
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.UUID

internal data class ChatMessage(
    val profile: GameProfile?,
    val sender: Component?,
    val text: Component,
    val encrypted: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
)

internal class ChatLog {
    private val entries = mutableListOf<ChatMessage>()
    fun add(message: ChatMessage) {
        entries += message
        if (entries.size > 200) entries.subList(0, entries.size - 200).clear()
    }
    fun messages(): List<ChatMessage> = entries.toList()
    fun clear() = entries.clear()
}

internal data class DirectChat(
    val id: UUID,
    var name: String,
    val log: ChatLog = ChatLog(),
    var connection: ClientPacketListener? = null,
    var server: String = "",
    var lastActive: Long = 0,
)

internal object ChatHistory {
    private val public = ChatLog()
    private val direct = linkedMapOf<UUID, DirectChat>()
    private val encrypted = mutableSetOf<UUID>()
    private var lastOpen: UUID? = null
    var revision = 0
        private set

    fun initialize() {
        NeoForge.EVENT_BUS.addListener<ClientChatReceivedEvent.Player> { event ->
            val message = event.message
            val profile = Minecraft.getInstance().connection?.getPlayerInfo(event.sender)?.profile
            val type = requireNotNull(event.boundChatType)
            val entry = ChatMessage(profile, type.name(), message)
            when {
                type.chatType().`is`(ChatType.MSG_COMMAND_INCOMING) -> {
                    val sender = profile ?: return@addListener
                    if (EncryptedChat.isPacket(message.string)) {
                        val decrypted = EncryptedChat.receive(message.string)
                        addDirect(sender.id, sender.name, if (decrypted == null) ChatMessage(null, null,
                            Component.literal("Unable to decrypt or unsupported C2C packet"))
                        else ChatMessage(sender, type.name(), Component.literal(decrypted), encrypted = true))
                    } else addDirect(sender.id, sender.name, entry)
                }
                type.chatType().`is`(ChatType.MSG_COMMAND_OUTGOING) -> {
                    if (EncryptedChat.isPacket(message.string)) return@addListener
                    val target = type.targetName().map(Component::getString).orElse(null) ?: return@addListener
                    val info = Minecraft.getInstance().connection?.onlinePlayers
                        ?.firstOrNull { it.profile.name.equals(target, ignoreCase = true) } ?: return@addListener
                    addDirect(info.profile.id, info.profile.name, entry)
                }
                else -> add(public, entry)
            }
        }
        NeoForge.EVENT_BUS.addListener<ClientChatReceivedEvent.System> { event ->
            if (!event.isOverlay) add(public, ChatMessage(null, null, event.message))
        }
        NeoForge.EVENT_BUS.addListener<ClientPlayerNetworkEvent.LoggingOut> {
            public.clear()
            revision++
        }
    }

    private fun add(log: ChatLog, message: ChatMessage) {
        log.add(message)
        revision++
    }

    private fun addDirect(id: UUID, name: String, message: ChatMessage) {
        val chat = direct.getOrPut(id) { DirectChat(id, name) }
        chat.name = name
        startSession(chat)
        add(chat.log, message)
    }

    private fun startSession(chat: DirectChat) {
        val client = Minecraft.getInstance()
        val connection = requireNotNull(client.connection) { "Join a world first" }
        if (chat.connection !== connection) {
            if (chat.lastActive > 0) chat.log.add(ChatMessage(null, null, Component.literal(
                "Previous session: ${chat.server} · ${formatChatSessionTime(chat.lastActive)}")))
            chat.connection = connection
            chat.server = client.currentServer?.name?.takeIf { it.isNotBlank() }
                ?: if (client.isLocalServer) "Singleplayer" else "Unknown server"
        }
        chat.lastActive = System.currentTimeMillis()
    }

    fun addOutgoing(player: UUID, text: String, encrypted: Boolean = false) {
        val profile = requireNotNull(Minecraft.getInstance().player) { "Join a world first" }.gameProfile
        val name = direct[player]?.name ?: requireNotNull(Minecraft.getInstance().connection).onlinePlayers
            .firstOrNull { it.profile.id == player }?.profile?.name ?: return
        addDirect(player, name, ChatMessage(profile, Component.literal(profile.name), Component.literal(text), encrypted))
    }

    fun open(player: String): UUID {
        val info = requireNotNull(Minecraft.getInstance().connection) { "Join a world first" }.onlinePlayers
            .firstOrNull { it.profile.name.equals(player, ignoreCase = true) } ?: error("Player is no longer online")
        val chat = direct.getOrPut(info.profile.id) { DirectChat(info.profile.id, info.profile.name) }
        chat.name = info.profile.name
        startSession(chat)
        revision++
        return chat.id
    }

    fun lastChat(): UUID? = lastOpen
    fun rememberChat(id: UUID?) { lastOpen = id }
    fun conversation(id: UUID): DirectChat? = direct[id]
    fun conversations(): List<DirectChat> = direct.values.toList()
    fun messages(player: UUID?): List<ChatMessage> =
        if (player == null) public.messages() else direct[player]?.log?.messages().orEmpty()

    fun isEncrypted(player: UUID): Boolean = player in encrypted
    fun toggleEncrypted(player: UUID): Boolean = if (!encrypted.add(player)) {
        encrypted.remove(player)
        false
    } else true
}

internal fun chatPlayerKey(name: String): String = name.trim().lowercase(Locale.ROOT)
internal fun chatTimestampNeeded(previous: Long?, current: Long): Boolean =
    previous == null || current - previous >= 300_000

internal fun formatChatTime(timestamp: Long, now: Instant = Instant.now()): String {
    val time = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
    val current = now.atZone(ZoneId.systemDefault())
    val pattern = when {
        time.toLocalDate() == current.toLocalDate() -> "HH:mm"
        !time.toLocalDate().isBefore(current.toLocalDate().minusDays(6)) -> "EEE HH:mm"
        time.year == current.year -> "MMM d, HH:mm"
        else -> "MMM d, yyyy, HH:mm"
    }
    return DateTimeFormatter.ofPattern(pattern).format(time)
}

internal fun formatChatSessionTime(timestamp: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
