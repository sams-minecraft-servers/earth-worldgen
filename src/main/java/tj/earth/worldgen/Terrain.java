package tj.earth.worldgen;

import net.minecraft.world.level.dimension.DimensionType;

/** Where the surface of the world is, and what kind of place it is, for one column of blocks. */
public final class Terrain {
    /** A level no block is below: "there is no water in this column". */
    public static final int DRY = DimensionType.MIN_Y * 2;

    private static final float SEA_MARGIN_METRES = 5f;
    /**
     * Water that reads higher than that is still the sea when the sea is this near: up to
     * NEAR_SEA_METRES high within 64 blocks of it, up to FAR_SEA_METRES within 224.
     */
    private static final int[] SEA_REACH = {24, 64, 128, 224};
    private static final float NEAR_SEA_METRES = 60f, FAR_SEA_METRES = 25f;

    /** Everything worked out about one column. The game asks about the same column thousands of times while it builds a chunk. */
    public record Column(int x, int z, float metres, int cover, float surfaceY, int waterLevel) {
        /** The Y of the first block above the ground. */
        public int top() {
            return (int) Math.ceil(surfaceY);
        }
    }

    // Columns already worked out, shared by every thread. A slot is simply overwritten when another
    // column lands on it; a Column never changes once made, so no locking is needed.
    private static final int REMEMBERED = 1 << 16;
    private static final Column[] COLUMNS = new Column[REMEMBERED];

    private Terrain() {}

    public static Column column(int blockX, int blockZ) {
        int slot = (blockX * 0x9E3779B1 + blockZ * 0x85EBCA6B) >>> 16;
        Column column = COLUMNS[slot];
        if (column == null || column.x != blockX || column.z != blockZ) {
            float metres = metres(blockX, blockZ);
            int cover = cover(blockX, blockZ);
            boolean sea = isSea(blockX, blockZ, cover, metres);
            column = new Column(blockX, blockZ, metres, cover, surfaceY(blockX, blockZ, metres, cover, sea), waterLevel(blockX, blockZ, metres, cover, sea));
            COLUMNS[slot] = column;
        }
        return column;
    }

    /** Real metres above sea level at a column; NaN where nothing is known. */
    public static float metres(int blockX, int blockZ) {
        EarthConfig c = EarthConfig.get();
        return Elevation.metres(c.latitude(blockZ), c.longitude(blockX));
    }

    /** What covers the ground at a column: one of the LandCover constants. */
    public static int cover(int blockX, int blockZ) {
        EarthConfig c = EarthConfig.get();
        return LandCover.at(c.latitude(blockZ), c.longitude(blockX));
    }

    /** Water as the maps show it. Where there is no land cover map, whatever is not above sea level. */
    public static boolean isWater(int cover, float metres) {
        return cover == LandCover.WATER || (cover == LandCover.UNKNOWN && !(metres > 0));
    }

    /** Sea, as opposed to a lake or river: water whose surface is at sea level. */
    public static boolean isSea(int blockX, int blockZ, int cover, float metres) {
        if (!isWater(cover, metres)) return false;
        // The elevation data is rough along a waterfront (it sees ships, cranes, quays and the cliffs
        // above), so water within a few metres of sea level is the sea, and so is water that reads
        // higher but has the sea close by: otherwise a harbour is dotted with raised blobs of water.
        if (!(metres > SEA_MARGIN_METRES)) return true;
        if (metres > NEAR_SEA_METRES) return false;
        for (int r : SEA_REACH) {
            if (r > 64 && metres > FAR_SEA_METRES) return false;
            for (int dx = -r; dx <= r; dx += r) {
                for (int dz = -r; dz <= r; dz += r) {
                    if (dx == 0 && dz == 0) continue;
                    int there = cover(blockX + dx, blockZ + dz);
                    if (there != LandCover.WATER && there != LandCover.UNKNOWN) continue;
                    if (!(metres(blockX + dx, blockZ + dz) > (there == LandCover.WATER ? SEA_MARGIN_METRES : 0))) return true;
                }
            }
        }
        return false;
    }

    /**
     * Blocks below this Y are water in a column that has any (the sea, a lake, a river); DRY otherwise.
     * A lake's water stands at the height the elevation data gives its surface.
     */
    private static int waterLevel(int blockX, int blockZ, float metres, int cover, boolean sea) {
        EarthConfig c = EarthConfig.get();
        if (!isWater(cover, metres)) return DRY;
        return sea ? c.seaLevel : landY(c, stillWaterMetres(blockX, blockZ, metres));
    }

