package tj.earth.worldgen;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * How the real Earth is laid onto the world. Read once from config/earth-mod/earth_worldgen.json in the
 * server's folder (see ConfigFile); a missing field means the default below.
 *
 * Changing any of these after a world has been explored leaves a seam between old and new chunks.
 */
public final class EarthConfig {
    /** Folder of packed elevation tiles (N44/N44E008.ehg and so on; see tools/PackElevation.java). */
    public String dataDir = "earth-data/elevation";
    /** Folder of ESA WorldCover land cover files (ESA_WorldCover_10m_2021_v200_N45E006_Map.tif and so on). */
    public String landCoverDir = "earth-data/worldcover";
    /** Folder of packed streets and building outlines (N44/N44E008.urb and so on; see tools/pack_urban.py). Nothing is built where a tile is missing. */
    public String urbanDir = "earth-data/urban";
    /** Real metres across one block on the ground, at the equator: 5 means a 1:5 map. */
    public double horizontalScale = 5.0;
    /**
     * Real metres of height in one block. Less than horizontalScale exaggerates the relief.
     * The world is 4064 blocks tall and the Earth spans 19,784 m from its deepest trench to its
     * highest peak, so nothing under about 4.9 fits; 5 is true scale on a 1:5 map.
     */
    public double metresPerBlockY = 5.0;
    /**
     * Must be the same as sea_level in the datapack's noise settings. At 160, with 5 m per block,
     * Everest (y 1930) and the Challenger Deep (y -2027) are both inside the world.
     */
    public int seaLevel = 160;
    /** How far below sea level the seabed lies where no depth is known, in blocks. */
    public int unknownSeaDepth = 6;
    /** Villagers put in each chunk of built-up land as it is first made (see CityVillagers); 0 for none. */
    public float villagersPerCityChunk = 0.25f;

    /**
     * When a new world is made, put the mod's datapack in its datapacks folder (needed before the
     * world's first start). Nothing is done for a world that already exists.
     */
    public boolean installDatapack = true;
    /** Where players first appear in a new world (the world's spawn), as latitude and longitude. Default: Nairobi. */
    public Place newWorldSpawn = new Place(-1.2864, 36.8172);
    /** Fetching the map data for each area the first time it is needed (see Downloads). */
    public Download download = new Download();

    public static final class Place {
        public double latitude, longitude;

        public Place() {}

        public Place(final double latitude, final double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    public static final class Download {
        /** Download each one-degree elevation tile (about 5 to 25 MB) the first time land over it is made. */
        public boolean elevation = true;
        /** Download each three-degree land cover tile (ESA WorldCover, tens to hundreds of MB) the first time it is needed. */
        public boolean landCover = true;
        /** How many tiles may download at once. */
        public int atOnce = 2;
        /** The most each download may take from the line, in MB per second; 0 for no limit. Worth setting on a machine whose disk other servers share. */
        public double maxMegabytesPerSecond = 0;
    }

    /** The real towns and cities: their streets and buildings (see UrbanBuilder) and their people (see CityVillagers). */
    public Cities cities = new Cities();
    /** Famous buildings and wrecks, built where they really stand (see Monuments). */
    public MonumentSettings monuments = new MonumentSettings();
    /** The game's own structures (villages, strongholds, mineshafts...). */
    public Structures structures = new Structures();
    /** Animals where they really live (see Fauna). */
    public Animals animals = new Animals();

    public static final class Cities {
        /** false: no streets, buildings or city villagers anywhere. */
        public boolean enabled = true;
        /** Building outlines (needs packed streets files, see urbanDir). */
        public boolean buildings = true;
        /** Roads, paths, railways and bridges (needs packed streets files, see urbanDir). */
        public boolean roads = true;
        /** Villagers on built-up land (how many: villagersPerCityChunk). */
        public boolean villagers = true;
        /** Two-letter country codes (ISO 3166-1, e.g. "FR", "US") where no streets, buildings or city villagers are made. */
        public List<String> disabledCountries = List.of();
    }

    public static final class MonumentSettings {
        /** false: no monuments anywhere. */
        public boolean enabled = true;
        /** Monument ids not to build (the ids are listed in docs/Monuments.md), e.g. "eiffel_tower". */
        public List<String> disabled = List.of();
        /** Two-letter country codes whose monuments are not built. */
        public List<String> disabledCountries = List.of();
        /** More monument files of your own, in the mod's format (see tools/landmarks in the docs). */
        public List<String> extraFiles = List.of();
    }

    public static final class Structures {
        /**
         * Structure sets the game does not make in the Earth world, by id: "minecraft:villages",
         * "minecraft:strongholds", "minecraft:mineshafts", "minecraft:pillager_outposts"... ("minecraft:"
         * may be left out). Villages are off by default: real towns take their place. Eyes of ender and
         * /locate do not find a structure that is switched off.
         */
        public List<String> disabled = List.of("minecraft:villages");
    }

    public static final class Animals {
        /** false: animals spawn by biome as in any world, instead of in their real ranges. */
        public boolean realRanges = true;
        /** Axolotls in Mexico City's lakes and canals. */
        public boolean axolotls = true;
    }

    /** Whether a two-letter code is in a list of country codes (any case). */
    public static boolean listed(final List<String> codes, final String code) {
        if (code.isEmpty() || codes.isEmpty()) return false;
        for (String c : codes) if (c.equalsIgnoreCase(code)) return true;
        return false;
    }

    /** A structure set id with "minecraft:" filled in where it was left out, in lower case. */
    public static String fullId(final String id) {
        String s = id.trim().toLowerCase(Locale.ROOT);
        return s.contains(":") ? s : "minecraft:" + s;
    }

    private static final double EQUATOR_METRES = 40_075_016.686;
    private static volatile EarthConfig instance;

    public static EarthConfig get() {
        EarthConfig config = instance;
        return config != null ? config : load();
    }

    private static synchronized EarthConfig load() {
        if (instance == null) {
            instance = ConfigFile.read("earth_worldgen.json", EarthConfig.class, new EarthConfig(), Path.of("config", "earth_worldgen.json"));
            EarthWorldgen.LOG.info("Earth: 1:{} map, {} m of height per block, elevation from {}",
                (long) instance.horizontalScale, instance.metresPerBlockY, Path.of(instance.dataDir).toAbsolutePath());
        }
        return instance;
    }

    /** Blocks in one degree of longitude, and (the map is a plain grid) of latitude. */
    public double blocksPerDegree() {
        return EQUATOR_METRES / horizontalScale / 360.0;
    }

    public double latitude(int blockZ) {
        return -blockZ / blocksPerDegree();
    }

    public double longitude(int blockX) {
        return blockX / blocksPerDegree();
    }
}
