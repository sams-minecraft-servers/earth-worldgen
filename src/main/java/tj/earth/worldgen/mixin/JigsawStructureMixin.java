package tj.earth.worldgen.mixin;

import java.util.Optional;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import tj.earth.worldgen.Underground;

/**
 * Structures built from pieces that start at a fixed height (trial chambers, ancient cities) start
 * that far below the real ground instead. Those that start on the surface (villages...) already follow it.
 */
@Mixin(JigsawStructure.class)
abstract class JigsawStructureMixin {
    @Shadow @Final
    private Optional<Heightmap.Types> projectStartToHeightmap;

    @Redirect(method = "findGenerationPoint", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/heightproviders/HeightProvider;sample(Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/WorldGenerationContext;)I"))
    private int earth$startHeight(final HeightProvider height, final RandomSource random, final WorldGenerationContext heights, final Structure.GenerationContext context) {
        if (this.projectStartToHeightmap.isPresent()) return height.sample(random, heights);
        return Underground.height(height, random, heights, context.chunkPos().getMiddleBlockX(), context.chunkPos().getMiddleBlockZ());
    }
}
