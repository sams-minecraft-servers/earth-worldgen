package tj.earth.worldgen.mixin;

import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tj.earth.worldgen.Radio;

/** Runs the radio once a tick (starting it on the first), and saves the dial when the server stops. */
@Mixin(MinecraftServer.class)
abstract class RadioServerMixin {
    @Unique private boolean earth$radioStarted;

    @Inject(method = "tickServer", at = @At("TAIL"))
    private void earth$radioTick(final BooleanSupplier haveTime, final CallbackInfo callback) {
        if (!Radio.enabled()) return;
        if (!earth$radioStarted) {
            earth$radioStarted = true;
            Radio.started((MinecraftServer) (Object) this);
        }
        Radio.tick((MinecraftServer) (Object) this);
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void earth$radioSave(final CallbackInfo callback) {
        if (Radio.enabled()) Radio.saveDial();
    }
}
