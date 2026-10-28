package tj.earth.worldgen;

import java.util.Arrays;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.CarvingMask;

/**
 * The blocks of one chunk that caves and ravines are cut out of, with one rule added: nothing is
 * cut at or above the bed of a sea, lake or river, nor beside one. The game would let the water in
 * and flood every cave near a coast; here caves stay dry and the water stays where the maps put it.
 */
public final class EarthCarvingMask extends CarvingMask {
    private static final int NOT_WORKED_OUT = Integer.MIN_VALUE;
    /** Water this many blocks to the side counts as being beside a column. */
    private static final int BESIDE = 2;

    private final int minBlockX, minBlockZ;
    /** For each column, the highest Y that may be cut. */
    private final int[] ceilings = new int[256];

    public EarthCarvingMask(final int minY, final int maxY, final ChunkPos chunk) {
        super(minY, maxY);
        this.minBlockX = chunk.getMinBlockX();
        this.minBlockZ = chunk.getMinBlockZ();
        Arrays.fill(ceilings, NOT_WORKED_OUT);
    }

    @Override
    public void carve(final int x, final int y, final int z) {
        int ceiling = ceilings[z << 4 | x];
        if (ceiling == NOT_WORKED_OUT) ceilings[z << 4 | x] = ceiling = ceiling(minBlockX + x, minBlockZ + z);
        if (y <= ceiling) super.carve(x, y, z);
    }

    /** The highest Y that may be cut at a column: below the bed of any water at or beside it, by Underground.WATERTIGHT. */
    public static int ceiling(final int blockX, final int blockZ) {
        int ceiling = Integer.MAX_VALUE;
        for (int dz = -BESIDE; dz <= BESIDE; dz += BESIDE) {
            for (int dx = -BESIDE; dx <= BESIDE; dx += BESIDE) {
                Terrain.Column column = Terrain.column(blockX + dx, blockZ + dz);
                if (column.waterLevel() != Terrain.DRY) ceiling = Math.min(ceiling, column.top() - Underground.WATERTIGHT - 1);
            }
        }
        return ceiling;
    }
}
