package dev.spotlight.mixin;

import dev.spotlight.platform.LocationActions;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class WorldSpawnMixin {
    @Inject(method = "handleSetSpawn", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/multiplayer/ClientLevel;setDefaultSpawnPos(Lnet/minecraft/core/BlockPos;F)V",
        shift = At.Shift.AFTER), require = 1, allow = 1)
    private void spotlight$serverSpawn(ClientboundSetDefaultSpawnPositionPacket packet, CallbackInfo ci) {
        LocationActions.serverSpawnReceived();
    }
}
