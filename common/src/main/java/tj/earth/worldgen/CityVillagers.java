package tj.earth.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The people of the real towns and cities. The game's own villages are switched off in the
 * datapack; instead villagers are put, as a chunk is first made, wherever the land cover map shows
 * built-up ground. How many is "villagersPerCityChunk" in the config (a chunk that is half built
 * up gets half as many).
 */
public final class CityVillagers {
    private CityVillagers() {}

    public static void settle(final WorldGenRegion region) {
        EarthConfig config = EarthConfig.get();
        float perChunk = config.villagersPerCityChunk;
        if (!Earth.is(region.getLevel()) || !(perChunk > 0) || !config.cities.enabled || !config.cities.villagers) return;
        ChunkPos chunk = region.getCenter();
        if (EarthConfig.listed(config.cities.disabledCountries, Countries.atBlock(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ()))) return;

        // How built up the chunk is, from sixteen points across it.
        int[] builtX = new int[16], builtZ = new int[16];
        int built = 0;
        for (int i = 0; i < 16; i++) {
            int x = chunk.getMinBlockX() + 2 + (i & 3) * 4, z = chunk.getMinBlockZ() + 2 + (i >> 2) * 4;
            if (Terrain.column(x, z).cover() == LandCover.BUILT_UP) {
                builtX[built] = x;
                builtZ[built++] = z;
            }
        }
        if (built == 0) return;

        // The same people in the same places every time this chunk is made in this world.
        RandomSource random = RandomSource.create(region.getSeed() ^ chunk.pack() * 0x9E3779B97F4A7C15L);
        float wanted = perChunk * built / 16f;
        int people = (int) wanted + (random.nextFloat() < wanted - (int) wanted ? 1 : 0);
        for (int n = 0; n < people; n++) {
            int at = random.nextInt(built), x = builtX[at], z = builtZ[at];
            BlockPos pos = new BlockPos(x, region.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (!region.getFluidState(pos.below()).isEmpty() || !region.getBlockState(pos).isAir()) continue;
            Villager villager = EntityTypes.VILLAGER.create(region.getLevel(), EntitySpawnReason.CHUNK_GENERATION);
            if (villager == null) continue;
            villager.snapTo(x + 0.5, pos.getY(), z + 0.5, random.nextFloat() * 360f, 0f);
            synchronized (Earth.CREATURES) {
                villager.finalizeSpawn(region, region.getCurrentDifficultyAt(pos), EntitySpawnReason.CHUNK_GENERATION, null);
            }
            region.addFreshEntityWithPassengers(villager);
        }
    }
}
