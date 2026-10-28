package tj.earth.worldgen.mixin;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import tj.earth.worldgen.EarthBiomeSource;
import tj.earth.worldgen.EarthConfig;
import tj.earth.worldgen.EarthWorldgen;

/**
 * Takes the structure sets switched off in the config (structures.disabled) out of the Earth
 * world's list of structures it may make. A set that is not on the list is never placed, and
 * nothing that looks for structures (eyes of ender, /locate, maps) finds it either. Other
 * dimensions are untouched.
 */
@Mixin(ChunkGeneratorStructureState.class)
abstract class ChunkGeneratorStructureStateMixin {
    @ModifyVariable(method = "createForNormal", at = @At("STORE"), ordinal = 0)
    private static List<Holder<StructureSet>> earth$switchedOff(final List<Holder<StructureSet>> sets, final RandomState randomState,
                                                               final long levelSeed, final ChunkPos origin, final BiomeSource biomeSource,
                                                               final HolderLookup<StructureSet> allStructures) {
        if (!(biomeSource instanceof EarthBiomeSource)) return sets;
        List<String> off = EarthConfig.get().structures.disabled.stream().map(EarthConfig::fullId).toList();
        if (off.isEmpty()) return sets;
        List<Holder<StructureSet>> kept = sets.stream()
            .filter(set -> set.unwrapKey().map(key -> !off.contains(key.identifier().toString())).orElse(true))
            .toList();
        if (kept.size() != sets.size()) EarthWorldgen.LOG.info("Earth: structures switched off: {}", off);
        return kept;
    }
}
