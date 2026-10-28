package tj.earth.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.dimension.DimensionType;

/**
 * Animals where they really live. The game picks animals by biome, and the Earth's biomes are
 * few and broad (any forest, any snow), so pandas would turn up in every jungle and polar bears on
 * every glacier. Here each animal listed below is added to the spawn list where its real range is
 * and, if it is "only there", taken off the list everywhere else. The game's own rules about the
 * ground an animal stands on still apply (camels on sand, goats on rock, turtles on beaches).
 *
 * Used for the animals put in a chunk when it is first made and for those that turn up later.
 * Animals not listed (cows, sheep, pigs, chickens, rabbits, squid...) are left as the game has them.
 * Axolotls only live in Mexico City's lakes and canals and the game's rule for them (clay in lush
 * caves) never holds there, so they are put straight into that water as it is made.
 */
public final class Fauna {
    /** What decides a place: latitude and longitude in degrees, real metres above sea level, LandCover. */
    @FunctionalInterface
    interface Range {
        boolean has(double lat, double lon, float metres, int cover);
    }

    /** One animal: where it lives, whether it is found nowhere else, how often and in what numbers. */
    record Kind(EntityType<?> type, boolean onlyThere, int weight, int fewest, int most, Range range) {
        MobSpawnSettings.SpawnerData data() {
            return new MobSpawnSettings.SpawnerData(type, UniformInt.of(fewest, most));
        }
    }

    private static boolean box(double lat, double lon, double south, double north, double west, double east) {
        return lat >= south && lat <= north && lon >= west && lon <= east;
    }

    private static boolean green(int cover) {
        return cover == LandCover.GRASS || cover == LandCover.SHRUBS || cover == LandCover.CROPS || cover == LandCover.UNKNOWN;
    }

    private static boolean dry(int cover) {
        return cover == LandCover.BARE || cover == LandCover.SHRUBS || cover == LandCover.GRASS || cover == LandCover.UNKNOWN;
    }

    static final List<Kind> KINDS = List.of(
        // The bamboo forests of Sichuan, Shaanxi and Gansu.
        new Kind(EntityTypes.PANDA, true, 10, 1, 2, (lat, lon, m, c) -> box(lat, lon, 26, 35, 101, 111) && c == LandCover.TREES),
        // The Arctic, and Hudson Bay's shores, which they reach further south.
        new Kind(EntityTypes.POLAR_BEAR, true, 6, 1, 2, (lat, lon, m, c) -> lat >= 66 || box(lat, lon, 51, 66, -95, -76)),
        // The Sahara, Arabia, Iran and the Thar; the Taklamakan and Gobi (Bactrian camels); feral camels in inland Australia.
        new Kind(EntityTypes.CAMEL, true, 8, 1, 3, (lat, lon, m, c) -> dry(c)
            && (box(lat, lon, 12, 35, -17, 75) || box(lat, lon, 36, 48, 75, 115) || box(lat, lon, -32, -18, 115, 142))),
        // The Andes, high up (llamas and alpacas, with their wild cousins).
        new Kind(EntityTypes.LLAMA, true, 10, 2, 4, (lat, lon, m, c) -> box(lat, lon, -45, 0, -80, -62) && m >= 2000),
        // Tropical forest everywhere, and Australia's cockatoos and parrots.
        new Kind(EntityTypes.PARROT, true, 10, 1, 2, (lat, lon, m, c) -> c == LandCover.TREES
            && (Math.abs(lat) <= 23.5 || box(lat, lon, -40, -10, 112, 155))),
        // Forests from Texas to northern Argentina.
        new Kind(EntityTypes.OCELOT, true, 4, 1, 3, (lat, lon, m, c) -> c == LandCover.TREES && box(lat, lon, -35, 30, -118, -34)),
        // Scrub and grassland from Kansas to Patagonia.
        new Kind(EntityTypes.ARMADILLO, true, 8, 1, 2, (lat, lon, m, c) -> dry(c) && box(lat, lon, -45, 40, -120, -34)),
        // Across the northern half of the world.
        new Kind(EntityTypes.WOLF, true, 6, 2, 4, (lat, lon, m, c) -> lat >= 25 && (c == LandCover.TREES || c == LandCover.SNOW_AND_ICE)),
        // Across the northern half of the world, and (brought by settlers) Australia.
        new Kind(EntityTypes.FOX, true, 8, 2, 4, (lat, lon, m, c) -> (lat >= 25 || box(lat, lon, -44, -10, 112, 155))
            && (c == LandCover.TREES || c == LandCover.SHRUBS || c == LandCover.GRASS)),
        // High mountains outside the Andes: ibex in the Alps, tahr in the Himalaya, mountain goats in the Rockies.
        new Kind(EntityTypes.GOAT, true, 10, 1, 3, (lat, lon, m, c) -> m >= 1800 && !box(lat, lon, -56, 12, -82, -60)),
        // Wild and feral herds: the steppe from the Black Sea to Mongolia, the American West, Australia's brumbies.
        new Kind(EntityTypes.HORSE, false, 6, 2, 6, (lat, lon, m, c) -> green(c)
            && (box(lat, lon, 40, 55, 30, 125) || box(lat, lon, 30, 50, -118, -96) || box(lat, lon, -38, -20, 135, 153))),
        // Northern Africa and the Middle East.
        new Kind(EntityTypes.DONKEY, false, 4, 1, 3, (lat, lon, m, c) -> dry(c) && box(lat, lon, 5, 40, -17, 75)),
        // Warm beaches.
        new Kind(EntityTypes.TURTLE, true, 5, 2, 5, (lat, lon, m, c) -> Math.abs(lat) <= 35),
        // Warm and temperate seas.
        new Kind(EntityTypes.DOLPHIN, true, 2, 1, 2, (lat, lon, m, c) -> Math.abs(lat) <= 55),
        // Cold and temperate seas.
        new Kind(EntityTypes.COD, true, 15, 3, 6, (lat, lon, m, c) -> Math.abs(lat) >= 30),
        // The North Atlantic and North Pacific, and the rivers that run into them.
        new Kind(EntityTypes.SALMON, true, 15, 1, 5, (lat, lon, m, c) -> lat >= 40),
        // Warm seas.
        new Kind(EntityTypes.TROPICAL_FISH, true, 25, 8, 8, (lat, lon, m, c) -> Math.abs(lat) <= 30),
        new Kind(EntityTypes.PUFFERFISH, true, 15, 1, 3, (lat, lon, m, c) -> Math.abs(lat) <= 30),
        // The Indian Ocean and the western Pacific.
        new Kind(EntityTypes.NAUTILUS, true, 2, 1, 1, (lat, lon, m, c) -> Math.abs(lat) <= 30 && (lon >= 40 || lon <= -150))
    );

