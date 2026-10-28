import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;

/**
 * Packs the downloaded elevation tiles into the form the mod reads.
 *
 *     java -Xmx1g tools/PackElevation.java <hgt-gz folder> <packed folder> [threads, default 4]
 *
 * A downloaded tile (N45/N45E006.hgt.gz) is one gzip stream of 26 MB, which has to be unpacked
 * whole to read one height. A packed tile (N45/N45E006.ehg) holds the same heights, exactly, as
 * 256 x 256 squares compressed one by one behind a table of where each is, so the mod unpacks
 * only the squares under places being explored:
 *
 *     int "EHGT", int samples per side, int square size, then one int per square (rows of squares,
 *     north first) and one more: where each square's bytes start and where the last one ends.
 *     A square is deflated; unpacked it is the high bytes of all its values and then the low bytes,
 *     each value being the difference from the sample to its west (from the one to its north in the
 *     first column, from 0 in the corner). Everything is big-endian.
 *
 * It can be stopped and started again: a tile already packed is skipped, and a packed file only
 * gets its name once it is complete and has been read back and compared with the download.
 */
public class PackElevation {
    static final int MAGIC = 0x45484754, SQUARE = 256;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) { System.err.println("usage: java tools/PackElevation.java <hgt-gz folder> <packed folder> [threads]"); System.exit(2); }
        Path from = Path.of(args[0]), to = Path.of(args[1]);
        int threads = args.length > 2 ? Integer.parseInt(args[2]) : 4;

        List<Path> all = new ArrayList<>();
        try (Stream<Path> files = Files.walk(from)) { files.filter(f -> f.toString().endsWith(".hgt.gz")).forEach(all::add); }
        List<Path> todo = new ArrayList<>();
        for (Path file : all) if (!Files.exists(packedPath(to, file))) todo.add(file);
        System.out.printf("%d tiles downloaded, %d still to pack%n", all.size(), todo.size());

        AtomicInteger done = new AtomicInteger(), failed = new AtomicInteger();
        AtomicLong bytesIn = new AtomicLong(), bytesOut = new AtomicLong();
        long started = System.currentTimeMillis();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (Path file : todo) {
            pool.execute(() -> {
                try {
                    Path out = packedPath(to, file);
                    Files.createDirectories(out.getParent());
                    Path part = out.resolveSibling(out.getFileName() + ".part");
                    byte[] packed = pack(file);
                    Files.write(part, packed);
                    Files.move(part, out, StandardCopyOption.ATOMIC_MOVE);
                    bytesIn.addAndGet(Files.size(file));
                    bytesOut.addAndGet(packed.length);
                } catch (Throwable e) {
                    failed.incrementAndGet();
                    System.out.println("failed: " + file + ": " + e);
                }
                int n = done.incrementAndGet();
                if (n % 500 == 0 || n == todo.size()) {
                    double hours = (System.currentTimeMillis() - started) / 3.6e6;
                    System.out.printf("%s  %d of %d tiles, %.1f GB packed into %.1f GB, %.1f h so far, about %.1f h to go, %d failed%n",
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")), n, todo.size(),
                        bytesIn.get() / 1e9, bytesOut.get() / 1e9, hours, hours * (todo.size() - n) / n, failed.get());
                }
            });
        }
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.DAYS);
        System.out.println(failed.get() > 0 ? "finished: " + failed.get() + " tiles could not be packed; run again to retry them" : "finished: everything is packed");
        if (failed.get() > 0) System.exit(1);
    }

    static Path packedPath(Path to, Path download) {
        String name = download.getFileName().toString();
        return to.resolve(download.getParent().getFileName()).resolve(name.substring(0, name.length() - ".hgt.gz".length()) + ".ehg");
    }

    static byte[] pack(Path download) throws IOException, DataFormatException {
        byte[] raw;
        try (InputStream in = new GZIPInputStream(Files.newInputStream(download), 1 << 16)) { raw = in.readAllBytes(); }
        int size = (int) Math.round(Math.sqrt(raw.length / 2));
        if ((long) size * size * 2 != raw.length || size < 2) throw new IOException("not a square grid of 16-bit heights");

        int across = (size + SQUARE - 1) / SQUARE, squares = across * across;
        int head = 12 + 4 * (squares + 1);
        ByteArrayOutputStream body = new ByteArrayOutputStream(raw.length / 4);
        ByteBuffer table = ByteBuffer.allocate(head).putInt(MAGIC).putInt(size).putInt(SQUARE);
        Deflater deflater = new Deflater(6);
        byte[] plain = new byte[SQUARE * SQUARE * 2], buffer = new byte[1 << 16];
        for (int square = 0; square < squares; square++) {
            int r0 = square / across * SQUARE, c0 = square % across * SQUARE;
            int h = Math.min(SQUARE, size - r0), w = Math.min(SQUARE, size - c0), n = w * h;
            for (int r = 0; r < h; r++) {
                for (int c = 0; c < w; c++) {
                    int before = c > 0 ? value(raw, size, r0 + r, c0 + c - 1) : r > 0 ? value(raw, size, r0 + r - 1, c0) : 0;
                    int difference = value(raw, size, r0 + r, c0 + c) - before;
                    plain[r * w + c] = (byte) (difference >> 8);
                    plain[n + r * w + c] = (byte) difference;
                }
            }
            table.putInt(head + body.size());
            deflater.reset();
            deflater.setInput(plain, 0, n * 2);
            deflater.finish();
            while (!deflater.finished()) body.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();
        table.putInt(head + body.size());
        byte[] packed = new byte[head + body.size()];
        System.arraycopy(table.array(), 0, packed, 0, head);
        System.arraycopy(body.toByteArray(), 0, packed, head, body.size());
        check(packed, raw, size);
        return packed;
    }

    static int value(byte[] raw, int size, int row, int column) {
        int at = (row * size + column) * 2;
        return (short) (raw[at] << 8 | raw[at + 1] & 0xff);
    }

    /** Reads the packed tile back the way the mod does and compares every height with the download. */
    static void check(byte[] packed, byte[] raw, int size) throws IOException, DataFormatException {
        ByteBuffer in = ByteBuffer.wrap(packed);
        if (in.getInt(0) != MAGIC || in.getInt(4) != size || in.getInt(8) != SQUARE) throw new IOException("the packed header is wrong");
        int across = (size + SQUARE - 1) / SQUARE;
        Inflater inflater = new Inflater();
        byte[] plain = new byte[SQUARE * SQUARE * 2];
        for (int square = 0; square < across * across; square++) {
            int r0 = square / across * SQUARE, c0 = square % across * SQUARE;
            int h = Math.min(SQUARE, size - r0), w = Math.min(SQUARE, size - c0), n = w * h;
            int start = in.getInt(12 + 4 * square), end = in.getInt(16 + 4 * square);
            inflater.reset();
            inflater.setInput(packed, start, end - start);
            int got = 0;
            while (got < n * 2 && !inflater.finished()) {
                int more = inflater.inflate(plain, got, n * 2 - got);
                if (more == 0 && inflater.needsInput()) break;
                got += more;
            }
            if (got != n * 2) throw new IOException("a packed square is short");
            short[] heights = new short[n];
            for (int r = 0; r < h; r++) {
                for (int c = 0; c < w; c++) {
                    int i = r * w + c;
                    int before = c > 0 ? heights[i - 1] : r > 0 ? heights[i - w] : 0;
                    heights[i] = (short) (before + (plain[i] << 8 | plain[n + i] & 0xff));
                    if (heights[i] != value(raw, size, r0 + r, c0 + c)) throw new IOException("the packed tile does not match the download");
                }
            }
        }
        inflater.end();
    }
}
