package tj.earth.worldgen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Builds the real streets and buildings (see Urban) into a chunk as it is first made, after its
 * caves are cut and before its trees and plants go in, so those keep off the roads and roofs.
 *
 * Everything is at the scale of the map. On a 1:5 map a block is 5 m, so a house is a block or two
 * across and one high: its outline and height are right, but there is no room for a doorway.
 * Each column of blocks is decided on its own from the outlines, so a building or road that
 * crosses into the next chunk comes out the same whichever chunk is made first.
 *
 * What goes on a column: a road cuts through a building, a building stands over a footpath or
 * track, and a bridge's deck runs level above whatever is underneath it.
 */
public final class UrbanBuilder {
    /** Real metres from the middle of each kind of road to its edge (the index is the kind in Urban). */
    private static final double[] HALF_WIDTH = {0, 6, 5, 5, 4.5, 4, 3, 2, 3, 1, 1.5, 2.5};
    /** However narrow, a road is at least this many blocks from middle to edge, so it never breaks into dashes. */
    private static final double LEAST_HALF_WIDTH = 0.71;
    /** A bridge stands on a pier this often along its length, in blocks, where it is not over deep water. */
    private static final int PIER_EVERY = 16, DEEPEST_PIER = 8;
    /** A building outline over water deeper than this is left out: the maps disagree there. */
    private static final int DEEPEST_FOUNDATION = 16;
    private static final int NOT_WORKED_OUT = Integer.MIN_VALUE, NO_DECK = Integer.MIN_VALUE;

    private static final BlockState MAIN_ROAD = Blocks.CONCRETE.gray().defaultBlockState(), ROAD = Blocks.CONCRETE.lightGray().defaultBlockState(),
        PAVING = Blocks.STONE_BRICKS.defaultBlockState(), FOOTPATH = Blocks.DIRT_PATH.defaultBlockState(), TRACK = Blocks.COARSE_DIRT.defaultBlockState(),
        RAILWAY = Blocks.COBBLED_DEEPSLATE.defaultBlockState(), FOOTBRIDGE = Blocks.OAK_PLANKS.defaultBlockState(),
        PIER = Blocks.STONE_BRICKS.defaultBlockState(), FOUNDATION = Blocks.STONE.defaultBlockState();

    // What buildings are made of. One style for the whole world so far: which walls and roofs belong to which region is still to come.
    private static final BlockState[] HOUSE_WALLS = states(Blocks.DYED_TERRACOTTA.white(), Blocks.DYED_TERRACOTTA.yellow(), Blocks.DYED_TERRACOTTA.orange(), Blocks.DYED_TERRACOTTA.pink(),
        Blocks.SMOOTH_SANDSTONE, Blocks.TERRACOTTA, Blocks.DYED_TERRACOTTA.lightGray(), Blocks.DYED_TERRACOTTA.white());
    private static final BlockState[] BUSINESS_WALLS = states(Blocks.CONCRETE.white(), Blocks.SMOOTH_STONE, Blocks.DYED_TERRACOTTA.lightGray(), Blocks.SMOOTH_SANDSTONE);
    private static final BlockState[] WORKS_WALLS = states(Blocks.ANDESITE, Blocks.SMOOTH_STONE, Blocks.IRON_BLOCK, Blocks.BRICKS);
    private static final BlockState[] PUBLIC_WALLS = states(Blocks.STONE_BRICKS, Blocks.SMOOTH_SANDSTONE, Blocks.BRICKS, Blocks.QUARTZ_BLOCK);
    private static final BlockState[] FARM_WALLS = states(Blocks.SPRUCE_PLANKS, Blocks.OAK_PLANKS, Blocks.COBBLESTONE, Blocks.BRICKS);
    private static final BlockState[] PITCHED_ROOFS = states(Blocks.BRICK_SLAB, Blocks.BRICK_SLAB, Blocks.GRANITE_SLAB, Blocks.STONE_BRICK_SLAB, Blocks.DEEPSLATE_TILE_SLAB);
    private static final BlockState[] FLAT_ROOFS = states(Blocks.SMOOTH_STONE, Blocks.ANDESITE, Blocks.POLISHED_ANDESITE);

