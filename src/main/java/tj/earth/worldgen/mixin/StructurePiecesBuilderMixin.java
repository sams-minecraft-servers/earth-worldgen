package tj.earth.worldgen.mixin;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tj.earth.worldgen.Underground;

/**
 * Mineshafts and strongholds are put "somewhere below sea level, above the bottom of the world".
 * In the Earth dimension that is: somewhere in the 128 blocks below the lowest ground over them.
 */
@Mixin(StructurePiecesBuilder.class)
abstract class StructurePiecesBuilderMixin {
    @Shadow
    public abstract BoundingBox getBoundingBox();

    @Shadow
    public abstract void offsetPiecesVertically(int dy);

    @Inject(method = "moveBelowSeaLevel", at = @At("HEAD"), cancellable = true)
    private void earth$belowGround(final int seaLevel, final int minY, final RandomSource random, final int offset, final CallbackInfoReturnable<Integer> callback) {
        if (minY != DimensionType.MIN_Y) return;
        BoundingBox box = this.getBoundingBox();
        int ground = Underground.lowestGround(box);
        int highest = ground - 1 - offset, top = box.getYSpan() + ground - Underground.DEPTH + 1;
        if (top < highest) top += random.nextInt(highest - top);
        int dy = top - box.maxY();
        this.offsetPiecesVertically(dy);
        callback.setReturnValue(dy);
    }
}
