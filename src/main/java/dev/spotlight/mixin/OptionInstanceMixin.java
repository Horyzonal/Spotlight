package dev.spotlight.mixin;

import dev.spotlight.platform.ExtendedOptions;
import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(OptionInstance.class)
abstract class OptionInstanceMixin {
    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private void spotlight$get(CallbackInfoReturnable<Object> callback) {
        Object value = ExtendedOptions.value((OptionInstance<?>)(Object)this);
        if (value != null) callback.setReturnValue(value);
    }

    @Inject(method = "set", at = @At("HEAD"))
    private void spotlight$set(Object value, CallbackInfo callback) {
        ExtendedOptions.clear((OptionInstance<?>)(Object)this);
    }
}
