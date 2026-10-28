package tj.earth.worldgen.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tj.earth.worldgen.EarthWorldgen;

/**
 * The game closes its built-in registries before mods are started, so our entries are added
 * at the last moment they can be: just before the registries are frozen.
 */
@Mixin(BuiltInRegistries.class)
abstract class BuiltInRegistriesMixin {
    @Inject(method = "freeze", at = @At("HEAD"))
    private static void earth$register(final CallbackInfo callback) {
        EarthWorldgen.register();
    }
}
