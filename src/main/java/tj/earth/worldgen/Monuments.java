package tj.earth.worldgen;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Famous buildings and wrecks (the Eiffel Tower, the Statue of Liberty, the Titanic...), each built
 * where it really stands, as the chunks under it are first made: every chunk builds its own part,
 * so a monument comes out whole whichever of its chunks is made first.
 *
 * The monuments are in the mod's monuments.bin.gz (made by the server's landmark tools; format in
 * docs/Monuments.md), plus any files named in monuments.extraFiles. A monument stands on the first
 * free block above the ground or water at its centre, on a stone footing, with the air it stands
 * in cleared; a wreck lies on the sea bed as it is. Chunks that already exist are never changed.
 */
public final class Monuments {
    private Monuments() {}

    /** One monument. Box and clear coordinates are relative to the block on the ground at its centre. */
    record Monument(String id, String name, String country, double latitude, double longitude, int x, int z, boolean seabed,
                    short[] clear, short[] boxes, int[] palette) {}

    private static volatile Map<Long, List<Monument>> byChunk;
    private static volatile BlockState[] states;
    private static final List<String> paletteIds = new ArrayList<>();

    /** Builds the parts of any monuments that reach into this chunk. */
    public static void build(final ChunkAccess chunk) {
        EarthConfig.MonumentSettings settings = EarthConfig.get().monuments;
        if (!settings.enabled) return;
        List<Monument> here = index().get(chunk.getPos().pack());
        if (here == null) return;
        BlockState[] palette = states();
        ChunkPos at = chunk.getPos();
        int minX = at.getMinBlockX(), minZ = at.getMinBlockZ(), maxX = minX + 15, maxZ = minZ + 15;
        int bottom = chunk.getMinY(), top = chunk.getMaxY();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (Monument m : here) {
            if (settings.disabled.contains(m.id) || EarthConfig.listed(settings.disabledCountries, m.country)) continue;
            int[] centre = centre(m);
            int cx = centre[0], cz = centre[1], base = base(m, cx, cz);
            short[] c = m.clear;
            if (c[4] > 0) {
                for (int x = Math.max(minX, cx + c[0]); x <= Math.min(maxX, cx + c[2]); x++)
                    for (int z = Math.max(minZ, cz + c[1]); z <= Math.min(maxZ, cz + c[3]); z++)
                        for (int y = Math.max(bottom, base); y <= Math.min(top, base + c[4]); y++)
                            chunk.setBlockState(pos.set(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
            short[] b = m.boxes;
            for (int i = 0; i < b.length; i += 7) {
                int x1 = Math.max(minX, cx + b[i]), x2 = Math.min(maxX, cx + b[i + 3]);
                int z1 = Math.max(minZ, cz + b[i + 2]), z2 = Math.min(maxZ, cz + b[i + 5]);
                if (x1 > x2 || z1 > z2) continue;
                int y1 = Math.max(bottom, base + b[i + 1]), y2 = Math.min(top, base + b[i + 4]);
                BlockState state = palette[m.palette[b[i + 6]]];
                for (int x = x1; x <= x2; x++)
                    for (int z = z1; z <= z2; z++)
                        for (int y = y1; y <= y2; y++)
                            chunk.setBlockState(pos.set(x, y, z), state, 2);
            }
        }
    }

    /** Every monument, for listing (id, name, country). */
    public static List<Monument> all() {
        List<Monument> out = new ArrayList<>();
        for (List<Monument> l : index().values()) for (Monument m : l) if (!out.contains(m)) out.add(m);
        return out;
    }

    /** The block at the monument's centre on this world's map. */
    static int[] centre(final Monument m) {
        EarthConfig c = EarthConfig.get();
        if (c.horizontalScale == 5.0) return new int[] { m.x, m.z };   // exactly where the landmark tools put it
        double perDegree = c.blocksPerDegree();
        return new int[] { (int) Math.round(m.longitude * perDegree), (int) Math.round(-m.latitude * perDegree) };
    }

    /** The Y the monument's own Y 0 is at: the first free block above the ground (or the water), or above the sea bed for a wreck. */
    private static int base(final Monument m, final int x, final int z) {
        Terrain.Column column = Terrain.column(x, z);
        if (m.seabed || column.waterLevel() == Terrain.DRY) return column.top();
        return Math.max(column.top(), column.waterLevel());
    }

    private static BlockState[] states() {
        BlockState[] s = states;
        if (s != null && s.length == paletteIds.size()) return s;
        synchronized (Monuments.class) {
            s = new BlockState[paletteIds.size()];
            for (int i = 0; i < s.length; i++) {
                try {
                    s[i] = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, paletteIds.get(i), false).blockState();
                } catch (Exception e) {
                    EarthWorldgen.LOG.warn("Earth: monuments use a block the game does not know ({}); stone is used instead", paletteIds.get(i));
                    s[i] = Blocks.STONE.defaultBlockState();
                }
            }
            states = s;
            return s;
        }
    }

    private static Map<Long, List<Monument>> index() {
        Map<Long, List<Monument>> m = byChunk;
        if (m != null) return m;
        synchronized (Monuments.class) {
            if (byChunk != null) return byChunk;
            List<Monument> all = new ArrayList<>();
            try (InputStream in = Monuments.class.getResourceAsStream("/earth_worldgen/monuments.bin.gz")) {
                if (in != null) read(in, all, "the mod's monuments");
            } catch (IOException e) {
                EarthWorldgen.LOG.error("Earth: the mod's monuments could not be read: {}", e.getMessage());
            }
            for (String file : EarthConfig.get().monuments.extraFiles) {
                try (InputStream in = Files.newInputStream(Path.of(file))) {
                    read(in, all, file);
                } catch (IOException e) {
                    EarthWorldgen.LOG.error("Earth: monuments file {} could not be read: {}", file, e.getMessage());
                }
            }
            Map<Long, List<Monument>> index = new HashMap<>();
            for (Monument mon : all) {
                int[] centre = centre(mon);
                int x1 = mon.clear[0], z1 = mon.clear[1], x2 = mon.clear[2], z2 = mon.clear[3];
                for (int i = 0; i < mon.boxes.length; i += 7) {
                    x1 = Math.min(x1, mon.boxes[i]); z1 = Math.min(z1, mon.boxes[i + 2]);
                    x2 = Math.max(x2, mon.boxes[i + 3]); z2 = Math.max(z2, mon.boxes[i + 5]);
                }
                for (int cx = (centre[0] + x1) >> 4; cx <= (centre[0] + x2) >> 4; cx++)
                    for (int cz = (centre[1] + z1) >> 4; cz <= (centre[1] + z2) >> 4; cz++)
                        index.computeIfAbsent(ChunkPos.pack(cx, cz), k -> new ArrayList<>(1)).add(mon);
            }
            EarthWorldgen.LOG.info("Earth: {} monuments known", all.size());
            byChunk = index;
            return index;
        }
    }

    private static void read(final InputStream raw, final List<Monument> into, final String what) throws IOException {
        DataInputStream in = new DataInputStream(new GZIPInputStream(raw, 1 << 16));
        if (in.readInt() != 0x454d4f4e || in.readInt() != 1) throw new IOException(what + " is not a version 1 monuments file");
        int[] palette = new int[in.readInt()];
        synchronized (paletteIds) {
            for (int i = 0; i < palette.length; i++) {
                String id = in.readUTF();
                int at = paletteIds.indexOf(id);
                if (at < 0) { at = paletteIds.size(); paletteIds.add(id); }
                palette[i] = at;
            }
        }
        int count = in.readInt();
        for (int n = 0; n < count; n++) {
            String id = in.readUTF(), name = in.readUTF(), country = in.readUTF();
            double lat = in.readDouble(), lon = in.readDouble();
            int x = in.readInt(), z = in.readInt();
            boolean seabed = (in.readByte() & 1) != 0;
            short[] clear = new short[5];
            for (int i = 0; i < 5; i++) clear[i] = in.readShort();
            short[] boxes = new short[in.readInt() * 7];
            for (int i = 0; i < boxes.length; i++) boxes[i] = in.readShort();
            into.add(new Monument(id, name, country, lat, lon, x, z, seabed, clear, boxes, palette));
        }
    }
}
