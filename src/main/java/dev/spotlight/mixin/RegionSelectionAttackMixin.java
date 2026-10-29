package dev.spotlight.mixin;

import dev.spotlight.platform.RegionSelection;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class RegionSelectionAttackMixin {
    @Inject(method = "startAttack()Z", at = @At("HEAD"), cancellable = true)
    private void spotlight$blockAttack(CallbackInfoReturnable<Boolean> ci) {
        if (RegionSelection.INSTANCE.active()) ci.setReturnValue(false);
    }

    @Inject(method = "startUseItem()V", at = @At("HEAD"), cancellable = true)
    private void spotlight$blockUse(CallbackInfo ci) {
        if (RegionSelection.INSTANCE.active()) ci.cancel();
    }

    @Inject(method = "continueAttack(Z)V", at = @At("HEAD"), cancellable = true)
    private void spotlight$blockHeldAttack(boolean attacking, CallbackInfo ci) {
        if (RegionSelection.INSTANCE.active()) ci.cancel();
    }
}
