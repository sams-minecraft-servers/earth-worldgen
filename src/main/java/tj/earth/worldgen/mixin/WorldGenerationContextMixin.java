package tj.earth.worldgen.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import tj.earth.worldgen.Underground;

/**
 * Lets a height rule be read the way it would be in an ordinary world ("80 above the bottom" from
 * Y -64, not from Y -2032) for as long as Underground asks for that. See Underground.height.
 */
@Mixin(WorldGenerationContext.class)
abstract class WorldGenerationContextMixin implements Underground.VanillaHeights {
    @Unique
    private boolean earth$vanilla;

    @Override
    public void earth$vanillaHeights(final boolean on) {
        this.earth$vanilla = on;
    }

    @ModifyReturnValue(method = "getMinGenY", at = @At("RETURN"))
    private int earth$minGenY(final int minY) {
        return this.earth$vanilla ? Underground.VANILLA_MIN_Y : minY;
    }

    @ModifyReturnValue(method = "getGenDepth", at = @At("RETURN"))
    private int earth$genDepth(final int height) {
        return this.earth$vanilla ? Underground.VANILLA_HEIGHT : height;
    }

    @ModifyReturnValue(method = "seaLevel", at = @At("RETURN"))
    private int earth$seaLevel(final int seaLevel) {
        return this.earth$vanilla ? Underground.VANILLA_SEA_LEVEL : seaLevel;
    }
}
