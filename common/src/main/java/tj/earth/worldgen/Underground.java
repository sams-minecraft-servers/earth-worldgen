package tj.earth.worldgen;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Puts what the game buries (ores, caves, mineshafts, strongholds...) at the right depth below the
 * real ground. The game gives all of these fixed heights ("diamonds below Y 16", "caves up to Y 180")
 * that assume ground near Y 64 over a world 384 blocks tall; here the ground is anywhere from the
 * bottom of the Mariana Trench to the top of Everest. So each height is worked out as it would be
 * in an ordinary world and then moved by how far the real ground at that place is from Y 64.
 */
public final class Underground {
    /** The ordinary world the game's heights were written for. */
    public static final int VANILLA_GROUND = 64, VANILLA_MIN_Y = -64, VANILLA_HEIGHT = 384, VANILLA_SEA_LEVEL = 63;
    /** How far below the ground the deepest of the game's buried things lie: its ground down to its bedrock. */
    public static final int DEPTH = VANILLA_GROUND - VANILLA_MIN_Y;
    /** The thickness of ground under a sea, lake or river that no cave may break: caves never drain the water above them. */
    public static final int WATERTIGHT = 8;

    /** A WorldGenerationContext that can be made to answer as the ordinary world would (see WorldGenerationContextMixin). */
    public interface VanillaHeights {
        void earth$vanillaHeights(boolean on);
    }

    /** Whether this server's overworld is the Earth dimension (the datapack is in its world): set when its biome source is made. */
    public static volatile boolean earthWorld;

    private Underground() {}

    public static boolean isEarth(final WorldGenerationContext context) {
        return context.getGenDepth() == DimensionType.Y_SIZE;
    }

    /** The Y of the first block above the ground (under water: above the bed). */
    public static int ground(final int blockX, final int blockZ) {
        return Terrain.column(blockX, blockZ).top();
    }

    /** A height from one of the game's height rules, for something at this column. Other dimensions get the game's own answer. */
    public static int height(final HeightProvider rule, final RandomSource random, final WorldGenerationContext context, final int blockX, final int blockZ) {
        if (!isEarth(context)) return rule.sample(random, context);
        VanillaHeights view = (VanillaHeights) context;
        int y;
        view.earth$vanillaHeights(true);
        try {
            y = rule.sample(random, context);
        } finally {
            view.earth$vanillaHeights(false);
        }
        // The game often picks a height under the bottom of its world, where nothing comes of it (half of
        // all diamond attempts go that way). That has to stay so, or there would be twice the diamonds.
        if (y < VANILLA_MIN_Y) return DimensionType.MIN_Y - DEPTH;
        return Math.min(y + ground(blockX, blockZ) - VANILLA_GROUND, DimensionType.MAX_Y);
    }

    /** The lowest ground anywhere over a structure, so that what is buried under a slope does not stick out of its foot. */
    public static int lowestGround(final BoundingBox box) {
        int lowest = Integer.MAX_VALUE;
        for (int i = 0; i <= 4; i++) {
            for (int j = 0; j <= 4; j++) {
                lowest = Math.min(lowest, ground(box.minX() + (box.maxX() - box.minX()) * i / 4, box.minZ() + (box.maxZ() - box.minZ()) * j / 4));
            }
        }
        return lowest;
    }
}
