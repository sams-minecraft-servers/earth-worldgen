package tj.earth.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;

/**
 * "earth_worldgen:earth" for the datapack's dimension: the biome of each place, from where it is
 * on the real Earth.
 *
 * With biomes.style "climate" (the default), each place is first sorted into a kind of real place
 * ("boreal_forest", "mediterranean_shrubland", "cold_steppe"...) from its land cover (forest,
 * crops, bare rock, water...), its climate (see RealClimate) and its height, and the kind is then given
 * one of its biomes from earth_worldgen/biomes.json: the game's own, or those of Terralith and
 * Biomes O' Plenty when they are installed, or the server's own choice (biomes.custom). Where a kind
 * has several biomes, each gets patches of about biomes.patchBlocks.
 *
 * With "classic", the first way: land cover read against height and latitude, the game's biomes only.
 */
public final class EarthBiomeSource extends BiomeSource {
    public static final MapCodec<EarthBiomeSource> CODEC = RecordCodecBuilder.mapCodec(
        i -> i.group(RegistryOps.retrieveGetter(Registries.BIOME)).apply(i, i.stable(EarthBiomeSource::new)));

    private final Holder<Biome> ocean, deepOcean, river, beach, plains, forest, taiga, jungle, savanna, desert, swamp, mangroves,
        meadow, stonyPeaks, frozenPeaks, snowyPlains, gravellyHills;
    private final boolean classic;
    /** The biomes of each kind of place, in the order of KINDS; a biome listed twice is picked twice as often. */
    private final List<List<Holder<Biome>>> kinds = new ArrayList<>();
    private final Map<String, Integer> kindIndex = new HashMap<>();
    private final int patch;

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
        EarthConfig.BiomeSettings settings = EarthConfig.get().biomes;
        this.classic = "classic".equalsIgnoreCase(settings.style);
        this.patch = Math.max(16, settings.patchBlocks);
        if (!classic) readKinds(biomes, settings);
    }

    /** Fills kinds from biomes.json and the config: the first of custom, the mods' choice, the game's that has a biome that is installed. */
    private void readKinds(final HolderGetter<Biome> biomes, final EarthConfig.BiomeSettings settings) {
        JsonObject table;
        try (Reader in = new InputStreamReader(EarthBiomeSource.class.getResourceAsStream("/earth_worldgen/biomes.json"), StandardCharsets.UTF_8)) {
            table = JsonParser.parseReader(in).getAsJsonObject().getAsJsonObject("kinds");
        } catch (Exception e) {
            throw new IllegalStateException("earth_worldgen/biomes.json could not be read", e);
        }
        Set<String> unknown = new LinkedHashSet<>(settings.custom.keySet());
        Set<String> used = new LinkedHashSet<>();
        for (Map.Entry<String, JsonElement> kind : table.entrySet()) {
            JsonObject sources = kind.getValue().getAsJsonObject();
            List<Holder<Biome>> chosen = List.of();
            List<String> own = settings.custom.get(kind.getKey());
            unknown.remove(kind.getKey());
            if (own != null) chosen = resolve(biomes, own, kind.getKey(), true);
            if (chosen.isEmpty() && settings.modBiomes) {
                List<Holder<Biome>> mods = new ArrayList<>();
                for (String mod : List.of("terralith", "biomesoplenty")) {
                    if (sources.has(mod)) mods.addAll(resolve(biomes, strings(sources.getAsJsonArray(mod)), kind.getKey(), false));
                }
                chosen = mods;
            }
            if (chosen.isEmpty()) chosen = resolve(biomes, strings(sources.getAsJsonArray("vanilla")), kind.getKey(), true);
            if (chosen.isEmpty()) chosen = List.of(plains);
            kindIndex.put(kind.getKey(), kinds.size());
            kinds.add(List.copyOf(chosen));
            for (Holder<Biome> b : chosen) used.add(b.unwrapKey().map(k -> k.identifier().toString()).orElse("?"));
        }
        for (String k : unknown) EarthWorldgen.LOG.warn("Earth: biomes.custom names a kind of place that doesn't exist: {} (the kinds are listed in docs/Biomes.md)", k);
        long fromMods = used.stream().filter(id -> !id.startsWith("minecraft:")).count();
        EarthWorldgen.LOG.info("Earth: biomes by real climate, {} kinds of place, {} biomes in use ({} from other mods)", kinds.size(), used.size(), fromMods);
    }

    private static List<String> strings(final JsonArray array) {
        List<String> out = new ArrayList<>();
        if (array != null) for (JsonElement e : array) out.add(e.getAsString());
        return out;
    }

    /** The installed biomes among ids; those not installed are left out (with a warning when warn is set: the game's own, or the server's choice). */
    private static List<Holder<Biome>> resolve(final HolderGetter<Biome> biomes, final List<String> ids, final String kind, final boolean warn) {
        List<Holder<Biome>> out = new ArrayList<>();
        for (String id : ids) {
            Identifier name = Identifier.tryParse(id.trim().toLowerCase(Locale.ROOT));
            Optional<? extends Holder<Biome>> biome = name == null ? Optional.empty() : biomes.get(ResourceKey.create(Registries.BIOME, name));
            if (biome.isPresent()) out.add(biome.get());
            else if (warn) EarthWorldgen.LOG.warn("Earth: biome {} (for {}) is not installed; left out", id, kind);
        }
        return out;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        if (!classic) return Stream.concat(kinds.stream().flatMap(List::stream), Stream.of(plains)).distinct();
        return Stream.of(ocean, deepOcean, river, beach, plains, forest, taiga, jungle, savanna, desert, swamp, mangroves,
            meadow, stonyPeaks, frozenPeaks, snowyPlains, gravellyHills);
    }

    @Override
    protected MapCodec<EarthBiomeSource> codec() {
        return CODEC;
    }

    @Override
    public BiomeResolver createResolver(final Climate.Sampler sampler) {
        return (quartX, quartY, quartZ) -> cached(quartX, quartZ);
    }

    /** The biome is the same all the way up a column, and the game asks for it at every fourth block of height: so each thread keeps its last columns. */
    private final ThreadLocal<Object[]> recent = ThreadLocal.withInitial(() -> new Object[128]);

    private Holder<Biome> cached(final int quartX, final int quartZ) {
        Object[] slots = recent.get();
        long key = (long) quartX << 32 | quartZ & 0xffffffffL;
        int at = ((quartX & 7) << 3 | quartZ & 7) << 1;
        if (slots[at] instanceof Long k && k == key) {
            @SuppressWarnings("unchecked") Holder<Biome> hit = (Holder<Biome>) slots[at + 1];
            return hit;
        }
        Holder<Biome> biome = biomeAt(QuartPos.toBlock(quartX) + 2, QuartPos.toBlock(quartZ) + 2);
        slots[at] = key;
        slots[at + 1] = biome;
        return biome;
    }

    private Holder<Biome> biomeAt(final int blockX, final int blockZ) {
        if (!classic) return pick(kind(blockX, blockZ), blockX, blockZ);
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

    /** One of a kind's biomes: each patch of about patchBlocks (around scattered points, so the edges are uneven) gets one. */
    private Holder<Biome> pick(final String kind, final int blockX, final int blockZ) {
        List<Holder<Biome>> choice = kinds.get(kindIndex.getOrDefault(kind, kindIndex.get("grassland")));
        if (choice.size() == 1) return choice.get(0);
        int cx = Math.floorDiv(blockX, patch), cz = Math.floorDiv(blockZ, patch);
        long best = Long.MAX_VALUE;
        int winner = 0;
        for (int dz = -1; dz <= 1; dz++)
            for (int dx = -1; dx <= 1; dx++) {
                int h = hash(cx + dx, cz + dz);
                long px = (long) (cx + dx) * patch + (h & 0x3ff) * patch / 1024, pz = (long) (cz + dz) * patch + (h >>> 10 & 0x3ff) * patch / 1024;
                long d = (px - blockX) * (px - blockX) + (pz - blockZ) * (pz - blockZ);
                if (d < best) { best = d; winner = h; }
            }
        return choice.get(Math.floorMod(winner >>> 20, choice.size()));
    }

    private static int hash(final int x, final int z) {
        int h = x * 0x27D4EB2D ^ z * 0x165667B1 ^ 0x5BD1E995;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        h *= 0xC2B2AE35;
        return h ^ h >>> 16;
    }

    /** What kind of real place a column is (a key of biomes.json). */
    private static String kind(final int blockX, final int blockZ) {
        Terrain.Column column = Terrain.column(blockX, blockZ);
        float metres = column.metres();
        int cover = column.cover();
        EarthConfig config = EarthConfig.get();
        double signedLatitude = config.latitude(blockZ), latitude = Math.abs(signedLatitude);
        if (Terrain.isWater(cover, metres)) {
            if (column.waterLevel() != config.seaLevel) return latitude > 60 ? "frozen_river" : latitude < 23.5 ? "warm_river" : "river";
            boolean deep = metres < -1000;
            if (latitude < 23.5) return deep ? "deep_warm_ocean" : "warm_ocean";
            if (latitude < 40) return deep ? "deep_lukewarm_ocean" : "lukewarm_ocean";
            if (latitude < 55) return deep ? "deep_ocean" : "ocean";
            if (latitude < 66) return deep ? "deep_cold_ocean" : "cold_ocean";
            return deep ? "deep_frozen_ocean" : "frozen_ocean";
        }
        int climate = RealClimate.at(signedLatitude, config.longitude(blockX));
        char group = RealClimate.group(climate), summer = RealClimate.summer(climate);
        // Roughly where permanent snow and the last trees are found: lower towards the poles.
        double snowLine = Math.max(0, 5000 - 48 * latitude), treeLine = snowLine - 900;
        boolean hotArid = climate == RealClimate.BWH || climate == RealClimate.BSH;
        boolean colder = group == 'E' || group == 'D' && (summer == 'c' || summer == 'v');

        if (cover == LandCover.MANGROVES) return "mangroves";
        if (cover == LandCover.SNOW_AND_ICE || climate == RealClimate.EF) return metres > snowLine - 600 && metres > 1200 ? "snowy_peaks" : "ice_sheet";
        if (metres > snowLine) return "snowy_peaks";
        if (cover == LandCover.WETLAND) {
            if (group == 'A') return "tropical_wetland";
            if (colder || group == 'D') return "bog";
            return summer == 'h' && group == 'C' ? "subtropical_wetland" : "marsh";
        }
        if (cover == LandCover.BARE) {
            if (metres > treeLine) return "rocky_peaks";
            if (group == 'B') return metres > 1200 ? "arid_highlands" : climate == RealClimate.BWK || climate == RealClimate.BSK ? "cold_desert" : "hot_desert";
            return group == 'E' ? "tundra" : "barren_rock";
        }
        if (cover == LandCover.MOSS_AND_LICHEN) return "tundra";
        if (cover == LandCover.TREES) {
            if (group == 'A') return metres > 1200 ? "cloud_forest" : climate == RealClimate.AW ? "tropical_dry_forest" : "tropical_rainforest";
            if (metres > treeLine - 900 && group != 'B') return "mountain_forest";
            if (group == 'B') return hotArid ? "tropical_dry_forest" : "mountain_forest";
            if (group == 'E') return "boreal_forest";
            if (group == 'C') {
                if (RealClimate.mediterranean(climate)) return "mediterranean_forest";
                return summer == 'h' ? "subtropical_forest" : summer == 'c' ? "temperate_rainforest" : "temperate_forest";
            }
            return summer == 'v' ? "snowy_boreal_forest" : summer == 'c' ? "boreal_forest" : "mixed_forest";
        }
        if (cover == LandCover.SHRUBS || cover == LandCover.GRASS) {
            boolean shrubs = cover == LandCover.SHRUBS;
            if (metres > treeLine - (shrubs ? 0 : 300)) return group == 'E' || colder ? "tundra" : "alpine_meadow";
            if (group == 'A') return "savanna";
            if (group == 'B') {
                // High desert (the Colorado Plateau, Iran, the Altiplano): canyons and mesas.
                if ((climate == RealClimate.BWH || climate == RealClimate.BWK) && shrubs && metres > 1200) return "arid_highlands";
                if (climate == RealClimate.BWH || climate == RealClimate.BWK) return "desert_shrubland";
                return hotArid ? (shrubs ? "hot_shrubland" : "hot_steppe") : (shrubs ? "cold_shrubland" : "cold_steppe");
            }
            if (group == 'E') return "tundra";
            if (group == 'C' && RealClimate.mediterranean(climate)) return "mediterranean_shrubland";
            if (colder) return shrubs ? "cold_shrubland" : "cold_grassland";
            return shrubs ? "temperate_shrubland" : "grassland";
        }
        if (cover == LandCover.CROPS) return "farmland";
        if (cover == LandCover.BUILT_UP) return "town";
        // No land cover map here: go by climate and height alone.
        if (metres > treeLine + 400) return "rocky_peaks";
        if (metres > treeLine) return "alpine_meadow";
        return switch (group) {
            case 'A' -> climate == RealClimate.AW ? "savanna" : "tropical_rainforest";
            case 'B' -> climate == RealClimate.BWH ? "hot_desert" : climate == RealClimate.BWK ? "cold_desert" : hotArid ? "hot_steppe" : "cold_steppe";
            case 'C' -> RealClimate.mediterranean(climate) ? "mediterranean_shrubland" : "temperate_forest";
            case 'D' -> colder ? "boreal_forest" : "mixed_forest";
            default -> "tundra";
        };
    }
}
