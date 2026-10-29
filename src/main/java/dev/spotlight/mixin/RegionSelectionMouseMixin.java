package dev.spotlight.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.spotlight.platform.RegionSelection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class RegionSelectionMouseMixin {
    @Unique private boolean spotlight$leftDown;
    @Unique private boolean spotlight$rightDown;

    @Inject(method = "onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V", at = @At("HEAD"), cancellable = true)
    private void spotlight$select(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (window == 0 || window != client.getWindow().handle()) return;
        int button = info.button();
        boolean left = button == InputConstants.MOUSE_BUTTON_LEFT;
        if (!left && button != InputConstants.MOUSE_BUTTON_RIGHT) return;
        boolean held = left ? spotlight$leftDown : spotlight$rightDown;
        if (action == InputConstants.RELEASE) {
            if (left) spotlight$leftDown = false;
            else spotlight$rightDown = false;
            if (held && client.screen == null) ci.cancel();
            return;
        }
        if (action != InputConstants.PRESS || client.screen != null || client.getOverlay() != null ||
                !client.isWindowActive() || !client.mouseHandler.isMouseGrabbed() || !RegionSelection.INSTANCE.active()) return;
        if (left) spotlight$leftDown = true;
        else spotlight$rightDown = true;
        if (!held) RegionSelection.INSTANCE.selectPoint(button);
        ci.cancel();
    }
}
