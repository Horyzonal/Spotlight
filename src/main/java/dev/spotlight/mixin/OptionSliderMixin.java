package dev.spotlight.mixin;

import dev.spotlight.platform.ExtendedOptions;
import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.client.OptionInstance$OptionInstanceSliderButton")
abstract class OptionSliderMixin {
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"))
    private static Object spotlight$initialValue(OptionInstance<?> option) {
        return ExtendedOptions.vanillaValue(option);
    }

}
