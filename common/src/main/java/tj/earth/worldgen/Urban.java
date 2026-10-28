package tj.earth.worldgen;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.Inflater;

/**
 * The real streets and buildings: every building's outline, with its height, floors and kind where
 * they are known, and every road and railway on the surface. Read from the files made by
 * tools/pack_urban.py, one per one-degree tile (N44/N44E008.urb), each sorted into small squares
 * so only the squares under places being explored are ever read. A square holds everything that
 * reaches into it, so whatever stands on a column is found in that column's own square.
 */
public final class Urban {
    /** Kinds of road, widest first; FOOTWAY and TRACK give way to buildings, the others cut through them. */
    public static final int MOTORWAY = 1, TRUNK = 2, PRIMARY = 3, SECONDARY = 4, TERTIARY = 5, STREET = 6, SERVICE = 7,
        PEDESTRIAN = 8, FOOTWAY = 9, TRACK = 10, RAIL = 11;
    /** Kinds of building. */
    public static final int UNKNOWN = 0, RESIDENTIAL = 1, COMMERCIAL = 2, INDUSTRIAL = 3, AGRICULTURAL = 4, CIVIC = 5, EDUCATION = 6,
        ENTERTAINMENT = 7, MEDICAL = 8, MILITARY = 9, OUTBUILDING = 10, RELIGIOUS = 11, SERVICE_BUILDING = 12, TRANSPORTATION = 13;
    /** What is known of a building's roof. */
    public static final int ROOF_UNKNOWN = 0, ROOF_FLAT = 1, ROOF_PITCHED = 2;

    public static final Square EMPTY = new Square(new Building[0], new Road[0]);

    private static final int MAX_SQUARES_IN_MEMORY = 64, NOT_WORKED_OUT = Integer.MIN_VALUE;
    private static final double UNITS_PER_DEGREE = 1_000_000;
    private static final Tile MISSING = new Tile(null, 0, 0, 0, null);
    private static final ConcurrentHashMap<Integer, Tile> TILES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Square> SQUARES = new ConcurrentHashMap<>();

    private record Tile(FileChannel channel, int south, int west, int across, int[] starts) {}

    /** Everything that reaches into one square of a tile. */
    public record Square(Building[] buildings, Road[] roads) {}

    /** A building's outline in block coordinates: the outer edge first, then any courtyards. */
    public static final class Building {
        public final double[] xs, zs;
        /** Where each ring of the outline ends in xs and zs. */
        public final int[] ringEnds;
        public final double minX, minZ, maxX, maxZ;
        public final int kind, roof, floors, id;
        /** Real height in metres; 0 where nobody recorded it. */
        public final float metres;
        /** The ground it covers, in square blocks. */
        public final double area;
        /** The level it is built up from, worked out by UrbanBuilder the first time it is needed. */
        int base = NOT_WORKED_OUT;

        Building(double[] xs, double[] zs, int[] ringEnds, int kind, int roof, float metres, int floors, int id) {
            this.xs = xs; this.zs = zs; this.ringEnds = ringEnds;
            this.kind = kind; this.roof = roof; this.metres = metres; this.floors = floors; this.id = id;
            double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE, twice = 0;
            for (int i = 0, n = ringEnds[0]; i < n; i++) {
                minX = Math.min(minX, xs[i]); maxX = Math.max(maxX, xs[i]);
                minZ = Math.min(minZ, zs[i]); maxZ = Math.max(maxZ, zs[i]);
                int next = i + 1 == n ? 0 : i + 1;
                twice += xs[i] * zs[next] - xs[next] * zs[i];
            }
            this.minX = minX; this.minZ = minZ; this.maxX = maxX; this.maxZ = maxZ;
            this.area = Math.abs(twice) / 2;
        }

        /** Whether a point is inside the outline (and not in a courtyard). */
        public boolean contains(double x, double z) {
            if (x < minX || x > maxX || z < minZ || z > maxZ) return false;
            boolean inside = false;
            for (int ring = 0, start = 0; ring < ringEnds.length; start = ringEnds[ring++]) {
                for (int i = start, j = ringEnds[ring] - 1; i < ringEnds[ring]; j = i++) {
                    if ((zs[i] > z) != (zs[j] > z) && x < (xs[j] - xs[i]) * (z - zs[i]) / (zs[j] - zs[i]) + xs[i]) inside = !inside;
                }
            }
            return inside;
        }
    }

