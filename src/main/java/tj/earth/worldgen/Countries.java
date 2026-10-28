package tj.earth.worldgen;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Which country a place is in, from Natural Earth's borders (public domain) as packed by
 * tools/pack_countries.py into the mod's own countries.bin.gz. Read once, the first time it is asked.
 * Places at sea, and on land no country claims, are in no country ("").
 */
public final class Countries {
    private Countries() {}

    private static final int CELL = 1_000_000;                 // the index is in one-degree cells
    private static volatile Countries.Data data;

    private record Polygon(int country, int west, int south, int east, int north, int[][] rings) {}

    private record Data(String[] codes, String[] names, List<Polygon>[] cells) {}

    /** The two-letter code of the country at a point ("" for none). */
    public static String at(final double latitude, final double longitude) {
        Data d = load();
        if (d == null) return "";
        int x = (int) Math.round(longitude * 1e6), y = (int) Math.round(latitude * 1e6);
        List<Polygon> cell = d.cells[cellOf(x, y)];
        if (cell == null) return "";
        for (Polygon p : cell) {
            if (x < p.west || x > p.east || y < p.south || y > p.north) continue;
            boolean inside = false;
            for (int[] ring : p.rings) inside ^= contains(ring, x, y);
            if (inside) return d.codes[p.country];
        }
        return "";
    }

    /** The country at a block, on this world's map. */
    public static String atBlock(final int blockX, final int blockZ) {
        EarthConfig c = EarthConfig.get();
        return at(c.latitude(blockZ), c.longitude(blockX));
    }

    /** The English name for a two-letter code, or the code itself. */
    public static String name(final String code) {
        Data d = load();
        if (d != null) for (int i = 0; i < d.codes.length; i++) if (d.codes[i].equals(code)) return d.names[i];
        return code;
    }

    private static boolean contains(final int[] ring, final int x, final int y) {
        boolean inside = false;
        for (int i = 0, j = ring.length - 2; i < ring.length; j = i, i += 2) {
            long xi = ring[i], yi = ring[i + 1], xj = ring[j], yj = ring[j + 1];
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (double) (yj - yi) + xi) inside = !inside;
        }
        return inside;
    }

    private static int cellOf(final int x, final int y) {
        int cx = Math.floorDiv(x, CELL) + 180, cy = Math.floorDiv(y, CELL) + 90;
        return Math.clamp(cy, 0, 180) * 361 + Math.clamp(cx, 0, 360);
    }

    @SuppressWarnings("unchecked")
    private static Data load() {
        Data d = data;
        if (d != null) return d;
        synchronized (Countries.class) {
            if (data != null) return data;
            try (InputStream raw = Countries.class.getResourceAsStream("/earth_worldgen/countries.bin.gz")) {
                if (raw == null) throw new IOException("countries.bin.gz is not in the mod");
                DataInputStream in = new DataInputStream(new GZIPInputStream(raw, 1 << 16));
                if (in.readInt() != 0x45435459 || in.readInt() != 1) throw new IOException("not a version 1 countries file");
                int count = in.readInt();
                String[] codes = new String[count], names = new String[count];
                for (int i = 0; i < count; i++) { codes[i] = in.readUTF(); names[i] = in.readUTF(); }
                List<Polygon>[] cells = new List[181 * 361];
                int polygons = in.readInt();
                for (int n = 0; n < polygons; n++) {
                    int country = in.readShort(), west = in.readInt(), south = in.readInt(), east = in.readInt(), north = in.readInt();
                    int[][] rings = new int[in.readInt()][];
                    for (int r = 0; r < rings.length; r++) {
                        int[] ring = new int[in.readInt() * 2];
                        for (int i = 0; i < ring.length; i++) ring[i] = in.readInt();
                        rings[r] = ring;
                    }
                    Polygon p = new Polygon(country, west, south, east, north, rings);
                    for (int cy = Math.floorDiv(south, CELL); cy <= Math.floorDiv(north, CELL); cy++) {
                        for (int cx = Math.floorDiv(west, CELL); cx <= Math.floorDiv(east, CELL); cx++) {
                            int cell = cellOf(cx * CELL, cy * CELL);
                            if (cells[cell] == null) cells[cell] = new ArrayList<>(2);
                            cells[cell].add(p);
                        }
                    }
                }
                data = new Data(codes, names, cells);
                EarthWorldgen.LOG.info("Earth: borders of {} countries read", count);
            } catch (IOException e) {
                EarthWorldgen.LOG.error("Earth: country borders could not be read ({}); every place counts as in no country", e.getMessage());
                data = new Data(new String[0], new String[0], new List[181 * 361]);
            }
            return data;
        }
    }
}
