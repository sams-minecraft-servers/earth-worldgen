package tj.earth.worldgen;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.zip.Inflater;

/**
 * The height of the real ground, in metres above sea level, from one-degree elevation tiles as
 * tools/PackElevation.java packs them (N45/N45E006.ehg: a square grid of 16-bit metres, north row
 * first, stored as 256 x 256 squares compressed one by one). A tile is mapped into memory the first
 * time a chunk needs it and only the squares under places being explored are unpacked, so the
 * whole planet's data costs nothing until somebody goes there.
 */
public final class Elevation {
    private static final short VOID = Short.MIN_VALUE;
    private static final int MAGIC = 0x45484754, MAX_SQUARES_IN_MEMORY = 512, MAX_TILES_OPEN = 2048;
    private static final Tile MISSING = new Tile(null, 0, 0, 0, 0, null);
    private static final ConcurrentHashMap<Integer, Tile> TILES = new ConcurrentHashMap<>();
    // The squares unpacked so far, oldest first: when there are too many the oldest is let go.
    private static final ConcurrentLinkedQueue<Unpacked> UNPACKED = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger UNPACKED_COUNT = new AtomicInteger();

    /** One packed file: `size` samples a side, in squares of 1 << shift samples, `across` squares a side; `squares` holds those unpacked. */
    private record Tile(ByteBuffer packed, int key, int size, int shift, int across, AtomicReferenceArray<short[]> squares) {}

    private record Unpacked(Tile tile, int index) {}

    private Elevation() {}

    /** Metres above sea level at a point, or NaN where nothing is known (a gap in the data, a tile not there). */
    public static float metres(double latitude, double longitude) {
        if (latitude >= 90 || latitude <= -90) return Float.NaN;
        longitude = ((longitude + 180) % 360 + 360) % 360 - 180;
        int south = (int) Math.floor(latitude), west = (int) Math.floor(longitude);
        Tile tile = TILES.get(south * 1000 + west);
        if (tile == null) tile = open(south, west);
        if (tile == MISSING) return Float.NaN;

        int last = tile.size - 1, mask = (1 << tile.shift) - 1;
        double column = (longitude - west) * last, row = (south + 1 - latitude) * last;
        int c = Math.min((int) column, last - 1), r = Math.min((int) row, last - 1);
        float fx = (float) (column - c), fy = (float) (row - r);
        // A smooth surface through the samples (Catmull-Rom over the 4 x 4 around the point): at
        // large map scales one sample covers several blocks, and straight lines between them show as facets.
        // The 16 samples are nearly always in one square, so it is looked up once and kept while it serves.
        short[] square = null;
        int held = -1, width = 0;
        float[] rows = new float[4], p = new float[4];
        for (int j = 0; j < 4; j++) {
            int rr = Math.max(0, Math.min(last, r - 1 + j));
            for (int i = 0; i < 4; i++) {
                int cc = Math.max(0, Math.min(last, c - 1 + i));
                int index = (rr >> tile.shift) * tile.across + (cc >> tile.shift);
                if (index != held) {
                    square = tile.squares.get(index);
                    if (square == null) square = unpack(tile, index);
                    held = index;
                    width = Math.min(mask + 1, tile.size - (cc & ~mask));
                }
                short metres = square[(rr & mask) * width + (cc & mask)];
                if (metres == VOID) return Float.NaN;
                p[i] = metres;
            }
            rows[j] = cubic(p[0], p[1], p[2], p[3], fx);
        }
        return cubic(rows[0], rows[1], rows[2], rows[3], fy);
    }

    private static float cubic(float p0, float p1, float p2, float p3, float t) {
        return p1 + 0.5f * t * (p2 - p0 + t * (2 * p0 - 5 * p1 + 4 * p2 - p3 + t * (3 * (p1 - p2) + p3 - p0)));
    }

    /** One square of a tile, unpacked: each value is stored as its difference from the one to its west (see tools/PackElevation.java). */
    private static short[] unpack(Tile tile, int index) {
        int side = 1 << tile.shift;
        int w = Math.min(side, tile.size - index % tile.across * side), h = Math.min(side, tile.size - index / tile.across * side), n = w * h;
        short[] heights = new short[n];
        try {
            int start = tile.packed.getInt(12 + 4 * index), end = tile.packed.getInt(16 + 4 * index);
            byte[] plain = new byte[n * 2];
            Inflater inflater = new Inflater();
            inflater.setInput(tile.packed.slice(start, end - start));
            int got = 0;
            while (got < plain.length && !inflater.finished()) {
                int more = inflater.inflate(plain, got, plain.length - got);
                if (more == 0 && (inflater.needsInput() || inflater.needsDictionary())) break;
                got += more;
            }
            inflater.end();
            if (got != plain.length) throw new IOException("it ends early");
            for (int i = 0; i < n; i++) {
                int before = i % w > 0 ? heights[i - 1] : i >= w ? heights[i - w] : 0;
                heights[i] = (short) (before + (plain[i] << 8 | plain[n + i] & 0xff));
            }
        } catch (Exception e) {
            // A square that cannot be read is treated as a gap in the data, rather than stopping the world.
            EarthWorldgen.LOG.error("Earth: a square of elevation tile {} could not be read: {}", name(Math.floorDiv(tile.key + 500, 1000), tile.key - Math.floorDiv(tile.key + 500, 1000) * 1000), e.toString());
            java.util.Arrays.fill(heights, VOID);
        }
        tile.squares.set(index, heights);
        UNPACKED.add(new Unpacked(tile, index));
        if (UNPACKED_COUNT.incrementAndGet() > MAX_SQUARES_IN_MEMORY) {
            // Anything let go while still in use is simply unpacked again.
            Unpacked oldest = UNPACKED.poll();
            if (oldest != null) {
                oldest.tile.squares.set(oldest.index, null);
                UNPACKED_COUNT.decrementAndGet();
            }
        }
        return heights;
    }

    private static String name(int south, int west) {
        return String.format("%c%02d%c%03d", south < 0 ? 'S' : 'N', Math.abs(south), west < 0 ? 'W' : 'E', Math.abs(west));
    }

    private static Tile open(int south, int west) {
        Tile tile = MISSING;
        String name = name(south, west);
        Path file = Path.of(EarthConfig.get().dataDir, name.substring(0, 3), name + ".ehg");
        if (Files.exists(file)) {
            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
                ByteBuffer packed = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
                int size = packed.getInt(4), side = packed.getInt(8);
                if (packed.getInt(0) != MAGIC || size < 2 || side < 1 || Integer.bitCount(side) != 1) throw new IOException("not a packed elevation tile");
                int across = (size + side - 1) / side;
                if (packed.getInt(12 + 4 * across * across) != channel.size()) throw new IOException("the file is not complete");
                tile = new Tile(packed, south * 1000 + west, size, Integer.numberOfTrailingZeros(side), across, new AtomicReferenceArray<>(across * across));
            } catch (Exception e) {
                EarthWorldgen.LOG.error("Earth: elevation tile {} could not be used: {}", file, e.getMessage());
            }
        }
        if (TILES.size() >= MAX_TILES_OPEN) {
            // Make room by forgetting a quarter of the tiles; one still in use is simply opened again.
            int drop = MAX_TILES_OPEN / 4;
            for (Integer old : TILES.keySet()) {
                if (drop-- <= 0) break;
                TILES.remove(old);
            }
        }
        TILES.put(south * 1000 + west, tile);
        return tile;
    }
}
