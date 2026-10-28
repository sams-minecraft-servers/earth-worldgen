package tj.earth.worldgen;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.Inflater;

/**
 * What covers the real ground at a point: trees, crops, buildings, water and so on, from the
 * ESA WorldCover map (10 m squares, about two blocks on a 1:5 map). The files are used exactly
 * as published: 3 x 3 degree GeoTIFFs of one byte per square, stored as compressed 1024-square
 * tiles, so only the tiles under places being explored are ever read.
 */
public final class LandCover {
    public static final int UNKNOWN = 0, TREES = 10, SHRUBS = 20, GRASS = 30, CROPS = 40, BUILT_UP = 50, BARE = 60,
        SNOW_AND_ICE = 70, WATER = 80, WETLAND = 90, MANGROVES = 95, MOSS_AND_LICHEN = 100;

    private static final int FILE_DEGREES = 3, SQUARES_PER_DEGREE = 12000, MAX_TILES_IN_MEMORY = 96;
    private static final Sheet MISSING = new Sheet(null, 0, 0, 0, null, null);
    private static final ConcurrentHashMap<Integer, Sheet> SHEETS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, byte[]> TILES = new ConcurrentHashMap<>();

    /** One published file: where its compressed tiles are. */
    private record Sheet(FileChannel channel, int width, int height, int tileSize, long[] offsets, long[] lengths) {}

    private LandCover() {}

    /** One of the constants above; UNKNOWN where there is no map (open sea, a file not downloaded). */
    public static int at(double latitude, double longitude) {
        if (latitude >= 90 || latitude <= -90) return UNKNOWN;
        longitude = ((longitude + 180) % 360 + 360) % 360 - 180;
        int south = Math.floorDiv((int) Math.floor(latitude), FILE_DEGREES) * FILE_DEGREES;
        int west = Math.floorDiv((int) Math.floor(longitude), FILE_DEGREES) * FILE_DEGREES;
        int key = south * 1000 + west;
        Sheet sheet = SHEETS.computeIfAbsent(key, k -> open(south, west));
        if (sheet == MISSING) return UNKNOWN;

        int column = Math.min(sheet.width - 1, (int) ((longitude - west) * SQUARES_PER_DEGREE));
        int row = Math.min(sheet.height - 1, (int) ((south + FILE_DEGREES - latitude) * SQUARES_PER_DEGREE));
        int across = (sheet.width + sheet.tileSize - 1) / sheet.tileSize;
        int index = (row / sheet.tileSize) * across + column / sheet.tileSize;
        byte[] tile = TILES.get((long) key << 32 | index);
        if (tile == null) tile = load(sheet, key, index);
        return tile[(row % sheet.tileSize) * sheet.tileSize + column % sheet.tileSize] & 0xff;
    }

    private static byte[] load(Sheet sheet, int key, int index) {
        byte[] squares = new byte[sheet.tileSize * sheet.tileSize];
        try {
            ByteBuffer packed = ByteBuffer.allocate((int) sheet.lengths[index]);
            long at = sheet.offsets[index];
            while (packed.hasRemaining()) {
                int n = sheet.channel.read(packed, at + packed.position());
                if (n < 0) throw new IOException("the file ends early");
            }
            Inflater inflater = new Inflater();
            inflater.setInput(packed.array());
            int done = 0;
            while (done < squares.length && !inflater.finished()) {
                int n = inflater.inflate(squares, done, squares.length - done);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break;
                done += n;
            }
            inflater.end();
        } catch (Exception e) {
            // A tile that cannot be read is treated as unmapped, once, rather than stopping the world.
            EarthWorldgen.LOG.error("Earth: a land cover tile could not be read: {}", e.toString());
            squares = new byte[sheet.tileSize * sheet.tileSize];
        }
        if (TILES.size() >= MAX_TILES_IN_MEMORY) {
            // Make room by forgetting a quarter of what is held; anything still in use is simply read again.
            int drop = MAX_TILES_IN_MEMORY / 4;
            for (Long old : TILES.keySet()) {
                if (drop-- <= 0) break;
                TILES.remove(old);
            }
        }
        TILES.put((long) key << 32 | index, squares);
        return squares;
    }

    private static Sheet open(int south, int west) {
        String name = String.format("ESA_WorldCover_10m_2021_v200_%c%02d%c%03d_Map.tif", south < 0 ? 'S' : 'N', Math.abs(south), west < 0 ? 'W' : 'E', Math.abs(west));
        Path file = Path.of(EarthConfig.get().landCoverDir, name);
        Downloads.landCover(file, name);
        if (!Files.exists(file)) return MISSING;
        try {
            FileChannel channel = FileChannel.open(file, StandardOpenOption.READ);
            ByteBuffer head = read(channel, 0, 8).order(ByteOrder.LITTLE_ENDIAN);
            if (head.getShort(0) != 0x4949 || head.getShort(2) != 42) throw new IOException("not a little-endian TIFF");
            long directory = head.getInt(4) & 0xffffffffL;
            int entries = read(channel, directory, 2).order(ByteOrder.LITTLE_ENDIAN).getShort(0) & 0xffff;
            ByteBuffer tags = read(channel, directory + 2, entries * 12).order(ByteOrder.LITTLE_ENDIAN);
            int width = 0, height = 0, tileSize = 0, compression = 0, bits = 0;
            long[] offsets = null, lengths = null;
            for (int i = 0; i < entries; i++) {
                int tag = tags.getShort(i * 12) & 0xffff, type = tags.getShort(i * 12 + 2) & 0xffff, count = tags.getInt(i * 12 + 4);
                long value = type == 3 ? tags.getShort(i * 12 + 8) & 0xffff : tags.getInt(i * 12 + 8) & 0xffffffffL;
                switch (tag) {
                    case 256 -> width = (int) value;
                    case 257 -> height = (int) value;
                    case 258 -> bits = (int) value;
                    case 259 -> compression = (int) value;
                    case 322 -> tileSize = (int) value;
                    case 323 -> { if (value != tileSize) throw new IOException("tiles are not square"); }
                    case 324 -> offsets = longs(channel, value, count, type);
                    case 325 -> lengths = longs(channel, value, count, type);
                    default -> { }
                }
            }
            if (bits != 8 || compression != 8 || tileSize <= 0 || offsets == null || lengths == null) throw new IOException("not a tiled, deflate-compressed, one-byte map");
            return new Sheet(channel, width, height, tileSize, offsets, lengths);
        } catch (IOException e) {
            EarthWorldgen.LOG.error("Earth: land cover file {} could not be used: {}", file, e.getMessage());
            return MISSING;
        }
    }

    private static long[] longs(FileChannel channel, long at, int count, int type) throws IOException {
        int size = type == 3 ? 2 : type == 16 ? 8 : 4;
        ByteBuffer data = read(channel, at, count * size).order(ByteOrder.LITTLE_ENDIAN);
        long[] out = new long[count];
        for (int i = 0; i < count; i++) out[i] = size == 2 ? data.getShort(i * 2) & 0xffff : size == 8 ? data.getLong(i * 8) : data.getInt(i * 4) & 0xffffffffL;
        return out;
    }

    private static ByteBuffer read(FileChannel channel, long at, int length) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(length);
        while (buffer.hasRemaining()) if (channel.read(buffer, at + buffer.position()) < 0) throw new IOException("the file ends early");
        return buffer.flip();
    }
}
