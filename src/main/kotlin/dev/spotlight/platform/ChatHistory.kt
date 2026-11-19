package dev.spotlight.platform

import com.mojang.authlib.GameProfile
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.network.chat.ChatType
import net.minecraft.network.chat.Component
import java.util.Locale

internal data class ChatMessage(val profile: GameProfile?, val sender: Component?, val text: Component)

internal class ChatLog {
    private val entries = mutableListOf<ChatMessage>()
    fun add(message: ChatMessage) {
        entries += message
        if (entries.size > 200) entries.subList(0, entries.size - 200).clear()
    }
    fun messages(): List<ChatMessage> = entries.toList()
    fun clear() = entries.clear()
}

internal object ChatHistory {
    private val public = ChatLog()
    private val direct = linkedMapOf<String, Pair<String, ChatLog>>()
    var revision = 0
        private set

    fun initialize() {
        ClientReceiveMessageEvents.CHAT.register { message, _, profile, type, _ ->
            val entry = ChatMessage(profile, type.name(), message)
            when {
                type.chatType().`is`(ChatType.MSG_COMMAND_INCOMING) -> addDirect(profile?.name() ?: return@register, entry)
                type.chatType().`is`(ChatType.MSG_COMMAND_OUTGOING) -> {
                    val target = type.targetName().map(Component::getString).orElse(null) ?: return@register
                    addDirect(target, entry)
                }
                else -> add(public, entry)
            }
        }
        ClientReceiveMessageEvents.GAME.register { message, overlay ->
            if (!overlay) add(public, ChatMessage(null, null, message))
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> clear() }
        ClientLifecycleEvents.CLIENT_STOPPING.register { clear() }
    }

    private fun add(log: ChatLog, message: ChatMessage) {
        log.add(message)
        revision++
    }

    private fun addDirect(player: String, message: ChatMessage) {
        val key = chatPlayerKey(player)
        if (key.isEmpty()) return
        val chat = direct[key] ?: (player to ChatLog()).also { direct[key] = it }
        add(chat.second, message)
    }

    fun open(player: String): String {
        val key = chatPlayerKey(player)
        val chat = direct[key] ?: (player to ChatLog()).also { direct[key] = it; revision++ }
        return chat.first
    }

    fun conversations(): List<String> = direct.values.map { it.first }
    fun messages(player: String?): List<ChatMessage> =
        if (player == null) public.messages() else direct[chatPlayerKey(player)]?.second?.messages().orEmpty()

    private fun clear() {
        public.clear()
        direct.clear()
        revision++
    }
}

internal fun chatPlayerKey(name: String): String = name.trim().lowercase(Locale.ROOT)
