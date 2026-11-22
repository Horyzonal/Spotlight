package dev.spotlight.platform

import java.security.KeyPairGenerator
import java.util.Base64

internal object EncryptedChatCheck {
    @JvmStatic
    fun main(args: Array<String>) {
        val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val message = "Encrypted hello § world 123"
        val encrypted = EncryptedChat.encryptPayload(EncryptedChat.packMessage(message), keys.public)
        val encoded = EncryptedChat.encodeBytes(encrypted)
        check(EncryptedChat.decodeBytes(encoded).copyOf(encrypted.size).contentEquals(encrypted))
        check(EncryptedChat.unpackMessage(EncryptedChat.decryptPayload(encrypted, keys.private)) == message)
        check(EncryptedChat.unpackMessage(EncryptedChat.decryptPayload(EncryptedChat.decodeBytes(encoded), keys.private)) == message)
        val testPacket = EncryptedChat.TEST_PACKET_HEADER + Base64.getEncoder().encodeToString(message.toByteArray())
        check(EncryptedChat.receive(testPacket) == message)
        check(EncryptedChat.receive(EncryptedChat.TEST_PACKET_HEADER + "!") == null)
    }
}