    /**
     * The height of a lake or river's surface. Water lies flat but the elevation data over it does
     * not quite, so the surface is taken as the lowest reading on the water round about.
     */
    private static float stillWaterMetres(int blockX, int blockZ, float metres) {
        float lowest = metres;
        for (int r = 32; r <= 96; r += 64) {
            for (int dx = -r; dx <= r; dx += r) {
                for (int dz = -r; dz <= r; dz += r) {
                    if ((dx == 0 && dz == 0) || cover(blockX + dx, blockZ + dz) != LandCover.WATER) continue;
                    float m = metres(blockX + dx, blockZ + dz);
                    if (m > SEA_MARGIN_METRES && m < lowest) lowest = m;
                }
            }
        }
        return lowest;
    }

    /** The Y just above the top solid block of a column: blocks below it are ground. */
    private static float surfaceY(int blockX, int blockZ, float metres, int cover, boolean sea) {
        EarthConfig c = EarthConfig.get();
        if (!isWater(cover, metres)) {
            // Land, which may lie below sea level (polders, the Dead Sea shore) and stays dry there.
            // Where the maps disagree along a coast (land here, but a sea depth for its height) the land wins
            // and stands just clear of the water. Real land far below sea level (the Dead Sea shore) is
            // flattened by this too: a known limit until such places are listed.
            if (Float.isNaN(metres) || (cover != LandCover.UNKNOWN && metres < 0)) return shore(blockX, blockZ, c.seaLevel + 1);
            return shore(blockX, blockZ, c.seaLevel + 1 + (float) (metres / c.metresPerBlockY));
        }
        float floor = DimensionType.MIN_Y + 8;
        if (sea) {
            if (Float.isNaN(metres)) return c.seaLevel - c.unknownSeaDepth;
            // The seabed where its depth is known; near the shore, where the data says "0", it shelves away from the land.
            float bed = c.seaLevel - 1 + (float) (metres / c.metresPerBlockY);
            return Math.max(floor, Math.min(bed, c.seaLevel - inlandDepth(blockX, blockZ)));
        }
        return Math.max(floor, landY(c, stillWaterMetres(blockX, blockZ, metres)) - inlandDepth(blockX, blockZ));
    }

    /**
     * Land comes down to the water beside it: the block at the water's edge is level with the
     * water, the one behind it a block higher, and from there the ground is as measured. (Measured
     * ground is never less than a block above the sea, which left every shore a step.)
     */
    private static float shore(int blockX, int blockZ, float surfaceY) {
        int level = waterBeside(blockX, blockZ, 1);
        if (level != DRY) return Math.max(level, surfaceY - 2);
        level = waterBeside(blockX, blockZ, 2);
        return level != DRY ? Math.max(level, surfaceY - 1) : surfaceY;
    }

    /** The level of the water one or two blocks from a column of land (the highest, if there are several); DRY if there is none. */
    private static int waterBeside(int blockX, int blockZ, int away) {
        int level = DRY;
        for (int dx = -away; dx <= away; dx++) {
            int dz = away - Math.abs(dx);
            for (int side = 0; side < (dz == 0 ? 1 : 2); side++, dz = -dz) {
                if (isWet(blockX + dx, blockZ + dz)) level = Math.max(level, column(blockX + dx, blockZ + dz).waterLevel());
            }
        }
        return level;
    }

    private static int landY(EarthConfig c, float metres) {
        return (int) Math.floor(c.seaLevel + 1 + metres / c.metresPerBlockY);
    }

    /**
     * How deep to make water whose depth nobody measured (lakes, rivers, the shallows): deeper the
     * further it is from a bank. Judged by how many of the points around it are water too.
     */
    private static int inlandDepth(int blockX, int blockZ) {
        int wet = 0;
        for (int ring = 0; ring < 3; ring++) {
            int r = 4 << (ring * 2); // 4, 16 and 64 blocks away
            if (isWet(blockX + r, blockZ)) wet++;
            if (isWet(blockX - r, blockZ)) wet++;
            if (isWet(blockX, blockZ + r)) wet++;
            if (isWet(blockX, blockZ - r)) wet++;
        }
        return 1 + wet; // 1 at the very edge, 13 in open water
    }

    private static boolean isWet(int blockX, int blockZ) {
        int cover = cover(blockX, blockZ);
        return cover == LandCover.WATER || (cover == LandCover.UNKNOWN && !(metres(blockX, blockZ) > 0));
    }
}
