package tj.earth.worldgen.mixin;

import tj.earth.worldgen.Earth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.structures.RuinedPortalPiece;
import net.minecraft.world.level.levelgen.structure.structures.RuinedPortalStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import tj.earth.worldgen.Underground;

/** Buried ruined portals: anywhere down to the bottom of the world in the game, within reach of the surface in the Earth dimension. */
@Mixin(RuinedPortalStructure.class)
abstract class RuinedPortalStructureMixin {
    @Shadow
    private static int getRandomWithinInterval(final RandomSource random, final int minPreferred, final int max) {
        throw new AssertionError();
    }

    @Redirect(method = "findSuitableY", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/structure/structures/RuinedPortalStructure;getRandomWithinInterval(Lnet/minecraft/util/RandomSource;II)I"))
    private static int earth$buriedY(final RandomSource random, final int lowest, final int highest,
                                     final RandomSource sameRandom, final ChunkGenerator generator, final RuinedPortalPiece.VerticalPlacement placement, final boolean airPocket,
                                     final int surfaceY, final int ySpan, final BoundingBox box, final LevelHeightAccessor heights, final RandomState randomState) {
        boolean earth = Earth.is(generator);
        return getRandomWithinInterval(random, earth ? Math.max(lowest, highest - Underground.DEPTH) : lowest, highest);
    }
}
