package tj.earth.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;

/**
 * "earth_worldgen:below_ground" for the datapack's density functions: one of the game's own
 * underground patterns (its noise caves, its ore veins) laid under the real ground instead of
 * under Y 64. The input is read as in an ordinary world, at the same depth below the ground, so
 * the game's heights in it ("iron veins between Y -60 and -8") mean the same depth here as there
 * (see Underground). Wherever the ordinary world has no ground (above its Y 64+, below its
 * bottom) the answer is "outside".
 *
 * With "watertight", nothing but "outside" is given within Underground.WATERTIGHT blocks of the
 * bed of a sea, lake or river, or beside one: caves never open into the water (as with the
 * carvers, see EarthCarvingMask).
 */
public record BelowGround(DensityFunction input, float outside, boolean watertight) implements DensityFunction {
    public static final MapCodec<BelowGround> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        DensityFunction.CODEC.fieldOf("input").forGetter(BelowGround::input),
        Codec.FLOAT.fieldOf("outside").forGetter(BelowGround::outside),
        Codec.BOOL.optionalFieldOf("watertight", false).forGetter(BelowGround::watertight)
    ).apply(i, BelowGround::new));

    /** The part of an ordinary world that is read: its bottom up to a little over its ground. */
    private static final int BAND_MIN_Y = Underground.VANILLA_MIN_Y, BAND_MAX_Y = Underground.VANILLA_GROUND + 16;

    @Override
    public DensitySampler compileSampler(final DensityFunction.CompileContext context) {
        return new Sampler(input.compileSampler(context), outside, watertight);
    }

    @Override
    public DensityFunction rewriteChildren(final DfRewriteRule rule) {
        DensityFunction rewritten = rule.rewrite(input);
        return rewritten == input ? this : new BelowGround(rewritten, outside, watertight);
    }

    @Override
    public Interval range() {
        return Interval.encapsulating(input.range(), Interval.ofExact(outside));
    }

    @Override
    public int domainAxes() {
        return ALL_AXES;
    }

    @Override
    public MapCodec<BelowGround> codec() {
        return CODEC;
    }

    /** How far the ordinary world is moved at a column: its Y = this Y minus the shift. */
    private static int shift(final int blockX, final int blockZ) {
        return Underground.ground(blockX, blockZ) - Underground.VANILLA_GROUND;
    }

    /** The highest Y at a column where the input may be used; above it, "outside". */
    private static int ceiling(final int blockX, final int blockZ, final boolean watertight) {
        int ceiling = BAND_MAX_Y + shift(blockX, blockZ);
        return watertight ? Math.min(ceiling, EarthCarvingMask.ceiling(blockX, blockZ)) : ceiling;
    }

    private record Sampler(DensitySampler input, float outside, boolean watertight) implements DensitySampler {
        @Override
        public void sampleVolume(final SamplerContext context, final DensityBuffer output, final DensityVolume volume) {
            // The input is read once over the ordinary world's band, with the same columns as asked
            // for, then each column takes the part of it that its own ground lines up with.
            int step = volume.stepBlockY();
            int bandMin = Math.floorDiv(BAND_MIN_Y, step) * step;
            int bandSize = (BAND_MAX_Y - bandMin) / step + 1;
            DensityVolume band = new DensityVolume(volume.sizeX(), bandSize, volume.sizeZ(), volume.minBlockX(), bandMin, volume.minBlockZ(),
                volume.stepBlockX(), step, volume.stepBlockZ());
            try (ScopedDensityBuffer values = context.acquireBuffer(band)) {
                input.sampleVolume(context, values, band);
                for (int z = 0; z < volume.sizeZ(); z++) {
                    int blockZ = volume.blockZ(z);
                    for (int x = 0; x < volume.sizeX(); x++) {
                        int blockX = volume.blockX(x);
                        int shift = shift(blockX, blockZ), ceiling = ceiling(blockX, blockZ, watertight);
                        int out = volume.indexUnchecked(x, 0, z), in = band.indexUnchecked(x, 0, z);
                        for (int y = 0; y < volume.sizeY(); y++) {
                            int blockY = volume.blockY(y);
                            int vanillaY = blockY - shift;
                            int at = Math.floorDiv(vanillaY - bandMin, step);
                            if (blockY > ceiling || vanillaY < BAND_MIN_Y || vanillaY > BAND_MAX_Y) output.set(out + y, outside);
                            else if (Math.floorMod(vanillaY - bandMin, step) == 0) output.set(out + y, values.get(in + at));
                            else output.set(out + y, input.sampleValue(context, blockX, vanillaY, blockZ)); // between the band's rows
                        }
                    }
                }
            }
        }

        @Override
        public float sampleValue(final SamplerContext context, final int blockX, final int blockY, final int blockZ) {
            int vanillaY = blockY - shift(blockX, blockZ);
            if (vanillaY < BAND_MIN_Y || vanillaY > BAND_MAX_Y || blockY > ceiling(blockX, blockZ, watertight)) return outside;
            return input.sampleValue(context, blockX, vanillaY, blockZ);
        }
    }
}
