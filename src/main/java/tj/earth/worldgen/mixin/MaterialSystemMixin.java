package tj.earth.worldgen.mixin;

import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.material.MaterialSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import tj.earth.worldgen.EarthFill;

/**
 * The game asks its material rule about every block of a column, down to the bottom of the world.
 * In the Earth dimension that is some two thousand blocks of deep rock per column that no rule
 * changes, so there the walk down stops a little below the ground. Other dimensions are untouched.
 */
@Mixin(MaterialSystem.class)
abstract class MaterialSystemMixin {
    @Redirect(method = "buildSurface", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkAccess;getMinY()I"))
    private int earth$materialsFrom(final ChunkAccess chunk) {
        return chunk.getHeight() == DimensionType.Y_SIZE ? EarthFill.materialsFrom(chunk) : chunk.getMinY();
    }
}
