package dev.spotlight.mixin;

import dev.spotlight.platform.ExtendedOptions;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(LightmapRenderStateExtractor.class)
abstract class LightmapMixin {
    @Redirect(method = "extract", slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;gamma()Lnet/minecraft/client/OptionInstance;")),
        at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F"), require = 1, allow = 1)
    private float spotlight$gamma(float minimum, float brightness) {
        return ExtendedOptions.gammaActive() ? brightness : Math.max(minimum, brightness);
    }
}
