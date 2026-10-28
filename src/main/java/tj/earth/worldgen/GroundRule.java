package tj.earth.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import net.minecraft.world.level.levelgen.material.rule.RuleEvaluator;

/**
 * "earth_worldgen:ground" for the datapack's material rule: what the ground is made of at each
 * depth below the real surface. The game's own rule goes by fixed heights (deepslate below Y 0),
 * which means nothing in a world whose ground runs from Y -2027 to Y 1930, and it is asked about
 * every one of the two thousand blocks under a column; this one answers the deep ones at once.
 *
 * The noise settings fill the world with the deep rock, so from the surface down it is: "surface"
 * (soil, sand, snow: the game's rule) where that gives a block, then "rock" for about "rock_depth"
 * blocks, then the deep rock untouched. (The game is only made to ask about the blocks near the
 * surface at all: see MaterialSystemMixin. The bedrock under the world is laid by EarthFill.)
 */
public record GroundRule(MaterialRule surface, BlockState rock, int rockDepth) implements MaterialRule {
    public static final MapCodec<GroundRule> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        MaterialRule.CODEC.fieldOf("surface").forGetter(GroundRule::surface),
        BlockState.CODEC.fieldOf("rock").forGetter(GroundRule::rock),
        Codec.intRange(1, 96).fieldOf("rock_depth").forGetter(GroundRule::rockDepth)
    ).apply(i, GroundRule::new));

    /** How far below the surface the game's surface rule is still asked, and the height of the uneven edges between layers. */
    private static final int SURFACE_REACH = 24, BLEND = 8;

    @Override
    public MapCodec<GroundRule> codec() {
        return CODEC;
    }

    @Override
    public RuleEvaluator compile(final MaterialRuleContext context) {
        final RuleEvaluator surface = this.surface.compile(context);
        return new RuleEvaluator() {
            private int x = Integer.MIN_VALUE, z, top;

            @Override
            public BlockState tryApply(final int blockX, final int blockY, final int blockZ) {
                if (blockX != x || blockZ != z) {
                    x = blockX;
                    z = blockZ;
                    top = Terrain.column(blockX, blockZ).top();
                }
                int depth = top - blockY;
                if (depth > rockDepth + BLEND) return null;
                if (depth <= SURFACE_REACH) {
                    BlockState state = surface.tryApply(blockX, blockY, blockZ);
                    if (state != null) return state;
                }
                // An uneven edge between the rock and the deep rock, the same every time for the same block.
                int scatter = (blockX * 0x9E3779B1 ^ blockY * 0x85EBCA6B ^ blockZ * 0xC2B2AE35) >>> 16 & (BLEND - 1);
                return depth <= rockDepth + scatter ? rock : null;
            }
        };
    }
}