    /** The middle line of a stretch of road or railway, in block coordinates. */
    public static final class Road {
        public final double[] xs, zs;
        /** How far along the line each point is, in blocks. */
        public final double[] along;
        public final double minX, minZ, maxX, maxZ;
        public final int kind;
        /** A bridge is held whole, so the level of its deck can run from one end to the other. */
        public final boolean bridge;
        /** The level of each end of a bridge, worked out by UrbanBuilder the first time it is needed. */
        int startY = NOT_WORKED_OUT, endY = NOT_WORKED_OUT;

        Road(double[] xs, double[] zs, int kind, boolean bridge) {
            this.xs = xs; this.zs = zs; this.kind = kind; this.bridge = bridge;
            this.along = new double[xs.length];
            double minX = xs[0], minZ = zs[0], maxX = xs[0], maxZ = zs[0];
            for (int i = 1; i < xs.length; i++) {
                along[i] = along[i - 1] + Math.hypot(xs[i] - xs[i - 1], zs[i] - zs[i - 1]);
                minX = Math.min(minX, xs[i]); maxX = Math.max(maxX, xs[i]);
                minZ = Math.min(minZ, zs[i]); maxZ = Math.max(maxZ, zs[i]);
            }
            this.minX = minX; this.minZ = minZ; this.maxX = maxX; this.maxZ = maxZ;
        }

