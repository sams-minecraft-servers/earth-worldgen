package tj.earth.worldgen.mixin;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.CarverOutput;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.carver.CanyonWorldCarver;
import net.minecraft.world.level.levelgen.carver.CaveWorldCarver;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import tj.earth.worldgen.Underground;

/** Caves and ravines start at their usual depth below the real ground of the chunk they start in. */
@Mixin({CaveWorldCarver.class, CanyonWorldCarver.class})
abstract class CarverMixin {
    @Redirect(method = "carve", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/heightproviders/HeightProvider;sample(Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/WorldGenerationContext;)I"))
    private int earth$startY(final HeightProvider height, final RandomSource random, final WorldGenerationContext context,
                             final WorldGenerationContext sameContext, final RandomSource sameRandom, final ChunkPos chunk, final ChunkPos startChunk, final CarverOutput output) {
        return Underground.height(height, random, context, startChunk.getMiddleBlockX(), startChunk.getMiddleBlockZ());
    }
}
