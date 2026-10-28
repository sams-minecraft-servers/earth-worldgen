package tj.earth.worldgen.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.structures.OceanMonumentPieces;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import tj.earth.worldgen.Underground;

/**
 * The game builds every ocean monument with its floor at Y 39, for a sea whose surface is at Y 63.
 * In the Earth dimension a monument stands on the real sea bed: its floor on the lowest ground
 * under it. Everything inside a monument is laid out from this one box, and the game makes the
 * same box again each time the monument is loaded, so this is the only place that needs to know.
 */
@Mixin(OceanMonumentPieces.MonumentBuilding.class)
abstract class OceanMonumentMixin {
    @WrapOperation(method = "<init>(Lnet/minecraft/util/RandomSource;IILnet/minecraft/core/Direction;)V",
        at = @At(value = "INVOKE", target = "makeBoundingBox(IIILnet/minecraft/core/Direction;III)Lnet/minecraft/world/level/levelgen/structure/BoundingBox;"))
    private static BoundingBox earth$onTheSeaBed(final int x, final int y, final int z, final Direction direction, final int width, final int height, final int depth,
                                                 final Operation<BoundingBox> original) {
        BoundingBox box = original.call(x, y, z, direction, width, height, depth);
        return Underground.earthWorld ? box.moved(0, Underground.lowestGround(box) - box.minY(), 0) : box;
    }
}
