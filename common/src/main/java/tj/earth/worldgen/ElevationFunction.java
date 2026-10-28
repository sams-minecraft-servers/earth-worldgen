package tj.earth.worldgen;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.Interval;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/**
 * "earth_worldgen:surface" for the datapack's density functions: the Y of the real ground at
 * each column. The noise settings turn it into terrain (solid below, open above), so everything
 * the game adds after that (caves, ores, plants, structures) works as it does in any world.
 */
public record ElevationFunction() implements DensityFunction {
    public static final MapCodec<ElevationFunction> CODEC = MapCodec.unit(new ElevationFunction());

    @Override
    public DensitySampler compileSampler(final DensityFunction.CompileContext context) {
        return Sampler.INSTANCE;
    }

    @Override
    public Interval range() {
        return Interval.of(DimensionType.MIN_Y, DimensionType.MAX_Y + 1);
    }

    @Override
    public int domainAxes() {
        return AXIS_X | AXIS_Z;
    }

    @Override
    public MapCodec<ElevationFunction> codec() {
        return CODEC;
    }

    @Override
    public DensityFunction rewriteChildren(final DfRewriteRule rule) {
        return this;
    }

    private enum Sampler implements DensitySampler {
        INSTANCE;

        @Override
        public void sampleVolume(final SamplerContext context, final DensityBuffer output, final DensityVolume volume) {
            for (int z = 0; z < volume.sizeZ(); z++) {
                int blockZ = volume.blockZ(z);
                for (int x = 0; x < volume.sizeX(); x++) {
                    output.setRange(volume.indexUnchecked(x, 0, z), volume.sizeY(), Terrain.column(volume.blockX(x), blockZ).surfaceY());
                }
            }
        }

        @Override
        public float sampleValue(final SamplerContext context, final int blockX, final int blockY, final int blockZ) {
            return Terrain.column(blockX, blockZ).surfaceY();
        }
    }
}
