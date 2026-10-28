package tj.earth.worldgen;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;

/**
 * Whether something belongs to the Earth world. The surest sign is the world's generator using the
 * mod's biome source, which no other world does; where only a chunk or a set of noise settings is at
 * hand, the Earth's exact shape is used instead (Y -2032 to 2031, the most the game allows, with the
 * mod's sea level). Every hook checks here, so other dimensions (the Nether, the End, Twilight Forest,
 * other mods' worlds) are never touched.
 */
public final class Earth {
    private Earth() {}

    public static final int MIN_Y = -2032;

    /**
     * Held while the mod sets up a creature it puts into new land (villagers, monument creatures).
     * Setting one up asks other mods things (Lithostitched keeps villager types in a plain map), and
     * with mods that make chunks on many threads at once (C2ME) two such questions at the same time
     * can break the other mod's map. Creatures are few, so doing them one at a time costs nothing.
     */
    public static final Object CREATURES = new Object();

    public static boolean is(final ChunkGenerator generator) {
        return generator != null && generator.getBiomeSource() instanceof EarthBiomeSource;
    }

    public static boolean is(final Level level) {
        return level instanceof ServerLevel server && is(server.getChunkSource().getGenerator());
    }

    /** For a chunk or anything else that only knows its own heights. */
    public static boolean shaped(final LevelHeightAccessor heights) {
        return heights.getMinY() == MIN_Y && heights.getHeight() == DimensionType.Y_SIZE;
    }

    /** For the noise settings on their own. */
    public static boolean is(final NoiseGeneratorSettings settings) {
        NoiseSettings noise = settings.noiseSettings();
        return noise.minY() == MIN_Y && noise.height() == DimensionType.Y_SIZE && settings.seaLevel() == EarthConfig.get().seaLevel;
    }
}
