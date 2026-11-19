// ClientCommands C2C protocol implementation, adapted for Spotlight.
// Original copyright (c) 2021 Earthcomputer; LGPL-3.0-or-later.
// Source: https://github.com/Earthcomputer/clientcommands/tree/fabric/src/main/java/net/earthcomputer/clientcommands/c2c
package dev.spotlight.platform

import net.minecraft.SharedConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.AccountProfileKeyPairManager
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey

import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher

internal object EncryptedChat {
    private const val PACKET_HEADER = "CCΕNC:"
    private const val MAX_DECOMPRESSED = 65536
    // Offline/self-chat fallback; never substitutes for another player's profile key.
    private val selfKeys by lazy { KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair() }

    fun isPacket(text: String): Boolean = PACKET_HEADER in text

    fun unavailable(player: UUID): String? {
        val client = Minecraft.getInstance()
        if (client.player?.uuid == player) return null
        val info = client.connection?.onlinePlayers?.firstOrNull { it.profile.id == player }
            ?: return "Recipient is offline"
        return if (info.chatSession?.profilePublicKey()?.data()?.key() == null)
            "Recipient has no Minecraft chat public key" else null
    }

    fun send(player: String, text: String) {
        val client = Minecraft.getInstance()
        val connection = requireNotNull(client.connection) { "Join a world first" }
        val recipient = connection.onlinePlayers.firstOrNull { it.profile.name.equals(player, ignoreCase = true) }
            ?: error("Player is no longer online")
        val self = recipient.profile.id() == client.player?.uuid
        val publicKey = recipient.chatSession?.profilePublicKey()?.data()?.key()
            ?: (if (self) ownKeys()?.publicKey()?.data()?.key() ?: selfKeys.public else null)
            ?: error("Recipient has no Minecraft chat public key")
        val encoded = encodeBytes(encryptPayload(packMessage(text), publicKey))
        val command = "msg $player $PACKET_HEADER$encoded"
        require(command.length < SharedConstants.MAX_CHAT_LENGTH) {
            "Encrypted message is too long for Minecraft chat (${command.length}/${SharedConstants.MAX_CHAT_LENGTH - 1} characters)"
        }
        connection.sendCommand(command)
        ChatHistory.addOutgoing(recipient.profile.id(), text, encrypted = true)
    }

    fun receive(text: String): String? {
        val privateKey = ownKeys()?.privateKey() ?: selfKeys.private
        val packet = text.substringAfter(PACKET_HEADER)
        return runCatching { unpackMessage(decryptPayload(decodeBytes(packet), privateKey)) }.getOrNull()
    }

    private fun ownKeys() = (Minecraft.getInstance().profileKeyPairManager as? AccountProfileKeyPairManager)
        ?.prepareKeyPair()?.join()?.orElse(null)

    internal fun packMessage(message: String): ByteArray {
        val text = message.toByteArray(Charsets.UTF_8)
        require(text.size <= 32767) { "Message exceeds C2C protocol limit" }
        val out = ByteArrayOutputStream()
        out.write(0)
        out.writeVarInt(text.size)
        out.write(text)
        return out.toByteArray()
    }

    internal fun unpackMessage(bytes: ByteArray): String {
        var offset = 0
        fun readVarInt(): Int {
            var value = 0
            for (shift in 0..28 step 7) {
                require(offset < bytes.size) { "Truncated VarInt" }
                val next = bytes[offset++].toInt()
                value = value or ((next and 127) shl shift)
                if (next and 128 == 0) return value
            }
            error("VarInt too long")
        }
        require(readVarInt() == 0) { "Not a ClientCommands message packet" }
        val length = readVarInt()
        require(length in 0..32767 && length == bytes.size - offset) { "Invalid message length" }
        return String(bytes, offset, length, Charsets.UTF_8)
    }

    internal fun encryptPayload(payload: ByteArray, key: PublicKey): ByteArray {
        val compressed = gzip(payload)
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val out = ByteArrayOutputStream()
        compressed.asList().chunked(245).forEach { chunk ->
            out.write(cipher.doFinal(chunk.toByteArray()))
        }
        return out.toByteArray()
    }

    internal fun decryptPayload(payload: ByteArray, key: PrivateKey): ByteArray {
        val blocks = payload.copyOf(payload.size / 256 * 256)
        require(blocks.isNotEmpty()) { "Invalid encrypted payload size" }
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, key)
        val decrypted = ByteArrayOutputStream()
        blocks.asList().chunked(256).forEach { chunk ->
            decrypted.write(cipher.doFinal(chunk.toByteArray()))
        }
        return ungzip(decrypted.toByteArray())
    }

    internal fun encodeBytes(bytes: ByteArray): String {
        val result = StringBuilder()
        var data = 0
        var bits = 0
        bytes.forEach { byte ->
            data = data or (byte.toInt().and(255) shl bits)
            bits += 8
            if (bits > 19) {
                result.appendCodePoint(mapCodepoint((data and 0xFFFFF) + 34))
                data = data ushr 20
                bits %= 20
            }
        }
        if (bits != 0) result.appendCodePoint(mapCodepoint((data and 0xFFFFF) + 34))
        return result.toString()
    }

    internal fun decodeBytes(text: String): ByteArray {
        val result = ByteArray((text.codePointCount(0, text.length) * 20 + 7) / 8)
        var bit = 0
        var index = 0
        while (index < text.length) {
            var point = text.codePointAt(index)
            index += Character.charCount(point)
            require(point < 0x10FFFF - 0x800) { "Invalid C2C code point" }
            point = unmapCodepoint(point) - 34
            val byte = bit / 8
            result[byte] = (result[byte].toInt() or (point shl (bit % 8))).toByte()
            result[byte + 1] = (result[byte + 1].toInt() or (point shr (8 - bit % 8))).toByte()
            result[byte + 2] = (result[byte + 2].toInt() or (point shr (16 - bit % 8))).toByte()
            bit += 20
        }
        return result
    }

    private fun mapCodepoint(point: Int): Int = when {
        point < 0xD800 -> when (point) { 167 -> 32; 127 -> 33; else -> point }
        else -> point + 0x800
    }

    private fun unmapCodepoint(point: Int): Int = when {
        point < 0xD800 -> when (point) { 32 -> 167; 33 -> 127; else -> point }
        else -> point - 0x800
    }

    private fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { out ->
        GZIPOutputStream(out).use { it.write(bytes) }
    }.toByteArray()

    private fun ungzip(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPInputStream(ByteArrayInputStream(bytes)).use { input ->
            val buffer = ByteArray(1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                require(out.size() + read <= MAX_DECOMPRESSED) { "Decompressed C2C packet too large" }
                out.write(buffer, 0, read)
            }
        }
        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.writeVarInt(value: Int) {
        var remaining = value
        do {
            var byte = remaining and 127
            remaining = remaining ushr 7
            if (remaining != 0) byte = byte or 128
            write(byte)
        } while (remaining != 0)
    }
}
