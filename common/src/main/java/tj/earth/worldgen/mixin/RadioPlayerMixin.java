package tj.earth.worldgen.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tj.earth.worldgen.Radio;

/** Forgets what a player was listening to, and which station packs their game held, when they leave. */
@Mixin(PlayerList.class)
abstract class RadioPlayerMixin {
    @Inject(method = "remove", at = @At("HEAD"))
    private void earth$radioLeft(final ServerPlayer player, final CallbackInfo callback) {
        if (Radio.enabled()) Radio.left(player);
    }
}
