package tj.earth.worldgen;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;

/**
 * Turns the noise settings into blocks for one chunk of the Earth dimension. It gives the same
 * result as the game's own step, but the game works out and stores every one of a chunk's million
 * blocks one at a time; here only the layers the surface and the noise caves under it pass
 * through are worked out. All the 16-block cubes below them are solid rock and are stored as such in one go (which is also how
 * they stay small in memory), and everything above the highest ground or water is left as air.
 * The bottom of the world is closed with bedrock here too.
 */
public final class EarthFill {
    /** How far below the lowest ground in a chunk the block-by-block work starts: under the deepest noise caves (see BelowGround). */
    private static final int MARGIN = Underground.DEPTH + 16;
    /** How far below the lowest ground in a chunk the game's material rule is still asked what the ground is made of. */
    private static final int MATERIAL_REACH = 128;
    private static final int BEDROCK_LAYERS = 5;
    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
    private static final Strategy<BlockState> BLOCK_STATES = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);

    /** The chunk a thread filled last and the Y of its lowest ground, for materialsFrom, which the game calls next. */
    private static final ThreadLocal<long[]> LAST = ThreadLocal.withInitial(() -> new long[] {Long.MIN_VALUE, 0});

    private EarthFill() {}

    /** The lowest Y at which the game needs to ask the material rule about this chunk: all below is deep rock. */
    public static int materialsFrom(final ChunkAccess chunk) {
        long[] last = LAST.get();
        if (last[0] != chunk.getPos().pack()) {
            int lowest = Integer.MAX_VALUE;
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) lowest = Math.min(lowest, Terrain.column(chunk.getPos().getMinBlockX() + x, chunk.getPos().getMinBlockZ() + z).top());
            }
            last[0] = chunk.getPos().pack();
            last[1] = lowest;
        }
        return Math.max(chunk.getMinY(), (int) last[1] - MATERIAL_REACH);
    }

    public static void fill(final NoiseGeneratorSettings settings, final NoiseChunk noiseChunk, final ChunkAccess chunk) {
        DensityVolume whole = noiseChunk.volume();
        float lowest = Float.MAX_VALUE, highest = -Float.MAX_VALUE;
        for (int z = 0; z < whole.sizeZ(); z++) {
            for (int x = 0; x < whole.sizeX(); x++) {
                Terrain.Column column = Terrain.column(whole.blockX(x), whole.blockZ(z));
                lowest = Math.min(lowest, column.surfaceY());
                highest = Math.max(highest, Math.max(column.surfaceY(), column.waterLevel()));
            }
        }

        long[] last = LAST.get();
        last[0] = chunk.getPos().pack();
        last[1] = (int) Math.ceil(lowest);

        // Whole cubes of rock, from the bottom of the world up to the cube the block-by-block work starts in.
        BlockState rock = settings.defaultBlock();
        LevelChunkSection[] sections = chunk.getSections();
        int firstWorked = Math.max(chunk.getSectionIndex(whole.minBlockY()), Math.min(chunk.getSectionIndex(whole.maxBlockY()), chunk.getSectionIndex((int) Math.floor(lowest) - MARGIN)));
        for (int i = chunk.getSectionIndex(whole.minBlockY()); i < firstWorked; i++) {
            sections[i] = new LevelChunkSection(new PalettedContainer<>(rock, BLOCK_STATES), sections[i].getBiomes());
        }
        if (firstWorked > 0 && whole.minBlockY() == chunk.getMinY()) bedrock(sections[0], whole);

        int bottom = Math.max(whole.minBlockY(), SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(firstWorked)));
        int top = Math.max(bottom, Math.min(whole.maxBlockY(), (int) Math.ceil(highest) + 1));
        DensityVolume volume = new DensityVolume(whole.sizeX(), top - bottom + 1, whole.sizeZ(), whole.minBlockX(), bottom, whole.minBlockZ());

        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        Aquifer aquifer = noiseChunk.aquifer();
        DensitySampler.Bound finalDensity = noiseChunk.cachingSamplers().get(settings.noiseRouter().finalDensity());
        try (ScopedDensityBuffer densities = finalDensity.sampleVolume(volume)) {
            for (int z = 0; z < volume.sizeZ(); z++) {
                int blockZ = volume.blockZ(z);
                for (int x = 0; x < volume.sizeX(); x++) {
                    int blockX = volume.blockX(x);
                    for (int y = volume.sizeY() - 1; y >= 0; y--) {
                        int blockY = volume.blockY(y);
                        BlockState state = aquifer.computeSubstance(blockX, blockY, blockZ, densities.get(volume.indexUnchecked(x, y, z)));
                        if (state == null) state = blockY < chunk.getMinY() + BEDROCK_LAYERS && bedrockAt(blockX, blockY - chunk.getMinY(), blockZ) ? BEDROCK : rock;
                        if (state.isAir()) continue;
                        sections[chunk.getSectionIndex(blockY)].setBlockState(x, SectionPos.sectionRelative(blockY), z, state, false);
                        oceanFloor.update(x, blockY, z, state);
                        worldSurface.update(x, blockY, z, state);
                    }
                }
            }
        }
    }

    /** The floor of the world: solid bedrock at the very bottom, thinning out over the layers above it. */
    private static void bedrock(final LevelChunkSection section, final DensityVolume whole) {
        for (int layer = 0; layer < BEDROCK_LAYERS; layer++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (bedrockAt(whole.minBlockX() + x, layer, whole.minBlockZ() + z)) section.setBlockState(x, layer, z, BEDROCK, false);
                }
            }
        }
    }

    private static boolean bedrockAt(final int blockX, final int layer, final int blockZ) {
        int chance = (blockX * 0x9E3779B1 ^ layer * 0x85EBCA6B ^ blockZ * 0xC2B2AE35) >>> 16 & 0xff;
        return chance * BEDROCK_LAYERS < (BEDROCK_LAYERS - layer) * 256;
    }
}