    private static final MobSpawnSettings.SpawnerData[] DATA = KINDS.stream().map(Kind::data).toArray(MobSpawnSettings.SpawnerData[]::new);
    private static final Map<Key, WeightedList<MobSpawnSettings.SpawnerData>> ADJUSTED = new ConcurrentHashMap<>();

    private record Key(WeightedList<MobSpawnSettings.SpawnerData> list, MobCategory category, int here) {}

    private Fauna() {}

    /** Which of KINDS live at a column, one bit each. */
    static int here(final int blockX, final int blockZ) {
        EarthConfig config = EarthConfig.get();
        double lat = config.latitude(blockZ), lon = config.longitude(blockX);
        Terrain.Column column = Terrain.column(blockX, blockZ);
        float metres = Float.isNaN(column.metres()) ? 0 : column.metres();
        int bits = 0;
        for (int i = 0; i < KINDS.size(); i++) {
            if (KINDS.get(i).range().has(lat, lon, metres, column.cover())) bits |= 1 << i;
        }
        return bits;
    }

    /** The game's spawn list for a place, with the animals that live there added and those that only live elsewhere taken off. */
    public static WeightedList<MobSpawnSettings.SpawnerData> adjust(final WeightedList<MobSpawnSettings.SpawnerData> list, final MobCategory category,
                                                                   final int blockX, final int blockZ) {
        if (!EarthConfig.get().animals.realRanges) return list;
        return ADJUSTED.computeIfAbsent(new Key(list, category, here(blockX, blockZ)), Fauna::build);
    }

    private static WeightedList<MobSpawnSettings.SpawnerData> build(final Key key) {
        List<Weighted<MobSpawnSettings.SpawnerData>> items = new ArrayList<>();
        for (Weighted<MobSpawnSettings.SpawnerData> item : key.list().unwrap()) {
            int kind = indexOf(item.value().type());
            if (kind < 0 || (key.here() & 1 << kind) != 0 || !KINDS.get(kind).onlyThere()) items.add(item);
        }
        for (int i = 0; i < KINDS.size(); i++) {
            Kind kind = KINDS.get(i);
            if ((key.here() & 1 << i) == 0 || kind.type().getCategory() != key.category()) continue;
            if (items.stream().noneMatch(item -> item.value().type() == kind.type())) items.add(new Weighted<>(DATA[i], kind.weight()));
        }
        return WeightedList.of(items);
    }

    private static int indexOf(final EntityType<?> type) {
        for (int i = 0; i < KINDS.size(); i++) {
            if (KINDS.get(i).type() == type) return i;
        }
        return -1;
    }

    /** Axolotls in the lakes and canals of the Valley of Mexico (Xochimilco, Chalco), as a chunk is first made. */
    public static void settleAxolotls(final WorldGenRegion region) {
        if (!EarthConfig.get().animals.axolotls) return;
        if (region.getHeight() != DimensionType.Y_SIZE) return;
        ChunkPos chunk = region.getCenter();
        EarthConfig config = EarthConfig.get();
        int x = chunk.getMiddleBlockX(), z = chunk.getMiddleBlockZ();
        if (!box(config.latitude(z), config.longitude(x), 19.1, 19.6, -99.3, -98.8)) return;
        RandomSource random = RandomSource.create(region.getSeed() ^ chunk.pack() * 0xC2B2AE3D27D4EB4FL);
        if (random.nextFloat() > 0.3f) return;
        for (int n = 1 + random.nextInt(2); n > 0; n--) {
            int bx = chunk.getMinBlockX() + random.nextInt(16), bz = chunk.getMinBlockZ() + random.nextInt(16);
            Terrain.Column column = Terrain.column(bx, bz);
            if (column.waterLevel() == Terrain.DRY || column.waterLevel() == config.seaLevel || column.waterLevel() - column.top() < 1) continue;
            BlockPos pos = new BlockPos(bx, column.top(), bz);
            if (!region.getFluidState(pos).isSource()) continue;
            Mob axolotl = EntityTypes.AXOLOTL.create(region.getLevel(), EntitySpawnReason.CHUNK_GENERATION);
            if (axolotl == null) continue;
            axolotl.snapTo(bx + 0.5, pos.getY(), bz + 0.5, random.nextFloat() * 360f, 0f);
            axolotl.finalizeSpawn(region, region.getCurrentDifficultyAt(pos), EntitySpawnReason.CHUNK_GENERATION, null);
            axolotl.setPersistenceRequired();
            region.addFreshEntityWithPassengers(axolotl);
        }
    }
}
