package dev.spotlight.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftServer.class)
abstract class VisualPingTestingMixin {
    @Inject(method = "usesAuthentication", at = @At("HEAD"), cancellable = true)
    private void spotlight$disableAuthenticationForVisualPing(CallbackInfoReturnable<Boolean> cir) {
        if (System.getProperty("spotlight.visualPing") != null) cir.setReturnValue(false);
    }
}
