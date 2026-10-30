package dev.spotlight.mixin;

import dev.spotlight.platform.Spotlight;
import dev.spotlight.platform.SpotlightClient;
import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Minecraft.class)
public abstract class CommandKeyMixin {
    
    @Invoker("openChatScreen") protected abstract void spotlight$openChatScreen(String text);

    @Redirect(method = "handleKeybinds", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/Minecraft;openChatScreen(Ljava/lang/String;)V",
        ordinal = 1), require = 1, allow = 1)
    private void spotlight$commandKey(Minecraft client, String text) {
        if ("/".equals(text) && client.screen == null && client.player != null
            && SpotlightClient.shouldReplaceCommandKey()) {
            Spotlight.INSTANCE.setRequestedCommandId(null);
            Spotlight.INSTANCE.setRequestedQuery("/");
            Spotlight.INSTANCE.open(null);
        } else {
            spotlight$openChatScreen(text);
        }
    }
}