    /** What one square of the map holds that reaches into the chunk being built. */
    private record Near(Urban.Square square, Urban.Building[] buildings, Urban.Road[] roads) {}

    private UrbanBuilder() {}

    private static final Urban.Road[] NO_ROADS = {};
    private static final Urban.Building[] NO_BUILDINGS = {};

    public static void build(final ChunkAccess chunk) {
        EarthConfig.Cities cities = EarthConfig.get().cities;
        if (!cities.enabled || !(cities.roads || cities.buildings)) return;
        int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
        if (!cities.disabledCountries.isEmpty() && EarthConfig.listed(cities.disabledCountries, Countries.atBlock(minX + 8, minZ + 8))) return;
        boolean roads = cities.roads, buildings = cities.buildings;
        // A chunk is much smaller than a square of the map, so its corners find every square it lies over.
        Near[] near = new Near[4];
        int squares = 0;
        for (int corner = 0; corner < 4; corner++) {
            Urban.Square square = Urban.at(minX + (corner & 1) * 15, minZ + (corner >> 1) * 15);
            boolean met = square == Urban.EMPTY;
            for (int i = 0; i < squares; i++) met |= near[i].square == square;
            if (!met) near[squares++] = near(square, minX, minZ);
        }
        if (squares == 0) return;

        EarthConfig c = EarthConfig.get();
        // A block is narrower from east to west on the real ground the further it is from the equator.
        double metresZ = c.horizontalScale, metresX = metresZ * Math.cos(Math.toRadians(c.latitude(minZ + 8)));
        double[] where = new double[1];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int z = minZ; z < minZ + 16; z++) {
            for (int x = minX; x < minX + 16; x++) {
                Urban.Square square = squares == 1 ? near[0].square : Urban.at(x, z);
                Near here = null;
                for (int i = 0; i < squares; i++) if (near[i].square == square) here = near[i];
                if (here == null || here.buildings.length + here.roads.length == 0) continue;

                Terrain.Column column = Terrain.column(x, z);
                boolean wet = column.waterLevel() != Terrain.DRY;
                int free = free(column);
                // Where a road lies: in place of the top block of the ground, or on the water.
                int flat = wet ? free : free - 1;
                double px = x + 0.5, pz = z + 0.5;

                Urban.Road road = null, path = null, bridge = null;
                int deck = NO_DECK;
                double pier = 0;
                for (Urban.Road candidate : roads ? here.roads : NO_ROADS) {
                    double half = Math.max(HALF_WIDTH[candidate.kind], LEAST_HALF_WIDTH * metresZ);
                    if (px < candidate.minX - half / metresX || px > candidate.maxX + half / metresX
                        || pz < candidate.minZ - half / metresZ || pz > candidate.maxZ + half / metresZ) continue;
                    if (candidate.distanceSquared(px, pz, metresX, metresZ, where) > half * half) continue;
                    if (candidate.bridge) {
                        double length = candidate.along[candidate.along.length - 1], t = length == 0 ? 0 : where[0] / length;
                        int level = (int) Math.round(startY(candidate) + t * (endY(candidate) - startY(candidate)));
                        if (wet) level = Math.max(level, free + 1);
                        if (level > flat) {
                            if (level > deck) {
                                deck = level;
                                bridge = candidate;
                                pier = where[0];
                            }
                            continue;
                        }
                    }
                    if (candidate.kind == Urban.FOOTWAY || candidate.kind == Urban.TRACK) {
                        if (path == null) path = candidate;
                    } else if (road == null || candidate.kind < road.kind) {
                        road = candidate;
                    }
                }

                if (road != null) {
                    chunk.setBlockState(pos.set(x, flat, z), surface(road.kind), 2);
                } else {
                    Urban.Building building = null;
                    for (Urban.Building candidate : buildings ? here.buildings : NO_BUILDINGS) {
                        if (candidate.contains(px, pz) && (building == null || height(candidate, metresX, metresZ) > height(building, metresX, metresZ))) building = candidate;
                    }
                    if (building != null && free - column.top() <= DEEPEST_FOUNDATION) {
                        raise(chunk, pos, x, z, column.top(), free, building, metresX, metresZ, deck == NO_DECK ? chunk.getMaxY() : deck - 2);
                    } else if (path != null && !wet) {
                        chunk.setBlockState(pos.set(x, flat, z), surface(path.kind), 2);
                    }
                }

                if (bridge != null) {
                    boolean foot = bridge.kind == Urban.FOOTWAY || bridge.kind == Urban.TRACK;
                    chunk.setBlockState(pos.set(x, deck, z), foot ? FOOTBRIDGE : surface(bridge.kind), 2);
                    if (!foot && road == null && (int) pier % PIER_EVERY == 0 && deck - free >= 2 && free - column.top() <= DEEPEST_PIER) {
                        for (int y = column.top(); y < deck; y++) {
                            if (chunk.getBlockState(pos.set(x, y, z)).isAir() || y < free) chunk.setBlockState(pos, PIER, 2);
                        }
                    }
                }
            }
        }
    }

    /** One column of a building: walls from the ground up to its roof line, then the roof. */
    private static void raise(ChunkAccess chunk, BlockPos.MutableBlockPos pos, int x, int z, int bed, int free, Urban.Building building,
                              double metresX, double metresZ, int highest) {
        double metres = height(building, metresX, metresZ), perBlock = EarthConfig.get().metresPerBlockY;
        boolean pitched = pitched(building, metres, metresX, metresZ);
        // A pitched roof is a half block of its own on top of the walls.
        int walls = Math.max(1, (int) Math.round((pitched ? metres - perBlock / 2 : metres) / perBlock));
        // Every column stands at least one block high, so a building on a slope steps up the hill.
        int top = Math.min(highest, Math.max(free, base(building) + walls - 1));
        if (top < free) return;
        for (int y = bed; y < free; y++) chunk.setBlockState(pos.set(x, y, z), FOUNDATION, 2);
        BlockState wall = pick(walls(building.kind), building.id);
        for (int y = free; y <= top; y++) chunk.setBlockState(pos.set(x, y, z), wall, 2);
        if (pitched) {
            if (top < highest) chunk.setBlockState(pos.set(x, top + 1, z), pick(PITCHED_ROOFS, building.id >>> 8), 2);
        } else if (walls >= 2) {
            chunk.setBlockState(pos.set(x, top, z), pick(FLAT_ROOFS, building.id >>> 8), 2);
        }
    }

    /** A building's real height in metres: as recorded, or from its floors, or a guess from its kind and size. */
    private static double height(Urban.Building building, double metresX, double metresZ) {
        if (building.metres > 0) return building.metres;
        if (building.floors > 0) return building.floors * 3.0 + 1;
        double area = building.area * metresX * metresZ;
        if (area < 40 || building.kind == Urban.OUTBUILDING) return 3;
        return switch (building.kind) {
            case Urban.AGRICULTURAL -> 5;
            case Urban.INDUSTRIAL, Urban.COMMERCIAL, Urban.TRANSPORTATION -> 8;
            case Urban.RELIGIOUS -> 12;
            default -> area > 400 ? 10 : 7;
        };
    }

    private static boolean pitched(Urban.Building building, double metres, double metresX, double metresZ) {
        if (building.roof != Urban.ROOF_UNKNOWN) return building.roof == Urban.ROOF_PITCHED;
        return switch (building.kind) {
            case Urban.COMMERCIAL, Urban.INDUSTRIAL, Urban.TRANSPORTATION, Urban.MILITARY, Urban.SERVICE_BUILDING, Urban.MEDICAL -> false;
            default -> metres < 35 && building.area * metresX * metresZ < 1500;
        };
    }

    private static BlockState[] walls(int kind) {
        return switch (kind) {
            case Urban.COMMERCIAL, Urban.MEDICAL, Urban.ENTERTAINMENT -> BUSINESS_WALLS;
            case Urban.INDUSTRIAL, Urban.TRANSPORTATION, Urban.MILITARY, Urban.SERVICE_BUILDING -> WORKS_WALLS;
            case Urban.CIVIC, Urban.EDUCATION, Urban.RELIGIOUS -> PUBLIC_WALLS;
            case Urban.AGRICULTURAL, Urban.OUTBUILDING -> FARM_WALLS;
            default -> HOUSE_WALLS;
        };
    }

    private static BlockState surface(int kind) {
        return switch (kind) {
            case Urban.MOTORWAY, Urban.TRUNK, Urban.PRIMARY, Urban.SECONDARY -> MAIN_ROAD;
            case Urban.PEDESTRIAN -> PAVING;
            case Urban.FOOTWAY -> FOOTPATH;
            case Urban.TRACK -> TRACK;
            case Urban.RAIL -> RAILWAY;
            default -> ROAD;
        };
    }

    /** The level a building is built up from: the usual height of the ground round its outline. */
    private static int base(Urban.Building building) {
        int base = building.base;
        if (base == NOT_WORKED_OUT) {
            int corners = building.ringEnds[0], step = Math.max(1, corners / 16), count = 0;
            long sum = 0;
            for (int i = 0; i < corners; i += step, count++) sum += free(Terrain.column((int) Math.floor(building.xs[i]), (int) Math.floor(building.zs[i])));
            building.base = base = (int) Math.round((double) sum / count);
        }
        return base;
    }

    private static int startY(Urban.Road bridge) {
        if (bridge.startY == NOT_WORKED_OUT) bridge.startY = roadLevel(bridge.xs[0], bridge.zs[0]);
        return bridge.startY;
    }

    private static int endY(Urban.Road bridge) {
        int last = bridge.xs.length - 1;
        if (bridge.endY == NOT_WORKED_OUT) bridge.endY = roadLevel(bridge.xs[last], bridge.zs[last]);
        return bridge.endY;
    }

    private static int roadLevel(double x, double z) {
        Terrain.Column column = Terrain.column((int) Math.floor(x), (int) Math.floor(z));
        return column.waterLevel() != Terrain.DRY ? free(column) : free(column) - 1;
    }

    /** The first block of a column that is neither ground nor water. */
    private static int free(Terrain.Column column) {
        return column.waterLevel() != Terrain.DRY ? Math.max(column.top(), column.waterLevel()) : column.top();
    }

    private static Near near(Urban.Square square, int minX, int minZ) {
        // Wider than any road, so that nothing reaching the chunk is missed.
        double reach = 8;
        List<Urban.Building> buildings = new ArrayList<>();
        for (Urban.Building b : square.buildings()) if (b.maxX >= minX && b.minX <= minX + 16 && b.maxZ >= minZ && b.minZ <= minZ + 16) buildings.add(b);
        List<Urban.Road> roads = new ArrayList<>();
        for (Urban.Road r : square.roads()) if (r.maxX >= minX - reach && r.minX <= minX + 16 + reach && r.maxZ >= minZ - reach && r.minZ <= minZ + 16 + reach) roads.add(r);
        return new Near(square, buildings.toArray(Urban.Building[]::new), roads.toArray(Urban.Road[]::new));
    }

    private static BlockState pick(BlockState[] from, int number) {
        return from[Math.floorMod(number, from.length)];
    }

    private static BlockState[] states(net.minecraft.world.level.block.Block... blocks) {
        BlockState[] states = new BlockState[blocks.length];
        for (int i = 0; i < blocks.length; i++) states[i] = blocks[i].defaultBlockState();
        return states;
    }
}