        /**
         * The square of the distance in metres from a point to the line, a block being metresX by
         * metresZ on the real ground. How far along the line the nearest point is goes in where[0].
         */
        public double distanceSquared(double x, double z, double metresX, double metresZ, double[] where) {
            double best = Double.MAX_VALUE;
            for (int i = 0; i + 1 < xs.length; i++) {
                double ax = (xs[i] - x) * metresX, az = (zs[i] - z) * metresZ;
                double dx = (xs[i + 1] - x) * metresX - ax, dz = (zs[i + 1] - z) * metresZ - az;
                double length = dx * dx + dz * dz;
                double t = length == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + az * dz) / length));
                double qx = ax + t * dx, qz = az + t * dz, distance = qx * qx + qz * qz;
                if (distance < best) {
                    best = distance;
                    where[0] = along[i] + t * (along[i + 1] - along[i]);
                }
            }
            return best;
        }
    }

    private Urban() {}

    /** The square holding the middle of a column of blocks; EMPTY where nothing is mapped or packed. */
    public static Square at(int blockX, int blockZ) {
        EarthConfig c = EarthConfig.get();
        double latitude = -(blockZ + 0.5) / c.blocksPerDegree(), longitude = (blockX + 0.5) / c.blocksPerDegree();
        if (latitude >= 90 || latitude <= -90 || longitude >= 180 || longitude < -180) return EMPTY;
        int south = (int) Math.floor(latitude), west = (int) Math.floor(longitude);
        int key = south * 1000 + west;
        Tile tile = TILES.computeIfAbsent(key, k -> open(south, west));
        if (tile == MISSING) return EMPTY;
        int row = Math.min(tile.across - 1, (int) ((latitude - south) * tile.across));
        int column = Math.min(tile.across - 1, (int) ((longitude - west) * tile.across));
        int index = row * tile.across + column;
        if (tile.starts[index] == tile.starts[index + 1]) return EMPTY;
        Square square = SQUARES.get((long) key << 32 | index);
        return square != null ? square : load(tile, key, index);
    }

    private static Square load(Tile tile, int key, int index) {
        Square square;
        try {
            long at = tile.starts[index] & 0xffffffffL;
            ByteBuffer packed = read(tile.channel, at, (int) ((tile.starts[index + 1] & 0xffffffffL) - at));
            Inflater inflater = new Inflater();
            inflater.setInput(packed);
            byte[] plain = new byte[Math.max(4096, packed.remaining() * 4)];
            int got = 0;
            while (!inflater.finished()) {
                if (got == plain.length) plain = java.util.Arrays.copyOf(plain, got * 2);
                int more = inflater.inflate(plain, got, plain.length - got);
                if (more == 0 && (inflater.needsInput() || inflater.needsDictionary())) throw new IOException("a square is cut short");
                got += more;
            }
            inflater.end();
            square = parse(ByteBuffer.wrap(plain, 0, got), tile);
        } catch (Exception e) {
            // A square that cannot be read is left unbuilt, rather than stopping the world.
            EarthWorldgen.LOG.error("Earth: the streets of a square could not be read: {}", e.toString());
            square = EMPTY;
        }
        if (SQUARES.size() >= MAX_SQUARES_IN_MEMORY) {
            // Make room by forgetting a quarter of what is held; anything still in use is simply read again.
            int drop = MAX_SQUARES_IN_MEMORY / 4;
            for (Long old : SQUARES.keySet()) {
                if (drop-- <= 0) break;
                SQUARES.remove(old);
            }
        }
        SQUARES.put((long) key << 32 | index, square);
        return square;
    }

    private static Square parse(ByteBuffer in, Tile tile) {
        Building[] buildings = new Building[number(in)];
        for (int b = 0; b < buildings.length; b++) {
            int kind = in.get() & 0xff, roof = in.get() & 0xff;
            float metres = number(in) / 10f;
            int floors = number(in);
            int[] ringEnds = new int[number(in)];
            double[][] rings = new double[ringEnds.length][];
            int total = 0, id = 0;
            for (int r = 0; r < rings.length; r++) {
                rings[r] = points(in, tile);
                // The same number wherever the building is met (it may lie in several squares and two tiles).
                if (r == 0) id = (int) Math.round(rings[0][0] * 8) * 31 + (int) Math.round(rings[0][1] * 8);
                ringEnds[r] = total += rings[r].length / 2;
            }
            double[] xs = new double[total], zs = new double[total];
            for (int r = 0, i = 0; r < rings.length; r++) {
                for (int p = 0; p < rings[r].length; p += 2, i++) {
                    xs[i] = rings[r][p];
                    zs[i] = rings[r][p + 1];
                }
            }
            buildings[b] = new Building(xs, zs, ringEnds, kind, roof, metres, floors, id * 0x9E3779B1);
        }
        Road[] roads = new Road[number(in)];
        for (int r = 0; r < roads.length; r++) {
            int kind = in.get() & 0xff, flags = in.get() & 0xff;
            double[] line = points(in, tile);
            double[] xs = new double[line.length / 2], zs = new double[line.length / 2];
            for (int i = 0; i < xs.length; i++) {
                xs[i] = line[i * 2];
                zs[i] = line[i * 2 + 1];
            }
            roads[r] = new Road(xs, zs, kind, (flags & 1) != 0);
        }
        return new Square(buildings, roads);
    }

    /** A run of points as block x, block z, block x and so on. In the file each is the difference from the one before. */
    private static double[] points(ByteBuffer in, Tile tile) {
        double blocksPerDegree = EarthConfig.get().blocksPerDegree();
        double[] out = new double[number(in) * 2];
        int east = 0, north = 0;
        for (int i = 0; i < out.length; i += 2) {
            east += signed(in);
            north += signed(in);
            out[i] = (tile.west + east / UNITS_PER_DEGREE) * blocksPerDegree;
            out[i + 1] = -(tile.south + north / UNITS_PER_DEGREE) * blocksPerDegree;
        }
        return out;
    }

    private static int number(ByteBuffer in) {
        int value = 0;
        for (int shift = 0; ; shift += 7) {
            int b = in.get();
            value |= (b & 0x7f) << shift;
            if (b >= 0) return value;
        }
    }

    private static int signed(ByteBuffer in) {
        int value = number(in);
        return (value & 1) == 0 ? value >>> 1 : -((value + 1) >>> 1);
    }

    private static Tile open(int south, int west) {
        String name = String.format("%c%02d%c%03d", south < 0 ? 'S' : 'N', Math.abs(south), west < 0 ? 'W' : 'E', Math.abs(west));
        Path file = Path.of(EarthConfig.get().urbanDir, name.substring(0, 3), name + ".urb");
        if (!Files.exists(file)) return MISSING;
        try {
            FileChannel channel = FileChannel.open(file, StandardOpenOption.READ);
            ByteBuffer head = read(channel, 0, 8);
            if (head.getInt(0) != 0x45555242 || head.get(4) != 1) throw new IOException("not a version 1 streets file");
            int across = head.getShort(6) & 0xffff;
            int[] starts = new int[across * across + 1];
            read(channel, 8, starts.length * 4).asIntBuffer().get(starts);
            return new Tile(channel, south, west, across, starts);
        } catch (IOException e) {
            EarthWorldgen.LOG.error("Earth: streets file {} could not be used: {}", file, e.getMessage());
            return MISSING;
        }
    }

    private static ByteBuffer read(FileChannel channel, long at, int length) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(length);
        while (buffer.hasRemaining()) if (channel.read(buffer, at + buffer.position()) < 0) throw new IOException("the file ends early");
        return buffer.flip();
    }
}
