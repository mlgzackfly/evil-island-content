package tw.zack.evilisland.world;

import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

public final class EvilIslandWorldGenerator extends ChunkGenerator {
    public static final int SEA_LEVEL = 62;
    public static final long DEFAULT_SEED = 4290053L;
    private static final int SUI_AN_X = 700;
    private static final int SUI_AN_Z = 0;
    private static final int NEW_CITY_X = 4300;
    private static final int NEW_CITY_Z = 0;
    private static final int MAGIC_ISLAND_X = 3420;
    private static final int MAGIC_ISLAND_Z = 1750;
    private static final int DRAGON_PALACE_X = 2500;
    private static final int DRAGON_PALACE_Z = -1900;
    private final long seed;
    private final int coordinateScale;

    public EvilIslandWorldGenerator(long seed) {
        this(seed, 1);
    }

    public EvilIslandWorldGenerator(long seed, int coordinateScale) {
        this.seed = seed;
        this.coordinateScale = Math.max(1, coordinateScale);
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        int minHeight = chunkData.getMinHeight();
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int x = modelCoordinate(chunkX * 16 + localX);
                int z = modelCoordinate(chunkZ * 16 + localZ);
                generateTerrainColumn(chunkData, localX, localZ, x, z, minHeight);
            }
        }
        applyLandmarks(chunkData, chunkX, chunkZ);
        addVegetation(chunkData, chunkX, chunkZ);
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return true;
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return new EvilIslandBiomeProvider(coordinateScale);
    }

    @Override
    public int getBaseHeight(WorldInfo worldInfo, Random random, int x, int z, HeightMap heightMap) {
        return surfaceHeight(modelCoordinate(x), modelCoordinate(z)) + 1;
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, worldCoordinate(NEW_CITY_X) + coordinateScale / 2.0,
                surfaceHeight(NEW_CITY_X, NEW_CITY_Z) + 2,
                worldCoordinate(NEW_CITY_Z) + coordinateScale / 2.0);
    }

    public static int surfaceHeight(int x, int z) {
        return EvilIslandShape.surfaceHeight(x, z);
    }

    public static boolean isEvilIsland(int x, int z) {
        return EvilIslandShape.isEvilIsland(x, z);
    }

    public static boolean isEastContinent(int x, int z) {
        return EvilIslandShape.isEastContinent(x, z);
    }

    public static boolean isMagicIsland(int x, int z) {
        return EvilIslandShape.isMagicIsland(x, z);
    }

    public static boolean isDragonPalace(int x, int z) {
        return EvilIslandShape.isDragonPalace(x, z);
    }

    public static String canonicalRegion(int x, int z) {
        return EvilIslandShape.canonicalRegion(x, z);
    }

    private void generateTerrainColumn(ChunkData data, int localX, int localZ, int x, int z, int minHeight) {
        int surface = terrainHeight(seed, x, z);
        boolean land = isLand(x, z);
        Material top = topMaterial(x, z);
        Material filler = fillerMaterial(x, z);

        data.setBlock(localX, minHeight, localZ, Material.BEDROCK);
        for (int y = minHeight + 1; y <= surface; y++) {
            Material material;
            if (y == surface) {
                material = top;
            } else if (y >= surface - 4) {
                material = filler;
            } else {
                material = oreOrStone(x, y, z);
            }
            if (land && y > 8 && y < surface - 7 && isNaturalCave(x, y, z)) {
                material = y <= SEA_LEVEL ? Material.CAVE_AIR : Material.AIR;
            }
            data.setBlock(localX, y, localZ, material);
        }
        if (surface < SEA_LEVEL) {
            for (int y = surface + 1; y <= SEA_LEVEL; y++) {
                data.setBlock(localX, y, localZ, Material.WATER);
            }
        }
    }

    private static int terrainHeight(long seed, int x, int z) {
        return EvilIslandShape.terrainHeight(seed, x, z);
    }

    private void applyLandmarks(ChunkData data, int chunkX, int chunkZ) {
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int x = modelCoordinate(chunkX * 16 + localX);
                int z = modelCoordinate(chunkZ * 16 + localZ);
                buildSuiAnColumn(data, localX, localZ, x, z);
                buildJiuhuiColumn(data, localX, localZ, x, z);
                buildMountainPassColumn(data, localX, localZ, x, z);
                buildRongxuColumn(data, localX, localZ, x, z);
                buildNewCityColumn(data, localX, localZ, x, z);
                buildMagicIslandColumn(data, localX, localZ, x, z);
                buildDragonPalaceColumn(data, localX, localZ, x, z);
            }
        }
    }

    private void buildSuiAnColumn(ChunkData data, int lx, int lz, int x, int z) {
        int dx = x - SUI_AN_X;
        int dz = z - SUI_AN_Z;
        double radius = EvilIslandShape.suiAnRadius(x, z);
        if (radius > 1.0) return;
        int ground = 78;

        boolean road = isSuiAnRoad(dx, dz, radius);
        if (road) data.setBlock(lx, ground, lz, radius > 0.88
                ? Material.POLISHED_BLACKSTONE_BRICKS : Material.POLISHED_ANDESITE);

        if (Math.abs(dx) <= 28 && Math.abs(dz) <= 28) {
            buildQingtianTowerColumn(data, lx, lz, dx, dz, ground);
            return;
        }

        if (radius < 0.92 && !road) {
            buildCityHouseColumn(data, lx, lz, x, z, ground, 36);
        }
    }

    private static boolean isSuiAnRoad(int dx, int dz, double radius) {
        if (Math.abs(dx) <= 5 || Math.abs(dz) <= 5) return true;
        double nx = dx / (double) EvilIslandShape.SUI_AN_HALF_X;
        double nz = dz / (double) EvilIslandShape.SUI_AN_HALF_Z;
        if (Math.abs(nx - nz) <= 0.018 || Math.abs(nx + nz) <= 0.018) return true;
        for (double ring : new double[]{0.25, 0.45, 0.68, 0.92}) {
            if (Math.abs(radius - ring) <= (ring == 0.92 ? 0.018 : 0.012)) return true;
        }
        return false;
    }

    private void buildQingtianTowerColumn(ChunkData data, int lx, int lz, int dx, int dz, int ground) {
        int edge = Math.max(Math.abs(dx), Math.abs(dz));
        int top = ground + 118;
        boolean shell = edge >= 25;
        if (shell) {
            for (int y = ground + 1; y <= top; y++) {
                boolean window = y % 12 >= 5 && y % 12 <= 8 && (Math.floorMod(dx + dz, 8) < 4);
                data.setBlock(lx, y, lz, window ? Material.TINTED_GLASS : Material.DEEPSLATE_BRICKS);
            }
        } else {
            for (int y = ground + 1; y < top; y++) {
                data.setBlock(lx, y, lz, y % 16 == 0 ? Material.SMOOTH_STONE : Material.AIR);
            }
        }
        if (edge <= 28) data.setBlock(lx, top, lz, Material.MUD_BRICKS);
        if (edge <= 20) data.setBlock(lx, top + 1, lz, Material.PRISMARINE_BRICKS);
        if (edge <= 10) data.setBlock(lx, top + 2, lz, Material.SEA_LANTERN);
    }

    private void buildCityHouseColumn(ChunkData data, int lx, int lz, int x, int z, int ground, int parcelSize) {
        int parcelX = Math.floorDiv(x, parcelSize);
        int parcelZ = Math.floorDiv(z, parcelSize);
        int inX = Math.floorMod(x, parcelSize);
        int inZ = Math.floorMod(z, parcelSize);
        int margin = 8 + (int) (positiveHash(parcelX, parcelZ, 9) % 3);
        int max = parcelSize - margin;
        if (inX < margin || inX > max || inZ < margin || inZ > max) return;
        int height = 8 + (int) (positiveHash(parcelX, parcelZ, 13) % 8);
        boolean boundary = inX == margin || inX == max || inZ == margin || inZ == max;
        Material wall = positiveHash(parcelX, parcelZ, 17) % 2 == 0 ? Material.STONE_BRICKS : Material.BRICKS;
        for (int y = ground + 1; y <= ground + height; y++) {
            if (boundary) {
                boolean window = y >= ground + 3 && y <= ground + 5 && (x + z) % 5 == 0;
                data.setBlock(lx, y, lz, window ? Material.GLASS_PANE : wall);
            } else {
                data.setBlock(lx, y, lz, Material.AIR);
            }
        }
        data.setBlock(lx, ground + height + 1, lz, Material.DARK_OAK_PLANKS);
    }

    private void buildJiuhuiColumn(ChunkData data, int lx, int lz, int x, int z) {
        if (x >= 1010 && x <= 1390 && Math.abs(z) <= 190) {
            boolean corridor = Math.floorMod(x - 1010, 48) < 9 || Math.floorMod(z + 190, 48) < 9;
            boolean chamber = Math.floorMod(x - 1010, 48) >= 14 && Math.floorMod(x - 1010, 48) <= 40
                    && Math.floorMod(z + 190, 48) >= 14 && Math.floorMod(z + 190, 48) <= 40;
            if (corridor || chamber) {
                data.setBlock(lx, 28, lz, Material.DEEPSLATE_TILES);
                for (int y = 29; y <= 54; y++) data.setBlock(lx, y, lz, Material.AIR);
                data.setBlock(lx, 55, lz, Material.REINFORCED_DEEPSLATE);
                if (corridor && (x + z) % 17 == 0) data.setBlock(lx, 31, lz, Material.SOUL_LANTERN);
            }
        }

        if (x >= 1330 && x <= 1490 && Math.abs(z) <= 7) {
            int tunnelY = 48 + (int) Math.round((x - 1330) * 0.58);
            for (int y = tunnelY; y <= tunnelY + 6; y++) data.setBlock(lx, y, lz, Material.AIR);
            data.setBlock(lx, tunnelY - 1, lz, Material.DEEPSLATE_TILES);
            if (Math.abs(z) == 7) {
                for (int y = tunnelY; y <= tunnelY + 6; y++) data.setBlock(lx, y, lz, Material.DEEPSLATE_BRICKS);
            }
        }
    }

    private void buildMountainPassColumn(ChunkData data, int lx, int lz, int x, int z) {
        int dx = x - 1540;
        int dz = z + 80;
        if (Math.abs(dx) > 58 || Math.abs(dz) > 58) return;
        int ground = EvilIslandShape.mountainPassTerraceHeight(x);
        if (Math.abs(dx) >= 52 || Math.abs(dz) >= 52) {
            for (int y = ground + 1; y <= ground + 6; y++) data.setBlock(lx, y, lz, Material.COBBLED_DEEPSLATE);
            return;
        }
        if (Math.abs(dx) <= 4 || Math.abs(dz) <= 4) {
            data.setBlock(lx, ground, lz, Material.POLISHED_BLACKSTONE_BRICKS);
        } else {
            buildCityHouseColumn(data, lx, lz, x, z, ground, 24);
        }
    }

    private void buildRongxuColumn(ChunkData data, int lx, int lz, int x, int z) {
        if (x < 1308 || x > 1332 || z < 485 || z > 710) return;
        int centerY = 102;
        boolean wall = x <= 1310 || x >= 1330;
        data.setBlock(lx, centerY - 1, lz, Material.SMOOTH_STONE);
        for (int y = centerY; y <= centerY + 8; y++) {
            data.setBlock(lx, y, lz, wall ? Material.IRON_BLOCK : Material.AIR);
        }
        data.setBlock(lx, centerY + 9, lz, Material.SMOOTH_STONE);
        if (!wall && Math.floorMod(z, 14) == 0) {
            data.setBlock(lx, centerY + 6, lz, Material.REDSTONE_LAMP);
        }
        if ((x == 1311 || x == 1329) && Math.floorMod(z, 24) == 0) {
            data.setBlock(lx, centerY + 2, lz, Material.DISPENSER);
        }
    }

    private void buildNewCityColumn(ChunkData data, int lx, int lz, int x, int z) {
        int dx = x - NEW_CITY_X;
        int dz = z - NEW_CITY_Z;
        if (Math.abs(dx) > 215 || Math.abs(dz) > 215) return;
        int ground = 76;
        boolean road = Math.abs(dx) <= 5 || Math.abs(dz) <= 5;
        boolean districtRoad = Math.abs(Math.abs(dx) - 160) <= 3 || Math.abs(Math.abs(dz) - 92) <= 3;
        if (road) data.setBlock(lx, ground, lz, Material.POLISHED_ANDESITE);
        if (districtRoad) data.setBlock(lx, ground, lz, Material.POLISHED_ANDESITE);

        boolean wallLine = Math.abs(dx) >= 178 && Math.abs(dx) <= 181 || Math.abs(dz) >= 178 && Math.abs(dz) <= 181;
        boolean constructionGap = Math.floorMod(x * 3 + z, 41) < 12 || Math.abs(dz) < 13;
        if (wallLine && !constructionGap) {
            for (int y = ground + 1; y <= ground + 9; y++) data.setBlock(lx, y, lz, Material.MUD_BRICKS);
            if (Math.floorMod(x + z, 9) == 0) data.setBlock(lx, ground + 10, lz, Material.SCAFFOLDING);
        }

        if (distanceSquared(x, z, NEW_CITY_X - 12, NEW_CITY_Z) <= 4) {
            data.setBlock(lx, ground + 1, lz, Material.LODESTONE);
        }
        if (distanceSquared(x, z, NEW_CITY_X + 12, NEW_CITY_Z) <= 4) {
            data.setBlock(lx, ground + 1, lz, Material.SMITHING_TABLE);
        }

        if (Math.abs(dx) < 150 && Math.abs(dz) < 150 && !road && !districtRoad
                && !isReservedNewCityParcel(dx, dz)) {
            int campX = Math.floorMod(dx + 150, 34);
            int campZ = Math.floorMod(dz + 150, 34);
            if (campX >= 8 && campX <= 25 && campZ >= 9 && campZ <= 24) {
                int roof = ground + 6;
                boolean edge = campX == 8 || campX == 25 || campZ == 9 || campZ == 24;
                if (edge) {
                    for (int y = ground + 1; y <= roof; y++) data.setBlock(lx, y, lz, Material.WHITE_WOOL);
                } else {
                    for (int y = ground + 1; y < roof; y++) data.setBlock(lx, y, lz, Material.AIR);
                    data.setBlock(lx, roof, lz, Material.RED_WOOL);
                }
            }
        }
    }

    private static boolean isReservedNewCityParcel(int dx, int dz) {
        boolean northDistricts = dz >= -164 && dz <= -101
                && ((dx >= -148 && dx <= -48) || (dx >= 48 && dx <= 148));
        boolean southDistricts = dz >= 101 && dz <= 164
                && ((dx >= -148 && dx <= -48) || (dx >= 48 && dx <= 148));
        boolean commandQuarter = dx >= -34 && dx <= 34 && dz >= -90 && dz <= -42;
        boolean westServices = dx >= -130 && dx <= -27 && dz >= -82 && dz <= 78;
        boolean eastServices = dx >= 28 && dx <= 74 && dz >= -82 && dz <= 74;
        boolean centralPlaza = Math.abs(dx) <= 40 && dz >= -34 && dz <= 38;
        return northDistricts || southDistricts || commandQuarter || westServices || eastServices || centralPlaza;
    }

    private void buildMagicIslandColumn(ChunkData data, int lx, int lz, int x, int z) {
        if (!isMagicIsland(x, z)) return;
        int dx = x - MAGIC_ISLAND_X;
        int dz = z - MAGIC_ISLAND_Z;
        int ground = terrainHeight(seed, x, z);
        int ring = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        if (ring >= 118 && ring <= 122) {
            for (int y = ground + 1; y <= ground + 18; y++) data.setBlock(lx, y, lz, Material.QUARTZ_BRICKS);
        }
        if (Math.abs(dx) <= 18 && Math.abs(dz) <= 18) {
            int edge = Math.max(Math.abs(dx), Math.abs(dz));
            for (int y = ground + 1; y <= ground + 54; y++) {
                data.setBlock(lx, y, lz, edge >= 16 ? Material.PURPUR_BLOCK : Material.AIR);
            }
            data.setBlock(lx, ground + 55, lz, Material.AMETHYST_BLOCK);
        }
        if ((Math.abs(dx - 55) <= 12 && Math.abs(dz) <= 12) || (Math.abs(dx + 55) <= 12 && Math.abs(dz) <= 12)) {
            buildMagicHouse(data, lx, lz, x, z, ground);
        }
    }

    private void buildMagicHouse(ChunkData data, int lx, int lz, int x, int z, int ground) {
        int localX = Math.floorMod(x + 12, 24);
        int localZ = Math.floorMod(z + 12, 24);
        boolean edge = localX == 0 || localX == 23 || localZ == 0 || localZ == 23;
        for (int y = ground + 1; y <= ground + 12; y++) data.setBlock(lx, y, lz, edge ? Material.CALCITE : Material.AIR);
        data.setBlock(lx, ground + 13, lz, Material.PURPUR_SLAB);
    }

    private void buildDragonPalaceColumn(ChunkData data, int lx, int lz, int x, int z) {
        double dx = x - DRAGON_PALACE_X;
        double dz = z - DRAGON_PALACE_Z;
        double radial = Math.sqrt(dx * dx + dz * dz);
        if (radial > 78) return;
        int floor = 27;
        int roof = floor + 8 + (int) Math.round(Math.sqrt(Math.max(0, 78 * 78 - radial * radial)) * 0.42);
        data.setBlock(lx, floor, lz, Material.PRISMARINE_BRICKS);
        for (int y = floor + 1; y < roof; y++) {
            Material material = radial >= 73 ? Material.DARK_PRISMARINE : Material.AIR;
            data.setBlock(lx, y, lz, material);
        }
        data.setBlock(lx, roof, lz, radial % 9 < 1 ? Material.SEA_LANTERN : Material.PRISMARINE);
        if (Math.abs(dx) <= 12 && Math.abs(dz) <= 12) {
            for (int y = floor + 1; y <= floor + 24; y++) {
                boolean edge = Math.abs(dx) >= 10 || Math.abs(dz) >= 10;
                data.setBlock(lx, y, lz, edge ? Material.GOLD_BLOCK : Material.AIR);
            }
        }
    }

    private void addVegetation(ChunkData data, int chunkX, int chunkZ) {
        long value = positiveHash(chunkX, chunkZ, 401);
        int count = (int) (value % 4);
        for (int i = 0; i < count; i++) {
            int lx = 2 + (int) (positiveHash(chunkX, chunkZ, 410 + i) % 12);
            int lz = 2 + (int) (positiveHash(chunkX, chunkZ, 430 + i) % 12);
            int worldX = chunkX * 16 + lx;
            int worldZ = chunkZ * 16 + lz;
            int x = modelCoordinate(worldX);
            int z = modelCoordinate(worldZ);
            if (!isEvilIsland(x, z) || x > 620 || nearBuiltLandmark(x, z) || isBlueYaoRiver(x, z)) continue;
            int ground = terrainHeight(seed, x, z);
            if (data.getType(lx, ground, lz) != Material.GRASS_BLOCK) continue;
            int trunk = 4 + (int) (positiveHash(x, z, 451) % 3);
            for (int y = 1; y <= trunk; y++) data.setBlock(lx, ground + y, lz, Material.DARK_OAK_LOG);
            for (int ox = -2; ox <= 2; ox++) {
                for (int oz = -2; oz <= 2; oz++) {
                    if (lx + ox < 0 || lx + ox > 15 || lz + oz < 0 || lz + oz > 15) continue;
                    if (Math.abs(ox) + Math.abs(oz) <= 3) data.setBlock(lx + ox, ground + trunk + 1, lz + oz, Material.DARK_OAK_LEAVES);
                }
            }
            data.setBlock(lx, ground + trunk + 2, lz, Material.DARK_OAK_LEAVES);
        }
    }

    private Material topMaterial(int x, int z) {
        if (isBlueYaoRiver(x, z)) return Material.GRAVEL;
        if (isEvilIsland(x, z) && x > 620) return Math.abs(z) > 500 ? Material.COARSE_DIRT : Material.STONE;
        if (isMagicIsland(x, z)) return Material.MOSS_BLOCK;
        if (isEastContinent(x, z)) return Material.GRASS_BLOCK;
        if (isEvilIsland(x, z) && x < -1900 && Math.abs(z) > 1100) return Material.MUD;
        return isEvilIsland(x, z) ? Material.GRASS_BLOCK : Material.GRAVEL;
    }

    private Material fillerMaterial(int x, int z) {
        if (isEvilIsland(x, z) && x > 620) return Material.TUFF;
        return isLand(x, z) ? Material.DIRT : Material.STONE;
    }

    private Material oreOrStone(int x, int y, int z) {
        long hash = positiveHash(x + y * 13, z - y * 7, 503);
        if (y < 0) return Material.DEEPSLATE;
        if (y < 20 && hash % 233 == 0) return Material.DIAMOND_ORE;
        if (y < 52 && hash % 83 == 0) return Material.IRON_ORE;
        if (y < 45 && hash % 157 == 0) return Material.GOLD_ORE;
        if (y < 70 && hash % 61 == 0) return Material.COAL_ORE;
        return Material.STONE;
    }

    private boolean isNaturalCave(int x, int y, int z) {
        if (nearBuiltLandmark(x, z)) return false;
        double wave = Math.sin((x + seed % 97) * 0.047)
                + Math.sin((z - seed % 71) * 0.053)
                + Math.sin((y + x * 0.07) * 0.115);
        return wave > 2.72;
    }

    private static boolean isLand(int x, int z) {
        return isEvilIsland(x, z) || isEastContinent(x, z) || isMagicIsland(x, z);
    }

    private static boolean isSuiAnFootprint(int x, int z) {
        return EvilIslandShape.suiAnRadius(x, z) <= 1.18;
    }

    private static boolean isNewCityFootprint(int x, int z) {
        return Math.abs(x - NEW_CITY_X) <= 230 && Math.abs(z - NEW_CITY_Z) <= 230;
    }

    private static boolean isBlueYaoRiver(int x, int z) {
        if (z < -1650 || z > 1150) return false;
        double riverX = 410 + Math.max(0, z) * 0.08 + Math.sin(z / 170.0) * 35;
        double width = z < -350 ? 38 : 24;
        return Math.abs(x - riverX) <= width;
    }

    private static boolean nearBuiltLandmark(int x, int z) {
        return EvilIslandShape.suiAnRadius(x, z) <= 1.18
                || distanceSquared(x, z, 1180, 0) < 430 * 430
                || distanceSquared(x, z, NEW_CITY_X, NEW_CITY_Z) < 270 * 270
                || isMagicIsland(x, z);
    }

    private static double gaussian(int x, int z, int centerX, int centerZ, double radiusX, double radiusZ) {
        double dx = (x - centerX) / radiusX;
        double dz = (z - centerZ) / radiusZ;
        return Math.exp(-(dx * dx + dz * dz) * 2.0);
    }

    private static double fractalNoise(long seed, int x, int z, double scale, int salt) {
        double total = 0;
        double amplitude = 1;
        double weight = 0;
        for (int octave = 0; octave < 4; octave++) {
            total += smoothNoise(seed, x, z, scale, salt + octave * 31) * amplitude;
            weight += amplitude;
            amplitude *= 0.5;
            scale *= 0.5;
        }
        return total / weight;
    }

    private static double smoothNoise(long seed, int x, int z, double scale, int salt) {
        double fx = x / scale;
        double fz = z / scale;
        int x0 = (int) Math.floor(fx);
        int z0 = (int) Math.floor(fz);
        double tx = fade(fx - x0);
        double tz = fade(fz - z0);
        double a = randomUnit(seed, x0, z0, salt);
        double b = randomUnit(seed, x0 + 1, z0, salt);
        double c = randomUnit(seed, x0, z0 + 1, salt);
        double d = randomUnit(seed, x0 + 1, z0 + 1, salt);
        return lerp(lerp(a, b, tx), lerp(c, d, tx), tz);
    }

    private static double randomUnit(long seed, int x, int z, int salt) {
        long hash = positiveHash(x ^ (int) seed, z ^ (int) (seed >>> 32), salt);
        return (hash % 2000001L) / 1000000.0 - 1.0;
    }

    private static long positiveHash(int x, int z, int salt) {
        long value = x * 341873128712L + z * 132897987541L + salt * 42317861L;
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value & Long.MAX_VALUE;
    }

    private static double fade(double value) {
        return value * value * (3.0 - 2.0 * value);
    }

    private static double lerp(double from, double to, double amount) {
        return from + (to - from) * amount;
    }

    private static long distanceSquared(int x, int z, int centerX, int centerZ) {
        long dx = x - centerX;
        long dz = z - centerZ;
        return dx * dx + dz * dz;
    }

    private int modelCoordinate(int worldCoordinate) {
        return Math.floorDiv(worldCoordinate, coordinateScale);
    }

    private int worldCoordinate(int modelCoordinate) {
        return modelCoordinate * coordinateScale;
    }
}
