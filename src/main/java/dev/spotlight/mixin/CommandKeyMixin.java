package dev.spotlight.mixin;

import dev.spotlight.platform.Spotlight;
import dev.spotlight.platform.SpotlightClient;
import net.minecraft.client.Minecraft;

import net.minecraft.client.gui.components.ChatComponent.ChatMethod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Minecraft.class)
public abstract class CommandKeyMixin {
    
    @Redirect(method = "handleKeybinds", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/Minecraft;openChatScreen(Lnet/minecraft/client/gui/components/ChatComponent$ChatMethod;)V",
        ordinal = 1), require = 1, allow = 1)
    private void spotlight$commandKey(Minecraft client, ChatMethod method) {
        if (method == ChatMethod.COMMAND && client.screen == null && client.player != null
            && SpotlightClient.shouldReplaceCommandKey()) {
            Spotlight.INSTANCE.setRequestedCommandId(null);
            Spotlight.INSTANCE.setRequestedQuery("/");
            Spotlight.INSTANCE.open(null);
        } else {
            client.openChatScreen(method);
        }
    }
}
