package dev.spotlight.mixin;

import dev.spotlight.platform.SoundControls;
import dev.spotlight.platform.EndlessMusic;
import net.minecraft.client.resources.sounds.SoundInstance;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MusicManager.class)
public abstract class MusicManagerMixin {
    @Accessor("currentMusic")
    protected abstract SoundInstance spotlight$currentMusic();

    @Accessor("nextSongDelay")
    protected abstract int spotlight$nextSongDelay();

    @Accessor("nextSongDelay")
    protected abstract void spotlight$nextSongDelay(int delay);

    @Inject(method = "tick()V", at = @At("HEAD"), cancellable = true)
    private void spotlight$manualMusic(CallbackInfo ci) {
        if (SoundControls.INSTANCE.getCurrentMusic() != null) {
            ci.cancel();
            return;
        }
        if (EndlessMusic.INSTANCE.getEnabled() && spotlight$currentMusic() == null && spotlight$nextSongDelay() > 0) {
            spotlight$nextSongDelay(0);
        }
    }
}
