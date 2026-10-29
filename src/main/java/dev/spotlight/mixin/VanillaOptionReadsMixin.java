package dev.spotlight.mixin;

import dev.spotlight.platform.ExtendedOptions;
import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;


@Mixin(targets = "net.minecraft.client.Options$3")
abstract class VanillaOptionReadsMixin {
    @Redirect(method = "process(Ljava/lang/String;Lnet/minecraft/client/OptionInstance;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"))
    private static Object spotlight$vanillaValue(OptionInstance<?> option) {
        return ExtendedOptions.vanillaValue(option);
    }
}
