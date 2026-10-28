package tj.earth.worldgen.mixin;

import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import tj.earth.worldgen.Underground;

/** Ores, geodes, springs, dungeons and everything else the game places "between Y a and Y b": by depth below the real ground instead. */
@Mixin(HeightRangePlacement.class)
abstract class HeightRangePlacementMixin {
    @Redirect(method = "modify", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/heightproviders/HeightProvider;sample(Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/WorldGenerationContext;)I"))
    private int earth$height(final HeightProvider height, final RandomSource random, final WorldGenerationContext context,
                             final PlacementContext placement, final RandomSource sameRandom, final BlockPos origin, final Consumer<BlockPos> output) {
        return Underground.height(height, random, context, origin.getX(), origin.getZ());
    }
}
