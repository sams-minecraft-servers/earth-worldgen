package tj.earth.worldgen;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;

/**
 * Fetches the map data for an area the first time land over it is made, so the mod works with
 * nothing downloaded in advance: elevation tiles from the public Terrain Tiles bucket (packed as
 * tools/PackElevation.java does), and land cover tiles from ESA WorldCover's public bucket.
 *
 * The thread making the chunk waits for the download; other chunks keep being made meanwhile. A tile
 * the source does not have (open sea) is remembered with an empty ".none" file, so it is asked for
 * only once. Settings: "download" in earth_worldgen.json.
 */
final class Downloads {
    private Downloads() {}

    private static final String ELEVATION = "https://s3.amazonaws.com/elevation-tiles-prod/skadi/";
    private static final String LAND_COVER = "https://esa-worldcover.s3.eu-central-1.amazonaws.com/v200/2021/map/";
    static final int MAGIC = 0x45484754, SQUARE = 256;

    private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(30)).build();
    private static final ConcurrentHashMap<Path, Object> LOCKS = new ConcurrentHashMap<>();
    private static volatile Semaphore slots;

    /** Makes sure the packed elevation tile is there if it can be; afterwards the file exists, or the area is sea. */
    static void elevation(final Path packed, final String name) {
        if (!EarthConfig.get().download.elevation) return;
        fetch(packed, () -> {
            byte[] gz = get(ELEVATION + name.substring(0, 3) + "/" + name + ".hgt.gz");
            if (gz == null) return null;
            byte[] raw;
            try (InputStream in = new GZIPInputStream(new java.io.ByteArrayInputStream(gz), 1 << 16)) { raw = in.readAllBytes(); }
            return pack(raw);
        }, "elevation " + name);
    }

    /** Makes sure the land cover tile is there if it can be. */
    static void landCover(final Path file, final String name) {
        if (!EarthConfig.get().download.landCover) return;
        fetch(file, () -> {
            // Streamed to disk: a land cover tile can be hundreds of MB.
            Path part = file.resolveSibling(file.getFileName() + ".part");
            Files.createDirectories(file.getParent());
            if (!toFile(LAND_COVER + name, part)) return null;
            Files.move(part, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return STREAMED;
        }, "land cover " + name);
    }

    private static final byte[] STREAMED = new byte[0];

    private interface Source {
        byte[] bytes() throws Exception;
    }

    private static void fetch(final Path file, final Source source, final String what) {
        if (Files.exists(file)) return;
        Path none = file.resolveSibling(file.getFileName() + ".none");
        if (Files.exists(none)) return;
        synchronized (LOCKS.computeIfAbsent(file, k -> new Object())) {
            if (Files.exists(file) || Files.exists(none)) return;
            Semaphore s = slots();
            try {
                s.acquire();
                long started = System.nanoTime();
                byte[] bytes = source.bytes();
                Files.createDirectories(file.getParent());
                if (bytes == null) {
                    Files.createFile(none);
                    return;
                }
                if (bytes != STREAMED) {
                    Path part = file.resolveSibling(file.getFileName() + ".part");
                    Files.write(part, bytes);
                    Files.move(part, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                }
                EarthWorldgen.LOG.info("Earth: downloaded {} ({} MB, {} s)", what, Files.size(file) / 1_048_576, (System.nanoTime() - started) / 1_000_000_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                // Not remembered: the next chunk over this area tries again.
                EarthWorldgen.LOG.warn("Earth: could not download {}: {}", what, e.getMessage());
            } finally {
                s.release();
                LOCKS.remove(file);
            }
        }
    }

    private static Semaphore slots() {
        Semaphore s = slots;
        if (s == null) {
            synchronized (Downloads.class) {
                if (slots == null) slots = new Semaphore(Math.max(1, EarthConfig.get().download.atOnce));
                s = slots;
            }
        }
        return s;
    }

    private static HttpRequest request(final String url) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(30)).header("User-Agent", "earth-worldgen (Minecraft mod)").GET().build();
    }

    /** The body of a link, or null when the source does not have it (404 or 403). Tries three times. */
    private static byte[] get(final String url) throws IOException, InterruptedException {
        for (int attempt = 1; ; attempt++) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 24);
            try {
                return copy(url, out) ? out.toByteArray() : null;
            } catch (IOException e) {
                if (attempt >= 3) throw e;
                Thread.sleep(5000L * attempt);
            }
        }
    }

    /** Saves a link to a file; false when the source does not have it. Tries three times, each from the start. */
    private static boolean toFile(final String url, final Path to) throws IOException, InterruptedException {
        for (int attempt = 1; ; attempt++) {
            try {
                boolean found;
                try (java.io.OutputStream out = Files.newOutputStream(to)) { found = copy(url, out); }
                if (!found) Files.deleteIfExists(to);
                return found;
            } catch (IOException e) {
                Files.deleteIfExists(to);
                if (attempt >= 3) throw e;
                Thread.sleep(5000L * attempt);
            }
        }
    }

    /** Copies a link's body into out, at no more than maxMegabytesPerSecond; false on 404/403. One attempt. */
    private static boolean copy(final String url, final java.io.OutputStream out) throws IOException, InterruptedException {
        HttpResponse<InputStream> response = HTTP.send(request(url), HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = response.body()) {
            if (response.statusCode() == 404 || response.statusCode() == 403) return false;
            if (response.statusCode() != 200) throw new IOException("the server answered " + response.statusCode());
            double limit = EarthConfig.get().download.maxMegabytesPerSecond * 1_048_576;
            long started = System.nanoTime(), total = 0;
            byte[] buffer = new byte[1 << 16];
            for (int n; (n = in.read(buffer)) > 0; ) {
                out.write(buffer, 0, n);
                total += n;
                if (limit > 0) {
                    long wait = started + (long) (total / limit * 1e9) - System.nanoTime();
                    if (wait > 0) Thread.sleep(wait / 1_000_000, (int) (wait % 1_000_000));
                }
            }
            return true;
        }
    }

    // ---------------------------------------------------------------- packing (the same as tools/PackElevation.java)

    static byte[] pack(final byte[] raw) throws IOException, DataFormatException {
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

    private static int value(final byte[] raw, final int size, final int row, final int column) {
        int at = (row * size + column) * 2;
        return (short) (raw[at] << 8 | raw[at + 1] & 0xff);
    }

    /** Reads the packed tile back the way the mod does and compares every height with the download. */
    private static void check(final byte[] packed, final byte[] raw, final int size) throws IOException, DataFormatException {
        ByteBuffer in = ByteBuffer.wrap(packed);
        int across = (size + SQUARE - 1) / SQUARE;
        Inflater inflater = new Inflater();
        byte[] plain = new byte[SQUARE * SQUARE * 2];
        short[] heights = new short[SQUARE * SQUARE];
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
            if (got != n * 2) { inflater.end(); throw new IOException("a packed square is short"); }
            for (int r = 0; r < h; r++) {
                for (int c = 0; c < w; c++) {
                    int i = r * w + c;
                    int before = c > 0 ? heights[i - 1] : r > 0 ? heights[i - w] : 0;
                    heights[i] = (short) (before + (plain[i] << 8 | plain[n + i] & 0xff));
                    if (heights[i] != value(raw, size, r0 + r, c0 + c)) { inflater.end(); throw new IOException("the packed tile does not match the download"); }
                }
            }
        }
        inflater.end();
    }
}
