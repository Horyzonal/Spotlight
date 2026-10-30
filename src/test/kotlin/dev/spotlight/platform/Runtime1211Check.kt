package dev.spotlight.platform

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import net.minecraft.world.level.LevelHeightAccessor

/** Native class-file audit; does not initialize Minecraft or apply Mixins. */
object Runtime1211Check {
    private data class Call(val owner: String, val name: String, val descriptor: String)
    private class NativeClass(name: String) {
        val methods = linkedMapOf<String, MutableList<Call>>()
        val fields = linkedMapOf<String, String>()
        init {
            val resource = name.replace('.', '/') + ".class"
            val bytes = requireNotNull(javaClass.classLoader.getResourceAsStream(resource)) { resource }.use { it.readBytes() }
            ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
                override fun visitField(access: Int, name: String, descriptor: String, signature: String?, value: Any?): FieldVisitor? {
                    fields[name] = descriptor
                    return null
                }
                override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor {
                    val calls = mutableListOf<Call>()
                    methods[name + descriptor] = calls
                    return object : MethodVisitor(Opcodes.ASM9) {
                        override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
                            calls += Call(owner, name, descriptor)
                        }
                    }
                }
            }, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        }
        fun method(signature: String) = requireNotNull(methods[signature]) { "Missing native method: $signature" }
    }

    @JvmStatic fun main(args: Array<String>) {
        val minecraft = NativeClass("net.minecraft.client.Minecraft")
        val chat = Call("net/minecraft/client/Minecraft", "openChatScreen", "(Ljava/lang/String;)V")
        minecraft.method("openChatScreen(Ljava/lang/String;)V")
        check(minecraft.method("handleKeybinds()V").count { it == chat } == 2)
        listOf("startAttack()Z", "startUseItem()V", "continueAttack(Z)V").forEach { minecraft.method(it) }
        NativeClass("net.minecraft.client.MouseHandler").method("onPress(JIII)V")
        val option = NativeClass("net.minecraft.client.OptionInstance")
        option.method("get()Ljava/lang/Object;")
        option.method("set(Ljava/lang/Object;)V")
        val get = Call("net/minecraft/client/OptionInstance", "get", "()Ljava/lang/Object;")
        check(NativeClass("net.minecraft.client.Options\$3")
            .method("process(Ljava/lang/String;Lnet/minecraft/client/OptionInstance;)V").count { it == get } == 1)
        val slider = NativeClass("net.minecraft.client.OptionInstance\$OptionInstanceSliderButton")
        check(slider.method("<init>(Lnet/minecraft/client/Options;IIIILnet/minecraft/client/OptionInstance;Lnet/minecraft/client/OptionInstance\$SliderableValueSet;Lnet/minecraft/client/OptionInstance\$TooltipSupplier;Ljava/util/function/Consumer;Z)V").count { it == get } == 1)
        val light = NativeClass("net.minecraft.client.renderer.LightTexture").method("updateLightTexture(F)V")
        val gamma = Call("net/minecraft/client/Options", "gamma", "()Lnet/minecraft/client/OptionInstance;")
        check(light.count { it == gamma } == 1)
        check(light.drop(light.indexOf(gamma)).count { it == Call("java/lang/Math", "max", "(FF)F") } == 1)
        check(NativeClass("net.minecraft.client.sounds.SoundManager").fields["soundEngine"] == "Lnet/minecraft/client/sounds/SoundEngine;")
        check(NativeClass("net.minecraft.client.sounds.SoundEngine").fields["instanceToChannel"] == "Ljava/util/Map;")
        NativeClass("net.minecraft.client.sounds.MusicManager").method("tick()V")
        val spawn = NativeClass("net.minecraft.client.multiplayer.ClientPacketListener")
            .method("handleSetSpawn(Lnet/minecraft/network/protocol/game/ClientboundSetDefaultSpawnPositionPacket;)V")
        check(spawn.count { it == Call("net/minecraft/client/multiplayer/ClientLevel", "setDefaultSpawnPos", "(Lnet/minecraft/core/BlockPos;F)V") } == 1)
        val sound = NativeClass("net.minecraft.client.sounds.SoundManager")
        sound.method("stop(Lnet/minecraft/client/resources/sounds/SoundInstance;)V")
        sound.method("stop()V")
        NativeClass("net.minecraft.world.level.storage.LevelData").method("getSpawnPos()Lnet/minecraft/core/BlockPos;")
        val channel = NativeClass("com.mojang.blaze3d.audio.Channel")
        channel.method("pause()V")
        channel.method("unpause()V")
        val recording = NativeClass("net.minecraft.client.resources.sounds.Sound")
        recording.method("getLocation()Lnet/minecraft/resources/ResourceLocation;")
        recording.method("getPath()Lnet/minecraft/resources/ResourceLocation;")
        recording.method("shouldStream()Z")
        NativeClass("net.minecraft.client.renderer.LevelRenderer")
            .method("renderLineBox(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/phys/AABB;FFFF)V")
        val height = LevelHeightAccessor.create(-64, 384)
        check(height.minBuildHeight == -64 && height.maxBuildHeight == 320)
        check(!height.isOutsideBuildHeight(-64) && !height.isOutsideBuildHeight(319))
        check(height.isOutsideBuildHeight(-65) && height.isOutsideBuildHeight(320))
        println("Runtime1211Check passed: all 11 mixin native targets, invocation counts, sound stop, outline renderer, exclusive build ceiling")
    }
}
