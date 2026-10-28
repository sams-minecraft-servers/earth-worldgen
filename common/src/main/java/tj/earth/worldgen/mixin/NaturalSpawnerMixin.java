package tj.earth.worldgen.mixin;

import tj.earth.worldgen.Earth;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import tj.earth.worldgen.Fauna;

/** The animals put in a chunk of the Earth dimension when it is first made are those that live there (see Fauna). */
@Mixin(NaturalSpawner.class)
abstract class NaturalSpawnerMixin {
    @Redirect(method = "spawnMobsForChunkGeneration", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/MobSpawnSettings;getMobsToSpawn(Lnet/minecraft/world/entity/MobCategory;)Lnet/minecraft/util/random/WeightedList;"))
    private static WeightedList<MobSpawnSettings.SpawnerData> earth$fauna(final MobSpawnSettings settings, final MobCategory category,
                                                                         final ServerLevelAccessor level, final BlockPos sourcePos, final ChunkPos chunk, final RandomSource random) {
        WeightedList<MobSpawnSettings.SpawnerData> mobs = settings.getMobsToSpawn(category);
        return Earth.is(level.getLevel()) ? Fauna.adjust(mobs, category, chunk.getMiddleBlockX(), chunk.getMiddleBlockZ()) : mobs;
    }
}
