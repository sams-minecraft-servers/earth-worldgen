package tj.earth.worldgen.mixin;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import tj.earth.worldgen.EarthBiomeSource;
import tj.earth.worldgen.EarthConfig;
import tj.earth.worldgen.EarthWorldgen;
import tj.earth.worldgen.Underground;

/**
 * The Earth world's list of structures it may make, set up once as a world loads:
 * <ul>
 * <li>sets switched off in the config (structures.disabled) are taken out. A set that is not on the
 * list is never placed, and nothing that looks for structures (eyes of ender, /locate, maps) finds
 * it either;</li>
 * <li>strongholds are spread over the whole planet (one in each 512 x 512 chunks, as the datapack
 * also says), instead of the game's rings round X 0 Z 0, which on Earth is open sea in the Gulf of
 * Guinea.</li>
 * </ul>
 * It is also where the mod learns that the Earth world is loaded (Underground.earthWorld). Other
 * dimensions and other worlds are untouched.
 */
@Mixin(ChunkGeneratorStructureState.class)
abstract class ChunkGeneratorStructureStateMixin {
    @ModifyVariable(method = "createForNormal", at = @At("STORE"), ordinal = 0)
    private static List<Holder<StructureSet>> earth$structureSets(final List<Holder<StructureSet>> sets, final RandomState randomState,
                                                                  final long levelSeed, final ChunkPos origin, final BiomeSource biomeSource,
                                                                  final HolderLookup<StructureSet> allStructures) {
        if (!(biomeSource instanceof EarthBiomeSource)) return sets;
        Underground.earthWorld = true;
        List<String> off = EarthConfig.get().structures.disabled.stream().map(EarthConfig::fullId).toList();
        List<Holder<StructureSet>> kept = sets.stream()
            .filter(set -> set.unwrapKey().map(key -> !off.contains(key.identifier().toString())).orElse(true))
            .map(ChunkGeneratorStructureStateMixin::earth$spreadOut)
            .toList();
        if (!off.isEmpty() && kept.size() != sets.size()) EarthWorldgen.LOG.info("Earth: structures switched off: {}", off);
        return kept;
    }

    private static Holder<StructureSet> earth$spreadOut(final Holder<StructureSet> set) {
        if (!(set.value().placement() instanceof ConcentricRingsStructurePlacement)) return set;
        return Holder.direct(new StructureSet(set.value().structures(), new RandomSpreadStructurePlacement(512, 256, RandomSpreadType.LINEAR, 0)));
    }
}
