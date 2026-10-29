package dev.spotlight.mixin;

import dev.spotlight.platform.SoundControls;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MusicManager.class)
public abstract class MusicManagerMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void spotlight$manualMusic(CallbackInfo ci) {
        if (SoundControls.INSTANCE.getCurrentMusic() != null) ci.cancel();
    }
}
