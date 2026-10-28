package tj.earth.worldgen;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

/**
 * Where water goes. The game's own rule is one sea level for the whole world, with lava below
 * Y -54; here the sea is at sea level, each lake and river stands at its real height, land below
 * sea level stays dry, and the ocean deeps are water all the way down. Under the bed of any of
 * them the ground is dry: a cave down there is a cave, not a flooded one (see EarthCarvingMask).
 */
public final class EarthFluids {
    private static final Aquifer.FluidStatus NONE = new Aquifer.FluidStatus(Terrain.DRY, Blocks.AIR.defaultBlockState());

    private EarthFluids() {}

    /** Our dimension is the one that uses the full height the game allows. */
    public static boolean isEarth(final NoiseGeneratorSettings settings) {
        return settings.noiseSettings().height() == DimensionType.Y_SIZE;
    }

    public static Aquifer.FluidPicker picker(final NoiseGeneratorSettings settings) {
        // One answer per water level, made the first time a lake or river at that height is met.
        final Aquifer.FluidStatus[] byLevel = new Aquifer.FluidStatus[DimensionType.Y_SIZE + 1];
        return (x, y, z) -> {
            Terrain.Column column = Terrain.column(x, z);
            int level = column.waterLevel();
            if (level == Terrain.DRY || y < column.top() - Underground.WATERTIGHT) return NONE;
            Aquifer.FluidStatus status = byLevel[level - DimensionType.MIN_Y];
            if (status == null) byLevel[level - DimensionType.MIN_Y] = status = new Aquifer.FluidStatus(level, settings.defaultFluid());
            return status;
        };
    }
}
