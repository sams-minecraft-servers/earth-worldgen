package tj.earth.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;

/**
 * "earth_worldgen:earth" for the datapack's dimension: the biome of each place, from where it is
 * on the real Earth. That is its real land cover (forest, crops, bare rock, water...) read against its height and latitude; measured climate follows.
 */
public final class EarthBiomeSource extends BiomeSource {
    public static final MapCodec<EarthBiomeSource> CODEC = RecordCodecBuilder.mapCodec(
        i -> i.group(RegistryOps.retrieveGetter(Registries.BIOME)).apply(i, i.stable(EarthBiomeSource::new)));

    private final Holder<Biome> ocean, deepOcean, river, beach, plains, forest, taiga, jungle, savanna, desert, swamp, mangroves,
        meadow, stonyPeaks, frozenPeaks, snowyPlains, gravellyHills;

    private EarthBiomeSource(final HolderGetter<Biome> biomes) {
        Underground.earthWorld = true;
        this.ocean = biomes.getOrThrow(Biomes.OCEAN);
        this.deepOcean = biomes.getOrThrow(Biomes.DEEP_OCEAN);
        this.river = biomes.getOrThrow(Biomes.RIVER);
        this.beach = biomes.getOrThrow(Biomes.BEACH);
        this.plains = biomes.getOrThrow(Biomes.PLAINS);
        this.forest = biomes.getOrThrow(Biomes.FOREST);
        this.taiga = biomes.getOrThrow(Biomes.TAIGA);
        this.jungle = biomes.getOrThrow(Biomes.JUNGLE);
        this.savanna = biomes.getOrThrow(Biomes.SAVANNA);
        this.desert = biomes.getOrThrow(Biomes.DESERT);
        this.swamp = biomes.getOrThrow(Biomes.SWAMP);
        this.mangroves = biomes.getOrThrow(Biomes.MANGROVE_SWAMP);
        this.meadow = biomes.getOrThrow(Biomes.MEADOW);
        this.stonyPeaks = biomes.getOrThrow(Biomes.STONY_PEAKS);
        this.frozenPeaks = biomes.getOrThrow(Biomes.FROZEN_PEAKS);
        this.snowyPlains = biomes.getOrThrow(Biomes.SNOWY_PLAINS);
        this.gravellyHills = biomes.getOrThrow(Biomes.WINDSWEPT_GRAVELLY_HILLS);
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.of(ocean, deepOcean, river, beach, plains, forest, taiga, jungle, savanna, desert, swamp, mangroves,
            meadow, stonyPeaks, frozenPeaks, snowyPlains, gravellyHills);
    }

    @Override
    protected MapCodec<EarthBiomeSource> codec() {
        return CODEC;
    }

    @Override
    public BiomeResolver createResolver(final Climate.Sampler sampler) {
        return (quartX, quartY, quartZ) -> biomeAt(QuartPos.toBlock(quartX) + 2, QuartPos.toBlock(quartZ) + 2);
    }

    private Holder<Biome> biomeAt(final int blockX, final int blockZ) {
        Terrain.Column column = Terrain.column(blockX, blockZ);
        float metres = column.metres();
        int cover = column.cover();
        if (Terrain.isWater(cover, metres)) {
            if (column.waterLevel() != EarthConfig.get().seaLevel) return river;
            return metres < -1000 ? deepOcean : ocean;
        }
        double latitude = Math.abs(EarthConfig.get().latitude(blockZ));
        // Roughly where permanent snow and the last trees are found: lower towards the poles.
        double snowLine = Math.max(0, 5000 - 48 * latitude), treeLine = snowLine - 900;
        boolean tropical = latitude < 23.5, cold = metres > treeLine - 900 || latitude > 58;
        return switch (cover) {
            case LandCover.TREES -> tropical && metres < 1500 ? jungle : cold ? taiga : forest;
            case LandCover.SHRUBS -> latitude < 35 ? savanna : metres > treeLine ? meadow : plains;
            case LandCover.GRASS -> metres > treeLine - 300 ? meadow : plains;
            case LandCover.CROPS, LandCover.BUILT_UP -> plains;
            case LandCover.BARE -> metres > treeLine ? stonyPeaks : latitude < 40 ? desert : gravellyHills;
            case LandCover.SNOW_AND_ICE -> metres > 1500 ? frozenPeaks : snowyPlains;
            case LandCover.WETLAND -> swamp;
            case LandCover.MANGROVES -> mangroves;
            case LandCover.MOSS_AND_LICHEN -> snowyPlains;
            default -> {
                // No land cover map here: go by height alone.
                if (metres > snowLine) yield frozenPeaks;
                if (metres > treeLine + 400) yield stonyPeaks;
                if (metres > treeLine) yield meadow;
                if (cold) yield taiga;
                yield metres > 350 ? forest : plains;
            }
        };
    }
}
