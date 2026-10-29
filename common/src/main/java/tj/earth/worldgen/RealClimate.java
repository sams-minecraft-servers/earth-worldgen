package tj.earth.worldgen;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

/**
 * The real climate of each place: its Köppen-Geiger class, from Beck et al. (2023), "High-resolution
 * (1 km) Köppen-Geiger maps for 1901–2099 based on constrained CMIP6 projections", Scientific Data
 * 10, 724 (CC BY 4.0), 1991-2020 map, packed to a 0.05 degree grid by tools/PackClimate.java.
 *
 * The classes are numbered as in the map's legend: 1 Af, 2 Am, 3 Aw, 4 BWh, 5 BWk, 6 BSh, 7 BSk,
 * 8-10 Csa/Csb/Csc, 11-13 Cwa/Cwb/Cwc, 14-16 Cfa/Cfb/Cfc, 17-20 Dsa-Dsd, 21-24 Dwa-Dwd,
 * 25-28 Dfa-Dfd, 29 ET, 30 EF; 0 is sea.
 */
public final class RealClimate {
    public static final int AF = 1, AM = 2, AW = 3, BWH = 4, BWK = 5, BSH = 6, BSK = 7, ET = 29, EF = 30;

    private static volatile byte[] grid;
    private static int width, height;

    private RealClimate() {}

    /** The climate class at a place on land (never 0: a square the map has as sea takes its nearest land's). */
    public static int at(final double latitude, final double longitude) {
        byte[] g = grid();
        if (g.length == 0) return guess(latitude);
        // The borders between climates are moved about a little, so they don't run in straight lines along the grid.
        double cell = 180.0 / height;
        double lat = latitude + cell * 0.9 * wobble(latitude, longitude, 0x51ED27);
        double lon = longitude + cell * 0.9 * wobble(latitude, longitude, 0x2C1B3C);
        int row = clamp((int) Math.floor((90 - lat) / cell), height), column = Math.floorMod((int) Math.floor((lon + 180) / cell), width);
        int found = g[row * width + column];
        if (found != 0) return found;
        for (int r = 1; r <= 3; r++)
            for (int dy = -r; dy <= r; dy++)
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int c = g[clamp(row + dy, height) * width + Math.floorMod(column + dx, width)];
                    if (c != 0) return c;
                }
        return guess(latitude);
    }

    /** The main group: 'A' tropical, 'B' arid, 'C' temperate, 'D' cold, 'E' polar. */
    public static char group(final int climate) {
        if (climate <= 3) return 'A';
        if (climate <= 7) return 'B';
        if (climate <= 16) return 'C';
        if (climate <= 28) return 'D';
        return 'E';
    }

    /** Dry in summer: the Mediterranean climates (Cs). */
    public static boolean mediterranean(final int climate) {
        return climate >= 8 && climate <= 10;
    }

    /** Summers: 'h' hot (a), 'w' warm (b), 'c' cold (c), 'v' very cold winters (d), for the C and D climates. */
    public static char summer(final int climate) {
        if (climate >= 8 && climate <= 16) return "hwc".charAt((climate - 8) % 3);
        if (climate >= 17 && climate <= 28) return "hwcv".charAt((climate - 17) % 4);
        return '-';
    }

    /** Without the map (or far out at sea): a rough guess from the latitude. */
    private static int guess(final double latitude) {
        double l = Math.abs(latitude);
        return l < 15 ? AM : l < 35 ? 14 : l < 55 ? 15 : l < 66 ? 27 : l < 75 ? ET : EF;
    }

    private static int clamp(final int i, final int size) {
        return Math.max(0, Math.min(size - 1, i));
    }

    /** Smooth noise between -1 and 1 over a grid of half a map square. */
    private static double wobble(final double latitude, final double longitude, final int seed) {
        double step = 90.0 / height;
        double fx = (longitude + 180) / step, fy = (90 - latitude) / step;
        int x = (int) Math.floor(fx), y = (int) Math.floor(fy);
        double tx = fx - x, ty = fy - y;
        tx = tx * tx * (3 - 2 * tx);
        ty = ty * ty * (3 - 2 * ty);
        double a = corner(x, y, seed), b = corner(x + 1, y, seed), c = corner(x, y + 1, seed), d = corner(x + 1, y + 1, seed);
        return (a + (b - a) * tx) + ((c + (d - c) * tx) - (a + (b - a) * tx)) * ty;
    }

    private static double corner(final int x, final int y, final int seed) {
        int h = x * 0x27D4EB2D ^ y * 0x165667B1 ^ seed;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        return (h & 0xffff) / 32767.5 - 1;
    }

    private static byte[] grid() {
        byte[] g = grid;
        if (g == null) {
            synchronized (RealClimate.class) {
                g = grid;
                if (g == null) grid = g = load();
            }
        }
        return g;
    }

    private static byte[] load() {
        try (InputStream raw = RealClimate.class.getResourceAsStream("/earth_worldgen/climate.bin.gz")) {
            if (raw == null) throw new IOException("climate.bin.gz is missing from the jar");
            DataInputStream in = new DataInputStream(new GZIPInputStream(raw, 1 << 16));
            byte[] magic = new byte[4];
            in.readFully(magic);
            if (!"EKG1".equals(new String(magic, java.nio.charset.StandardCharsets.US_ASCII))) throw new IOException("not a climate file");
            int w = in.readInt(), h = in.readInt();
            if (w <= 0 || h <= 0 || (long) w * h > 100_000_000L) throw new IOException("climate grid of a strange size: " + w + " x " + h);
            byte[] g = new byte[w * h];
            in.readFully(g);
            width = w;
            height = h;
            return g;
        } catch (IOException e) {
            EarthWorldgen.LOG.error("Earth: the climate map could not be read, so biomes go by latitude: {}", e.toString());
            width = 1;
            height = 1;
            return new byte[0];
        }
    }
}
