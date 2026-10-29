package dev.spotlight.mixin;

import dev.spotlight.platform.Spotlight;
import dev.spotlight.platform.SpotlightClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.ChatComponent.ChatMethod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Gui.class)
public abstract class CommandKeyMixin {
    
    @Redirect(method = "handleKeybinds", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/Gui;openChatScreen(Lnet/minecraft/client/gui/components/ChatComponent$ChatMethod;)V",
        ordinal = 1), require = 1, allow = 1)
    private void spotlight$commandKey(Gui gui, ChatMethod method) {
        Minecraft client = Minecraft.getInstance();
        if (method == ChatMethod.COMMAND && gui.screen() == null && client.player != null
            && SpotlightClient.shouldReplaceCommandKey()) {
            Spotlight.INSTANCE.setRequestedCommandId(null);
            Spotlight.INSTANCE.setRequestedQuery("/");
            Spotlight.INSTANCE.open(null);
        } else {
            gui.openChatScreen(method);
        }
    }
}
