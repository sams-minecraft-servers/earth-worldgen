package tj.earth.worldgen.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tj.earth.worldgen.Fauna;
import tj.earth.worldgen.Monuments;
import tj.earth.worldgen.UrbanBuilder;

/**
 * Builds the real streets and buildings (see UrbanBuilder) and any monuments (see Monuments) into
 * each chunk of the Earth dimension just before the game adds its trees, plants and structures,
 * and has animals turn up where they
 * really live (see Fauna). Other dimensions are untouched.
 */
@Mixin(ChunkGenerator.class)
abstract class ChunkGeneratorMixin {
    @Inject(method = "applyBiomeDecoration", at = @At("HEAD"))
    private void earth$streets(final WorldGenLevel level, final ChunkAccess chunk, final StructureManager structureManager, final CallbackInfo callback) {
        if (chunk.getHeight() != DimensionType.Y_SIZE) return;
        UrbanBuilder.build(chunk);
        Monuments.build(chunk);
    }

    @Inject(method = "getMobsAt", at = @At("RETURN"), cancellable = true)
    private void earth$fauna(final Level level, final StructureManager structureManager, final MobCategory category, final BlockPos pos,
                             final CallbackInfoReturnable<WeightedList<MobSpawnSettings.SpawnerData>> callback) {
        if (level.getHeight() == DimensionType.Y_SIZE) callback.setReturnValue(Fauna.adjust(callback.getReturnValue(), category, pos.getX(), pos.getZ()));
    }
}
