package tj.earth.worldgen.mixin;

import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tj.earth.worldgen.Radio;

/**
 * Hands a player's answers about the radio's packs to the radio, and keeps them from the game's own
 * handling: that would remove a player who declines any pack while the server's main pack is required.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
abstract class RadioPackAnswerMixin {
    @Inject(method = "handleResourcePackResponse", cancellable = true,
        at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V"))
    private void earth$radioAnswer(final ServerboundResourcePackPacket packet, final CallbackInfo callback) {
        if (Radio.enabled() && (Object) this instanceof ServerGamePacketListenerImpl game && Radio.answered(game.player, packet)) callback.cancel();
    }
}
