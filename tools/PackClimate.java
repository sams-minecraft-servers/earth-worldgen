import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.zip.GZIPOutputStream;

/**
 * Packs a Köppen-Geiger climate map (Beck et al. 2023, koppen_geiger_0p00833333.tif: 1 km, one
 * byte per square, tiled, LZW) into the mod's climate.bin.gz: a coarser grid where each square
 * holds the climate found most often under it.
 *
 *     java tools/PackClimate.java koppen_geiger_0p00833333.tif common/src/main/resources/earth_worldgen/climate.bin.gz [factor]
 *
 * factor (default 6) is how many source squares across make one packed square: 6 gives 0.05
 * degrees, a 7200 x 3600 grid (26 MB once loaded). The file: "EKG1", width, height (ints, big
 * endian), then width x height bytes, row by row from the north, each 0 (sea) or 1-30 (the
 * classes in the map's legend.txt).
 */
public final class PackClimate {
    public static void main(String[] args) throws IOException {
        int factor = args.length > 2 ? Integer.parseInt(args[2]) : 6;
        try (FileChannel ch = FileChannel.open(Path.of(args[0]))) {
            ByteBuffer head = read(ch, 0, 16);
            if (head.get(0) != 'I') throw new IOException("only little-endian TIFF");
            int ifd = head.getInt(4);
            int n = read(ch, ifd, 2).getShort(0) & 0xffff;
            ByteBuffer entries = read(ch, ifd + 2, n * 12);
            int width = 0, height = 0, tile = 0, compression = 0, predictor = 1;
            long[] offsets = null, lengths = null;
            for (int i = 0; i < n; i++) {
                int tag = entries.getShort(i * 12) & 0xffff, type = entries.getShort(i * 12 + 2) & 0xffff, count = entries.getInt(i * 12 + 4);
                long value = type == 3 ? entries.getShort(i * 12 + 8) & 0xffff : entries.getInt(i * 12 + 8) & 0xffffffffL;
                switch (tag) {
                    case 256 -> width = (int) value;
                    case 257 -> height = (int) value;
                    case 259 -> compression = (int) value;
                    case 317 -> predictor = (int) value;
                    case 322 -> tile = (int) value;
                    case 324 -> offsets = longs(ch, value, count, type);
                    case 325 -> lengths = longs(ch, value, count, type);
                    default -> {}
                }
            }
            if (compression != 5 || predictor != 1 || tile <= 0) throw new IOException("expected a tiled LZW map without predictor");
            int outW = width / factor, outH = height / factor;
            if ((long) outW * outH > 200_000_000L) throw new IOException("packed grid would be too large: " + outW + " x " + outH);
            byte[] out = new byte[outW * outH];
            int across = (width + tile - 1) / tile, down = (height + tile - 1) / tile;
            // Rows of the full map, held only until each band of factor rows has been packed.
            byte[] rows = new byte[(tile + factor) * width];
            int held = 0, firstRow = 0;
            int[] counts = new int[32];
            for (int ty = 0; ty < down; ty++) {
                for (int tx = 0; tx < across; tx++) {
                    int index = ty * across + tx;
                    byte[] squares = lzw(read(ch, offsets[index], (int) lengths[index]), tile * tile);
                    for (int y = 0; y < tile && ty * tile + y < height; y++)
                        System.arraycopy(squares, y * tile, rows, (held + y) * width + tx * tile, Math.min(tile, width - tx * tile));
                }
                held += Math.min(tile, height - ty * tile);
                int done = 0;
                for (; done + factor <= held; done += factor) {
                    int oy = (firstRow + done) / factor;
                    if (oy >= outH) break;
                    for (int ox = 0; ox < outW; ox++) {
                        java.util.Arrays.fill(counts, 0);
                        for (int y = 0; y < factor; y++)
                            for (int x = 0; x < factor; x++) counts[rows[(done + y) * width + ox * factor + x] & 31]++;
                        // Land wins over sea where any is found, so coasts keep their climate.
                        int best = 0, most = 0;
                        for (int c = 1; c < 32; c++) if (counts[c] > most) { most = counts[c]; best = c; }
                        out[oy * outW + ox] = (byte) best;
                    }
                }
                System.arraycopy(rows, done * width, rows, 0, (held - done) * width);
                held -= done;
                firstRow += done;
                System.err.printf("row %d of %d%n", ty + 1, down);
            }
            try (DataOutputStream o = new DataOutputStream(new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(Path.of(args[1]))), 1 << 16))) {
                o.writeBytes("EKG1");
                o.writeInt(outW);
                o.writeInt(outH);
                o.write(out);
            }
            System.err.printf("wrote %s: %d x %d%n", args[1], outW, outH);
        }
    }

    /** TIFF's LZW (MSB first, early change). */
    static byte[] lzw(ByteBuffer in, int size) {
        byte[] out = new byte[size];
        byte[][] table = new byte[4096][];
        for (int i = 0; i < 256; i++) table[i] = new byte[] {(byte) i};
        int next = 258, bits = 9, pos = 0;
        long buffer = 0;
        int have = 0, src = 0, limit = in.limit();
        byte[] previous = null;
        while (true) {
            while (have < bits && src < limit) { buffer = buffer << 8 | (in.get(src++) & 0xff); have += 8; }
            if (have < bits) break;
            int code = (int) (buffer >>> (have - bits)) & ((1 << bits) - 1);
            have -= bits;
            if (code == 257) break;
            if (code == 256) { next = 258; bits = 9; previous = null; continue; }
            byte[] entry;
            if (code < next && table[code] != null) entry = table[code];
            else if (previous != null) { entry = java.util.Arrays.copyOf(previous, previous.length + 1); entry[previous.length] = previous[0]; }
            else throw new IllegalStateException("bad LZW code " + code);
            int len = Math.min(entry.length, size - pos);
            System.arraycopy(entry, 0, out, pos, len);
            pos += len;
            if (previous != null && next < 4096) {
                byte[] add = java.util.Arrays.copyOf(previous, previous.length + 1);
                add[previous.length] = entry[0];
                table[next++] = add;
            }
            previous = entry;
            if (next + 1 >= (1 << bits) && bits < 12) bits++;
            if (pos >= size) break;
        }
        return out;
    }

    static long[] longs(FileChannel ch, long at, int count, int type) throws IOException {
        ByteBuffer b = read(ch, at, count * (type == 3 ? 2 : type == 16 ? 8 : 4));
        long[] r = new long[count];
        for (int i = 0; i < count; i++) r[i] = type == 3 ? b.getShort(i * 2) & 0xffff : type == 16 ? b.getLong(i * 8) : b.getInt(i * 4) & 0xffffffffL;
        return r;
    }

    static ByteBuffer read(FileChannel ch, long at, int length) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
        while (b.hasRemaining() && ch.read(b, at + b.position()) > 0) {}
        return b.flip();
    }
}
