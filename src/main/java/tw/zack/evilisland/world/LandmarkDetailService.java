package tw.zack.evilisland.world;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;
import tw.zack.evilisland.EvilIslandPlugin;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

public final class LandmarkDetailService {
    private static final int DETAIL_VERSION = 14;
    private static final int BLOCKS_PER_TICK = 6000;
    private static final int CHUNKS_PER_TICK = 4;
    private static final long CHUNK_LOAD_BUDGET_NANOS = 30_000_000L;

    private record Placement(int x, int y, int z, Material material) {
    }

    private record TerrainColumn(int x, int z, int ground) {
    }

    private record ChunkCoordinate(int x, int z) {
    }

    private final EvilIslandPlugin plugin;
    private final WorldAtlasService atlas;
    private final Deque<Placement> queue = new ArrayDeque<>();
    private final Deque<TerrainColumn> terrainQueue = new ArrayDeque<>();
    private final Deque<ChunkCoordinate> chunkQueue = new ArrayDeque<>();
    private BukkitTask task;
    private int total;
    private int totalChunks;
    private int settleTicks;

    public LandmarkDetailService(EvilIslandPlugin plugin, WorldAtlasService atlas) {
        this.plugin = plugin;
        this.atlas = atlas;
    }

    public void scheduleUpgrade() {
        String versionPath = atlas.worldStatePath("detail-version");
        int currentVersion = plugin.getConfig().getInt(versionPath, 0);
        if (currentVersion >= DETAIL_VERSION) {
            atlas.scheduleLandmarkVerification();
            return;
        }
        if (currentVersion < 1) {
            buildNewCityPlan();
            buildSecondaryLandmarksPlan();
        }
        if (currentVersion < 2) buildPolishPlan();
        if (currentVersion < 3) buildNewCityDistrictPlan();
        if (currentVersion < 4) buildCollisionRepairPlan();
        if (currentVersion < 5) buildMountainPassTerracePlan();
        if (currentVersion < 6) buildMountainPassCrossingRepair();
        if (currentVersion < 7) buildArchitecturalFinishPlan();
        if (currentVersion < 8) recolorExistingNewCityCamps();
        if (currentVersion < 9) buildMagicIslandReconstructionPlan();
        if (currentVersion < 10) buildMapAuditRepairPlan();
        if (currentVersion < 12) buildMountainPassApproachRepair();
        if (currentVersion < 14) buildSuiAnMetropolisPlan();
        prepareChunkQueue();
        total = queue.size() + terrainQueue.size();
        plugin.getLogger().info("Landmark detail upgrade queued: " + total
                + " terrain/build jobs across " + totalChunks + " chunks.");
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::applyBatch, 10L, 1L);
    }

    public boolean isBuilding() {
        return task != null;
    }

    private void applyBatch() {
        World world = atlas.mainWorld();
        if (!chunkQueue.isEmpty()) {
            long deadline = System.nanoTime() + CHUNK_LOAD_BUDGET_NANOS;
            int loaded = 0;
            while (loaded < CHUNKS_PER_TICK && !chunkQueue.isEmpty()) {
                ChunkCoordinate chunk = chunkQueue.removeFirst();
                world.getChunkAt(chunk.x(), chunk.z()).load(true);
                loaded++;
                if (System.nanoTime() >= deadline) break;
            }
            if (chunkQueue.size() % 1000 == 0) {
                plugin.getLogger().info("Landmark chunks prepared: "
                        + (totalChunks - chunkQueue.size()) + "/" + totalChunks + ".");
            }
            return;
        }
        if (settleTicks < 100) {
            settleTicks++;
            return;
        }
        int count = 0;
        while (count < BLOCKS_PER_TICK && !terrainQueue.isEmpty()) {
            TerrainColumn column = terrainQueue.removeFirst();
            int baseX = atlas.worldX(column.x());
            int baseZ = atlas.worldZ(column.z());
            for (int dx = 0; dx < atlas.coordinateScale(); dx++) {
                for (int dz = 0; dz < atlas.coordinateScale(); dz++) {
                    int x = baseX + dx;
                    int z = baseZ + dz;
                    int highest = Math.min(220, world.getHighestBlockYAt(x, z));
                    for (int y = column.ground() + 1; y <= highest; y++) {
                        if (!world.getBlockAt(x, y, z).isPassable()) {
                            world.getBlockAt(x, y, z).setType(Material.AIR, false);
                        }
                        count++;
                    }
                    for (int y = column.ground() - 4; y < column.ground(); y++) {
                        world.getBlockAt(x, y, z).setType(Material.MUD_BRICKS, false);
                        count++;
                    }
                    world.getBlockAt(x, column.ground(), z).setType(Material.PACKED_MUD, false);
                    count++;
                }
            }
        }
        while (count < BLOCKS_PER_TICK && !queue.isEmpty()) {
            Placement placement = queue.removeFirst();
            int baseX = atlas.worldX(placement.x());
            int baseZ = atlas.worldZ(placement.z());
            for (int dx = 0; dx < atlas.coordinateScale(); dx++) {
                for (int dz = 0; dz < atlas.coordinateScale(); dz++) {
                    world.getBlockAt(baseX + dx, placement.y(), baseZ + dz)
                            .setType(placement.material(), false);
                    count++;
                }
            }
        }
        if (!terrainQueue.isEmpty() || !queue.isEmpty()) return;

        task.cancel();
        task = null;
        plugin.getConfig().set(atlas.worldStatePath("detail-version"), DETAIL_VERSION);
        plugin.saveConfig();
        plugin.getLogger().info("Landmark detail upgrade complete: " + total + " block changes applied.");
        atlas.scheduleLandmarkVerification();
    }

    private void prepareChunkQueue() {
        Set<Long> chunks = new HashSet<>();
        for (TerrainColumn column : terrainQueue) {
            addScaledChunks(chunks, column.x(), column.z());
        }
        for (Placement placement : queue) {
            addScaledChunks(chunks, placement.x(), placement.z());
        }
        for (long packed : chunks) {
            chunkQueue.addLast(new ChunkCoordinate((int) (packed >> 32), (int) packed));
        }
        totalChunks = chunkQueue.size();
    }

    private void addScaledChunks(Set<Long> chunks, int modelX, int modelZ) {
        int minX = atlas.worldX(modelX) >> 4;
        int maxX = (atlas.worldX(modelX) + atlas.coordinateScale() - 1) >> 4;
        int minZ = atlas.worldZ(modelZ) >> 4;
        int maxZ = (atlas.worldZ(modelZ) + atlas.coordinateScale() - 1) >> 4;
        for (int chunkX = minX; chunkX <= maxX; chunkX++) {
            for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
                chunks.add(((long) chunkX << 32) | (chunkZ & 0xffffffffL));
            }
        }
    }

    private void buildNewCityPlan() {
        int ground = 76;

        clear(4263, ground + 1, -30, 4337, ground + 12, 34);
        patternedPlaza(4300, 2, ground, 36, 30, Material.POLISHED_ANDESITE, Material.SMOOTH_STONE);
        fountain(4300, 9, ground);

        building(4300, -66, ground, 28, 18, 2,
                Material.MUD_BRICKS, Material.DEEPSLATE_BRICKS, Material.DARK_OAK_SLAB);
        portico(4300, -47, ground, Material.DEEPSLATE_BRICKS);
        interiorHall(4300, -66, ground);

        building(4250, -63, ground, 16, 14, 1,
                Material.WHITE_TERRACOTTA, Material.QUARTZ_BRICKS, Material.SMOOTH_QUARTZ_SLAB);
        clinicInterior(4250, -63, ground);

        building(4350, -63, ground, 18, 14, 1,
                Material.BRICKS, Material.POLISHED_BLACKSTONE_BRICKS, Material.DEEPSLATE_TILE_SLAB);
        barracksInterior(4350, -63, ground);

        building(4352, 54, ground, 19, 15, 1,
                Material.BRICKS, Material.CUT_COPPER, Material.WAXED_CUT_COPPER_SLAB);
        refineryInterior(4352, 54, ground);

        clear(4228, ground + 1, 35, 4272, ground + 12, 76);
        mirrorCourt(4250, 55, ground);

        building(4195, -52, ground, 20, 13, 1,
                Material.STRIPPED_DARK_OAK_WOOD, Material.STONE_BRICKS, Material.DARK_OAK_SLAB);
        warehouseInterior(4195, -52, ground);
        building(4195, 50, ground, 20, 13, 1,
                Material.STRIPPED_DARK_OAK_WOOD, Material.STONE_BRICKS, Material.DARK_OAK_SLAB);
        warehouseInterior(4195, 50, ground);

        newCityWestGate(ground);
        for (int x = 4140; x <= 4260; x += 15) streetLamp(x, -9, ground);
        for (int x = 4140; x <= 4260; x += 15) streetLamp(x, 9, ground);
        for (int z = -24; z <= 30; z += 14) {
            streetLamp(4260, z, ground);
            streetLamp(4340, z, ground);
        }
    }

    private void buildSecondaryLandmarksPlan() {
        jiuhuiAssemblyHall();
        mountainPassInn();
        rongxuControlRoom();
        magicIslandCourt();
        dragonPalaceChamber();
    }

    private void buildPolishPlan() {
        polishQingtianTower();
        polishNewCityCore();
        for (int[] point : new int[][]{{652, -48}, {748, -48}, {652, 48}, {748, 48}}) {
            courtyardTree(point[0], point[1], 78, Material.CHERRY_LEAVES);
        }
        for (int[] point : new int[][]{{4270, -18}, {4330, -18}, {4270, 25}, {4330, 25}}) {
            courtyardTree(point[0], point[1], 76, Material.AZALEA_LEAVES);
        }
    }

    private void buildNewCityDistrictPlan() {
        int ground = 76;

        newCityRoadLoop(ground);
        trainingQuarter(ground);
        constructionQuarter(ground);
        farmQuarter(ground);
        expeditionQuarter(ground);

        for (int x = 4180; x <= 4420; x += 40) {
            streetLamp(x, -98, ground);
            streetLamp(x, 98, ground);
        }
        for (int z = -60; z <= 60; z += 30) {
            streetLamp(4133, z, ground);
            streetLamp(4467, z, ground);
        }
    }

    private void buildCollisionRepairPlan() {
        repairNewCityRoadClearance();
        repairMountainPassTerrain();
        repairMagicIslandTerrain();
        place(700, 198, 0, Material.SEA_LANTERN);
    }

    private void buildMountainPassTerracePlan() {
        int minX = 1482;
        int maxX = 1598;
        int minZ = -138;
        int maxZ = -22;

        for (int x = minX; x <= maxX; x++) {
            int ground = EvilIslandShape.mountainPassTerraceHeight(x);
            for (int z = minZ; z <= maxZ; z++) {
                fill(x, ground - 28, z, x, ground, z, Material.STONE);
                clear(x, ground + 1, z, x, 190, z);
                place(x, ground, z, Material.STONE_BRICKS);
            }
        }

        for (int boundaryX : new int[]{1488, 1512, 1536, 1560, 1584}) {
            int high = EvilIslandShape.mountainPassTerraceHeight(boundaryX - 1);
            int low = EvilIslandShape.mountainPassTerraceHeight(boundaryX);
            fill(boundaryX - 1, low + 1, minZ, boundaryX - 1, high, maxZ, Material.COBBLED_DEEPSLATE);
            for (int z = minZ + 4; z <= maxZ - 4; z += 8) {
                fill(boundaryX - 1, low + 2, z, boundaryX - 1, high - 2, z, Material.POLISHED_BASALT);
            }
        }

        buildMountainPassRoads(minX, maxX, minZ, maxZ);
        buildMountainPassWalls(minX, maxX, minZ, maxZ);
        buildMountainPassBuildings();
    }

    private void buildMountainPassRoads(int minX, int maxX, int minZ, int maxZ) {
        for (int x = minX + 3; x <= maxX - 3; x++) {
            int ground = EvilIslandShape.mountainPassTerraceHeight(x);
            fill(x, ground, -84, x, ground, -76, Material.POLISHED_BLACKSTONE_BRICKS);
            clear(x, ground + 1, -84, x, ground + 8, -76);
        }
        int centerGround = EvilIslandShape.mountainPassTerraceHeight(1540);
        fill(1536, centerGround, minZ + 4, 1544, centerGround, maxZ - 4, Material.POLISHED_BLACKSTONE_BRICKS);
        clear(1536, centerGround + 1, minZ + 4, 1544, centerGround + 10, maxZ - 4);

        for (int boundaryX : new int[]{1488, 1512, 1560, 1584}) {
            terraceStair(boundaryX, -80);
        }
        mountainPassBridgeCrossing();
    }

    private void terraceStair(int boundaryX, int centerZ) {
        int high = EvilIslandShape.mountainPassTerraceHeight(boundaryX - 1);
        int low = EvilIslandShape.mountainPassTerraceHeight(boundaryX);
        int length = high - low;
        for (int step = 0; step <= length; step++) {
            int x = boundaryX - length / 2 + step;
            int y = high - step;
            int base = EvilIslandShape.mountainPassTerraceHeight(x);
            fill(x, Math.min(base, y) - 2, centerZ - 4, x, y, centerZ + 4, Material.STONE_BRICKS);
            fill(x, y, centerZ - 4, x, y, centerZ + 4, Material.POLISHED_BLACKSTONE_BRICKS);
            clear(x, y + 1, centerZ - 4, x, 190, centerZ + 4);
            place(x, y + 1, centerZ - 5, Material.DEEPSLATE_BRICK_WALL);
            place(x, y + 1, centerZ + 5, Material.DEEPSLATE_BRICK_WALL);
        }
    }

    private void buildMountainPassCrossingRepair() {
        mountainPassBridgeCrossing();
    }

    private void buildArchitecturalFinishPlan() {
        polishMountainPassArchitecture();
    }

    private void polishMountainPassArchitecture() {
        polishMountainHouse(1524, -113, 2);
        polishMountainHouse(1497, -113, 2);
        polishMountainHouse(1497, -47, 1);
        polishMountainHouse(1524, -47, 2);
        polishMountainHouse(1551, -113, 2);
        polishMountainHouse(1551, -47, 1);
        polishMountainHouse(1572, -113, 1);
        polishMountainHouse(1572, -47, 2);
        polishMountainHouse(1591, -113, 1);
        polishMountainHouse(1591, -47, 1);

        for (int cx : new int[]{1497, 1524, 1551, 1572, 1591}) {
            int ground = EvilIslandShape.mountainPassTerraceHeight(cx);
            int halfX = mountainHouseHalfX(cx);
            mountainHouseWalkway(cx, ground);
            planter(cx - Math.min(5, halfX - 1), -94, ground);
            planter(cx + Math.min(5, halfX - 1), -66, ground);
        }

        for (int boundaryX : new int[]{1488, 1512, 1536, 1560, 1584}) {
            int high = EvilIslandShape.mountainPassTerraceHeight(boundaryX - 1);
            int low = EvilIslandShape.mountainPassTerraceHeight(boundaryX);
            for (int z = -134; z <= -26; z++) {
                if (z >= -88 && z <= -72) continue;
                place(boundaryX - 1, high + 1, z, Material.DEEPSLATE_BRICK_WALL);
            }
            for (int z = -126; z <= -34; z += 16) {
                fill(boundaryX - 1, low + 8, z - 1,
                        boundaryX - 1, low + 10, z + 1, Material.POLISHED_BLACKSTONE_BRICKS);
                place(boundaryX - 1, low + 9, z, Material.SEA_LANTERN);
            }
        }
        mountainPassBridgeCrossing();
    }

    private void polishMountainHouse(int cx, int cz, int floors) {
        int ground = EvilIslandShape.mountainPassTerraceHeight(cx);
        int halfX = mountainHouseHalfX(cx);
        int halfZ = 9;
        int roofY = ground + floors * 7 + 2;

        steppedRoof(cx, cz, halfX + 1, halfZ + 1, roofY + 1, Material.SPRUCE_SLAB);
        perimeter(cx - halfX - 1, roofY, cz - halfZ - 1,
                cx + halfX + 1, cz + halfZ + 1, Material.DARK_OAK_SLAB);
        fill(cx - halfX + 2, roofY + 2, cz - 1,
                cx + halfX - 2, roofY + 4, cz + 1, Material.DARK_OAK_PLANKS);
        fill(cx - halfX + 2, roofY + 2, cz - 5,
                cx - halfX + 3, roofY + 7, cz - 4, Material.BRICKS);

        clear(cx - 1, ground + 1, cz + halfZ, cx + 1, ground + 3, cz + halfZ + 1);
        fill(cx - 4, ground, cz + halfZ + 1,
                cx + 4, ground, cz + halfZ + 4, Material.POLISHED_ANDESITE);
        fill(cx - 4, ground + 5, cz + halfZ,
                cx + 4, ground + 5, cz + halfZ + 3, Material.DARK_OAK_SLAB);
        for (int x : new int[]{cx - 4, cx + 4}) {
            fill(x, ground + 1, cz + halfZ + 2,
                    x, ground + 4, cz + halfZ + 2, Material.STRIPPED_SPRUCE_LOG);
        }
        place(cx, ground + 5, cz + halfZ + 4, Material.LANTERN);

        if (floors > 1) {
            fill(cx - halfX + 2, ground + 8, cz + halfZ + 1,
                    cx + halfX - 2, ground + 8, cz + halfZ + 3, Material.SPRUCE_PLANKS);
            for (int x = cx - halfX + 2; x <= cx + halfX - 2; x++) {
                place(x, ground + 9, cz + halfZ + 3, Material.DARK_OAK_FENCE);
            }
            place(cx - halfX + 2, ground + 10, cz + halfZ + 2, Material.LANTERN);
            place(cx + halfX - 2, ground + 10, cz + halfZ + 2, Material.LANTERN);
        }
    }

    private int mountainHouseHalfX(int cx) {
        return cx >= 1584 || (cx >= 1536 && cx < 1560) ? 6 : 9;
    }

    private void mountainHouseWalkway(int cx, int ground) {
        fill(cx - 2, ground, -103, cx + 2, ground, -89, Material.POLISHED_ANDESITE);
        fill(cx - 2, ground, -71, cx + 2, ground, -57, Material.POLISHED_ANDESITE);
        for (int z : new int[]{-101, -95, -69, -63}) {
            place(cx - 3, ground + 1, z, Material.LANTERN);
            place(cx + 3, ground + 1, z, Material.LANTERN);
        }
    }

    private void planter(int x, int z, int ground) {
        fill(x - 1, ground, z - 1, x + 1, ground, z + 1, Material.MOSS_BLOCK);
        place(x, ground + 1, z, Material.FLOWERING_AZALEA);
        place(x - 1, ground + 1, z, Material.PINK_TULIP);
        place(x + 1, ground + 1, z, Material.WHITE_TULIP);
    }

    private void recolorExistingNewCityCamps() {
        World world = atlas.mainWorld();
        Material[] accents = {
                Material.RED_WOOL, Material.BROWN_WOOL, Material.GRAY_WOOL,
                Material.LIGHT_GRAY_WOOL, Material.BLUE_WOOL, Material.GREEN_WOOL
        };
        for (int gridX = 0; gridX < 9; gridX++) {
            for (int gridZ = 0; gridZ < 9; gridZ++) {
                int cx = 4300 - 133 + gridX * 34;
                int cz = -133 + gridZ * 34;
                if (crossesNewCityRoad(cx, cz)) continue;
                Material roof = world.getBlockAt(atlas.worldX(cx), 82, atlas.worldZ(cz)).getType();
                if (!roof.name().endsWith("_WOOL")) continue;
                Material accent = accents[Math.floorMod(gridX * 2 + gridZ * 3, accents.length)];
                polishCanvasCamp(cx, cz, accent, positiveHash(gridX, gridZ, 41));
            }
        }
    }

    private boolean crossesNewCityRoad(int cx, int cz) {
        int minX = cx - 9;
        int maxX = cx + 8;
        int minZ = cz - 8;
        int maxZ = cz + 7;
        boolean horizontalLoop = maxZ >= -95 && minZ <= -89 || maxZ >= 89 && minZ <= 95;
        boolean verticalLoop = maxX >= 4137 && minX <= 4143 || maxX >= 4457 && minX <= 4463;
        boolean mainRoad = maxX >= 4295 && minX <= 4305 || maxZ >= -5 && minZ <= 5;
        return horizontalLoop || verticalLoop || mainRoad;
    }

    private void polishCanvasCamp(int cx, int cz, Material accent, long variation) {
        int minX = cx - 9;
        int maxX = cx + 8;
        int minZ = cz - 8;
        int maxZ = cz + 7;

        perimeter(minX, 82, minZ, maxX, maxZ, accent);
        fill(minX + 2, 83, cz - 1, maxX - 2, 84, cz + 1, accent);
        for (int x = minX + 3; x <= maxX - 3; x += 5) {
            fill(x, 82, minZ + 1, x + 1, 82, maxZ - 1, accent);
        }

        clear(cx - 1, 77, maxZ, cx + 1, 80, maxZ + 1);
        fill(cx - 4, 81, maxZ, cx + 4, 81, maxZ + 3, accent);
        place(cx - 4, 78, maxZ + 2, Material.SPRUCE_FENCE);
        place(cx + 4, 78, maxZ + 2, Material.SPRUCE_FENCE);
        place(cx, 81, maxZ + 4, Material.LANTERN);
        place(cx + 5, 77, maxZ + 2, Material.BARREL);
        place(cx - 5, 77, maxZ + 2, Material.CRAFTING_TABLE);

        if (variation % 2 == 0) {
            fill(maxX + 1, 80, cz - 4, maxX + 4, 80, cz + 4, Material.WHITE_WOOL);
            fill(maxX + 3, 77, cz - 3, maxX + 3, 79, cz - 3, Material.SPRUCE_FENCE);
            fill(maxX + 3, 77, cz + 3, maxX + 3, 79, cz + 3, Material.SPRUCE_FENCE);
            place(maxX + 2, 77, cz, Material.BARREL);
        } else {
            place(minX - 2, 77, cz - 2, Material.HAY_BLOCK);
            place(minX - 2, 78, cz - 2, Material.LANTERN);
            fill(minX - 3, 77, cz + 2, minX - 1, 78, cz + 4, Material.BARREL);
        }
    }

    private void mountainPassBridgeCrossing() {
        int centerZ = -80;
        int low = EvilIslandShape.mountainPassTerraceHeight(1536);
        for (int x = 1527; x <= 1553; x++) {
            int pathY;
            if (x <= 1535) pathY = 117 - (x - 1527);
            else if (x <= 1544) pathY = 108;
            else pathY = 108 - (x - 1544);

            fill(x, low - 2, centerZ - 4, x, pathY, centerZ + 4, Material.STONE_BRICKS);
            fill(x, pathY, centerZ - 4, x, pathY, centerZ + 4, Material.POLISHED_BLACKSTONE_BRICKS);
            clear(x, pathY + 1, centerZ - 4, x, 190, centerZ + 4);
            place(x, pathY + 1, centerZ - 5, Material.DEEPSLATE_BRICK_WALL);
            place(x, pathY + 1, centerZ + 5, Material.DEEPSLATE_BRICK_WALL);
        }

        clear(1536, low + 1, centerZ - 3, 1544, low + 8, centerZ + 3);
        fill(1536, low, centerZ - 4, 1544, low, centerZ + 4, Material.POLISHED_BLACKSTONE_BRICKS);
        for (int x : new int[]{1536, 1544}) {
            fill(x, low + 1, centerZ - 4, x, low + 8, centerZ - 4, Material.POLISHED_DEEPSLATE);
            fill(x, low + 1, centerZ + 4, x, low + 8, centerZ + 4, Material.POLISHED_DEEPSLATE);
        }
        for (int z = centerZ - 2; z <= centerZ + 2; z += 2) {
            place(1540, low + 9, z, Material.SEA_LANTERN);
        }
    }

    private void buildMountainPassWalls(int minX, int maxX, int minZ, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            int ground = EvilIslandShape.mountainPassTerraceHeight(x);
            fill(x, ground + 1, minZ, x, ground + 6, minZ, Material.COBBLED_DEEPSLATE);
            fill(x, ground + 1, maxZ, x, ground + 6, maxZ, Material.COBBLED_DEEPSLATE);
            if (Math.floorMod(x, 5) < 3) {
                place(x, ground + 7, minZ, Material.DEEPSLATE_BRICK_WALL);
                place(x, ground + 7, maxZ, Material.DEEPSLATE_BRICK_WALL);
            }
        }
        for (int z = minZ; z <= maxZ; z++) {
            int westGround = EvilIslandShape.mountainPassTerraceHeight(minX);
            int eastGround = EvilIslandShape.mountainPassTerraceHeight(maxX);
            fill(minX, westGround + 1, z, minX, westGround + 6, z, Material.COBBLED_DEEPSLATE);
            fill(maxX, eastGround + 1, z, maxX, eastGround + 6, z, Material.COBBLED_DEEPSLATE);
        }
        clear(1536, EvilIslandShape.mountainPassTerraceHeight(1540) + 1, minZ,
                1544, EvilIslandShape.mountainPassTerraceHeight(1540) + 8, minZ);
        clear(1536, EvilIslandShape.mountainPassTerraceHeight(1540) + 1, maxZ,
                1544, EvilIslandShape.mountainPassTerraceHeight(1540) + 8, maxZ);
    }

    private void buildMountainPassBuildings() {
        mountainPassInn();
        mountainHouse(1497, -113, 2, Material.MUD_BRICKS, Material.SPRUCE_LOG);
        mountainHouse(1497, -47, 1, Material.STONE_BRICKS, Material.DARK_OAK_LOG);
        mountainHouse(1524, -47, 2, Material.BRICKS, Material.SPRUCE_LOG);
        mountainHouse(1551, -113, 2, Material.COBBLED_DEEPSLATE, Material.DARK_OAK_LOG);
        mountainHouse(1551, -47, 1, Material.MUD_BRICKS, Material.SPRUCE_LOG);
        mountainHouse(1572, -113, 1, Material.STONE_BRICKS, Material.DARK_OAK_LOG);
        mountainHouse(1572, -47, 2, Material.BRICKS, Material.SPRUCE_LOG);
        mountainHouse(1591, -113, 1, Material.COBBLED_DEEPSLATE, Material.DARK_OAK_LOG);
        mountainHouse(1591, -47, 1, Material.MUD_BRICKS, Material.SPRUCE_LOG);

        for (int x : new int[]{1497, 1524, 1551, 1572, 1591}) {
            int ground = EvilIslandShape.mountainPassTerraceHeight(x);
            streetLamp(x, -89, ground);
            streetLamp(x, -71, ground);
        }
        int centerGround = EvilIslandShape.mountainPassTerraceHeight(1540);
        for (int z = -125; z <= -35; z += 18) streetLamp(1548, z, centerGround);
    }

    private void mountainHouse(int cx, int cz, int floors, Material wall, Material trim) {
        int ground = EvilIslandShape.mountainPassTerraceHeight(cx);
        int halfX = cx >= 1584 || (cx >= 1536 && cx < 1560) ? 6 : 9;
        building(cx, cz, ground, halfX, 9, floors, wall, trim, Material.DARK_OAK_SLAB);
        fill(cx - halfX + 2, ground + 1, cz - 5, cx + halfX - 2, ground + 1, cz - 2, Material.SPRUCE_PLANKS);
        place(cx, ground + 2, cz - 3, Material.CAMPFIRE);
        place(cx - 3, ground + 1, cz + 4, Material.BARREL);
        place(cx + 3, ground + 1, cz + 4, Material.CRAFTING_TABLE);
    }

    private void repairNewCityRoadClearance() {
        int ground = 76;
        fill(4197, ground, -88, 4203, ground, 88, Material.GRASS_BLOCK);
        fill(4397, ground, -88, 4403, ground, 88, Material.GRASS_BLOCK);
        fill(4197, ground, -5, 4203, ground, 5, Material.POLISHED_ANDESITE);
        fill(4397, ground, -5, 4403, ground, 5, Material.POLISHED_ANDESITE);
        for (int z = -62; z <= 62; z += 31) {
            clear(4200, ground + 1, z, 4200, ground + 6, z);
            clear(4400, ground + 1, z, 4400, ground + 6, z);
        }

        clearRoad(4137, -92, 4463, -92, ground, 3);
        clearRoad(4137, 92, 4463, 92, ground, 3);
        clearRoad(4140, -95, 4140, 95, ground, 3);
        clearRoad(4460, -95, 4460, 95, ground, 3);

        clearRoad(4200, -101, 4200, -92, ground, 3);
        clearRoad(4400, -101, 4400, -92, ground, 3);
        clearRoad(4200, 92, 4200, 101, ground, 3);
        clearRoad(4400, 92, 4400, 101, ground, 3);

        newCityRoadLoop(ground);
        building(4195, -52, ground, 20, 13, 1,
                Material.STRIPPED_DARK_OAK_WOOD, Material.STONE_BRICKS, Material.DARK_OAK_SLAB);
        warehouseInterior(4195, -52, ground);
        building(4195, 50, ground, 20, 13, 1,
                Material.STRIPPED_DARK_OAK_WOOD, Material.STONE_BRICKS, Material.DARK_OAK_SLAB);
        warehouseInterior(4195, 50, ground);
        steppedRoof(4195, -52, 21, 14, 88, Material.DARK_OAK_SLAB);
        steppedRoof(4195, 50, 21, 14, 88, Material.DARK_OAK_SLAB);

        for (int x = 4180; x <= 4420; x += 40) {
            streetLamp(x, -98, ground);
            streetLamp(x, 98, ground);
        }
        for (int z = -60; z <= 60; z += 30) {
            streetLamp(4133, z, ground);
            streetLamp(4467, z, ground);
        }
    }

    private void repairMountainPassTerrain() {
        int ground = EvilIslandShape.surfaceHeight(1518, -115);
        prepareLevelSite(1498, -130, 1538, -100, ground, Material.STONE_BRICKS);
        mountainPassInn();
        steppedPath(1518, -99, ground, 1540, -80,
                EvilIslandShape.surfaceHeight(1540, -80), Material.POLISHED_BLACKSTONE_BRICKS, 2);
    }

    private void repairMagicIslandTerrain() {
        int ground = EvilIslandShape.surfaceHeight(3420, 1750);
        prepareLevelSite(3374, 1704, 3466, 1796, ground, Material.QUARTZ_BLOCK);
        magicIslandCourt();
    }

    private void buildMagicIslandReconstructionPlan() {
        int towerX = 3420;
        int towerZ = 1750;
        int towerGround = EvilIslandShape.surfaceHeight(towerX, towerZ);

        clear(towerX - 20, towerGround + 1, towerZ - 20,
                towerX + 20, towerGround + 66, towerZ + 20);
        clearAboveTerrain(3353, 1737, 3373, 1763, 140);
        clearAboveTerrain(3467, 1737, 3487, 1763, 140);

        int westX = 3352;
        int eastX = 3488;
        int westGround = EvilIslandShape.surfaceHeight(westX, towerZ);
        int eastGround = EvilIslandShape.surfaceHeight(eastX, towerZ);
        prepareLevelSite(westX - 13, towerZ - 13, westX + 13, towerZ + 13,
                westGround, Material.SMOOTH_QUARTZ);
        prepareLevelSite(eastX - 13, towerZ - 13, eastX + 13, towerZ + 13,
                eastGround, Material.SMOOTH_QUARTZ);

        magicIslandCourt();
        magicIslandTower(towerX, towerZ, towerGround);
        magicIslandPavilion(westX, towerZ, westGround);
        magicIslandPavilion(eastX, towerZ, eastGround);

        steppedPath(westX + 11, towerZ, westGround, towerX - 42, towerZ, towerGround,
                Material.PURPUR_BLOCK, 2);
        steppedPath(towerX + 42, towerZ, towerGround, eastX - 11, towerZ, eastGround,
                Material.PURPUR_BLOCK, 2);
    }

    private void clearAboveTerrain(int x1, int z1, int x2, int z2, int maxY) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                clear(x, EvilIslandShape.surfaceHeight(x, z) + 1, z, x, maxY, z);
            }
        }
    }

    private void buildMapAuditRepairPlan() {
        clear(4133, 77, 0, 4133, 84, 0);

        int towerX = 3420;
        int towerZ = 1750;
        int towerGround = EvilIslandShape.surfaceHeight(towerX, towerZ);
        int westX = 3352;
        int eastX = 3488;
        steppedPath(westX + 11, towerZ, EvilIslandShape.surfaceHeight(westX, towerZ),
                towerX - 42, towerZ, towerGround, Material.PURPUR_BLOCK, 2);
        steppedPath(towerX + 42, towerZ, towerGround,
                eastX - 11, towerZ, EvilIslandShape.surfaceHeight(eastX, towerZ),
                Material.PURPUR_BLOCK, 2);

    }

    private void buildMountainPassApproachRepair() {
        for (int x = 1528; x <= 1535; x++) {
            int ground = EvilIslandShape.mountainPassTerraceHeight(x);
            for (int z = -93; z <= -83; z++) {
                fill(x, ground - 8, z, x, ground - 1, z, Material.STONE);
                place(x, ground, z, Material.STONE_BRICKS);
                clear(x, ground + 1, z, x, 150, z);
            }
        }
        steppedPath(1518, -99, EvilIslandShape.surfaceHeight(1518, -115),
                1527, -85, 117, Material.POLISHED_BLACKSTONE_BRICKS, 2);
        mountainPassBridgeCrossing();
        clear(1526, 118, -85, 1528, 122, -85);
    }

    private void buildSuiAnMetropolisPlan() {
        int ground = 78;
        for (int x = EvilIslandShape.SUI_AN_X - 292; x <= EvilIslandShape.SUI_AN_X + 292; x++) {
            for (int z = EvilIslandShape.SUI_AN_Z - 464; z <= EvilIslandShape.SUI_AN_Z + 464; z++) {
                double radius = EvilIslandShape.suiAnRadius(x, z);
                if (radius > 1.08) continue;
                int target = EvilIslandShape.surfaceHeight(x, z);
                if (target > EvilIslandWorldGenerator.SEA_LEVEL + 4) {
                    terrainQueue.addLast(new TerrainColumn(x, z, target));
                }
            }
        }

        for (int x = EvilIslandShape.SUI_AN_X - EvilIslandShape.SUI_AN_HALF_X;
             x <= EvilIslandShape.SUI_AN_X + EvilIslandShape.SUI_AN_HALF_X; x++) {
            for (int z = EvilIslandShape.SUI_AN_Z - EvilIslandShape.SUI_AN_HALF_Z;
                 z <= EvilIslandShape.SUI_AN_Z + EvilIslandShape.SUI_AN_HALF_Z; z++) {
                double radius = EvilIslandShape.suiAnRadius(x, z);
                if (radius > 1.0) continue;
                if (isSuiAnMetropolisRoad(x - EvilIslandShape.SUI_AN_X,
                        z - EvilIslandShape.SUI_AN_Z, radius)) {
                    place(x, ground, z, radius > 0.89
                            ? Material.POLISHED_BLACKSTONE_BRICKS : Material.POLISHED_ANDESITE);
                }
            }
        }

        int row = 0;
        for (int z = -400; z <= 400; z += 20, row++) {
            for (int x = 436 + (row % 2) * 10; x <= 964; x += 20) {
                if (EvilIslandShape.suiAnRadius(x, z) >= 0.88) continue;
                if (isSuiAnReservedDistrict(x, z) || suiAnParcelTouchesRoad(x, z, 9, 7)) continue;
                long variation = positiveHash(x, z, 131);
                suiAnUrbanHouse(x, z, ground, 3 + (int) (variation % 2), variation);
            }
        }

        patternedPlaza(700, 0, ground, 64, 64, Material.STONE_BRICKS, Material.POLISHED_ANDESITE);
        rebuildQingtianTowerForMetropolis(ground);
        suiAnCommandHeadquarters(555, -43, ground);
        market(555, 105, ground);
        building(820, 112, ground, 24, 18, 3,
                Material.WHITE_TERRACOTTA, Material.QUARTZ_BRICKS, Material.SMOOTH_QUARTZ_SLAB);
        clinicInterior(820, 112, ground);
        building(820, -128, ground, 25, 19, 3,
                Material.STONE_BRICKS, Material.CUT_COPPER, Material.WAXED_CUT_COPPER_SLAB);
        interiorHall(820, -128, ground);
        building(548, -157, ground, 26, 18, 2,
                Material.BRICKS, Material.POLISHED_BLACKSTONE_BRICKS, Material.DEEPSLATE_TILE_SLAB);
        barracksInterior(548, -157, ground);

        for (int distance = -390; distance <= 390; distance += 30) {
            if (Math.abs(distance) < 72) continue;
            streetLamp(706, distance, ground);
        }
        for (int distance = -240; distance <= 240; distance += 30) {
            if (Math.abs(distance) < 72) continue;
            streetLamp(700 + distance, 7, ground);
        }
        stageSuiAnDefenseSupplies(ground);
    }

    private boolean isSuiAnMetropolisRoad(int dx, int dz, double radius) {
        if (Math.abs(dx) <= 6 || Math.abs(dz) <= 6) return true;
        double nx = dx / (double) EvilIslandShape.SUI_AN_HALF_X;
        double nz = dz / (double) EvilIslandShape.SUI_AN_HALF_Z;
        if (Math.abs(nx - nz) <= 0.020 || Math.abs(nx + nz) <= 0.020) return true;
        for (double ring : new double[]{0.25, 0.45, 0.68, 0.92}) {
            double width = ring == 0.92 ? 0.022 : 0.015;
            if (Math.abs(radius - ring) <= width) return true;
        }
        return false;
    }

    private boolean suiAnParcelTouchesRoad(int cx, int cz, int halfX, int halfZ) {
        for (int x = cx - halfX - 2; x <= cx + halfX + 2; x++) {
            for (int z = cz - halfZ - 2; z <= cz + halfZ + 2; z++) {
                double radius = EvilIslandShape.suiAnRadius(x, z);
                if (isSuiAnMetropolisRoad(x - EvilIslandShape.SUI_AN_X,
                        z - EvilIslandShape.SUI_AN_Z, radius)) return true;
            }
        }
        return false;
    }

    private boolean isSuiAnReservedDistrict(int x, int z) {
        if (Math.abs(x - 700) <= 76 && Math.abs(z) <= 76) return true;
        if (x >= 518 && x <= 592 && z >= -82 && z <= -18) return true;
        if (x >= 512 && x <= 598 && z >= 70 && z <= 138) return true;
        if (x >= 788 && x <= 852 && z >= 82 && z <= 142) return true;
        if (x >= 785 && x <= 855 && z >= -160 && z <= -96) return true;
        return x >= 510 && x <= 586 && z >= -185 && z <= -128;
    }

    private void suiAnUrbanHouse(int cx, int cz, int ground, int floors, long variation) {
        int halfX = 8 + (int) (variation % 2);
        int halfZ = 7;
        Material[] walls = {
                Material.BRICKS, Material.STONE_BRICKS, Material.MUD_BRICKS,
                Material.LIGHT_GRAY_TERRACOTTA, Material.TERRACOTTA
        };
        Material wall = walls[(int) (variation % walls.length)];
        Material trim = variation % 3 == 0 ? Material.STRIPPED_DARK_OAK_WOOD : Material.SPRUCE_LOG;
        for (int floor = 0; floor < floors; floor++) {
            int base = ground + floor * 7;
            fill(cx - halfX, base, cz - halfZ, cx + halfX, base, cz + halfZ, Material.SPRUCE_PLANKS);
            for (int y = base + 1; y <= base + 6; y++) {
                for (int x = cx - halfX; x <= cx + halfX; x++) {
                    place(x, y, cz - halfZ, facadeMaterial(x - cx, y - base, wall, trim));
                    place(x, y, cz + halfZ, facadeMaterial(x - cx, y - base, wall, trim));
                }
                for (int z = cz - halfZ + 1; z < cz + halfZ; z++) {
                    place(cx - halfX, y, z, facadeMaterial(z - cz, y - base, wall, trim));
                    place(cx + halfX, y, z, facadeMaterial(z - cz, y - base, wall, trim));
                }
            }
        }
        clear(cx - 2, ground + 1, cz + halfZ, cx + 2, ground + 4, cz + halfZ + 1);
        int roofY = ground + floors * 7;
        fill(cx - halfX - 1, roofY, cz - halfZ - 1,
                cx + halfX + 1, roofY, cz + halfZ + 1, Material.DARK_OAK_SLAB);
        fill(cx - halfX + 2, roofY + 1, cz - halfZ + 2,
                cx + halfX - 2, roofY + 1, cz + halfZ - 2, Material.SPRUCE_SLAB);
        fill(cx - halfX + 2, roofY + 2, cz - 3,
                cx - halfX + 3, roofY + 6, cz - 2, Material.BRICKS);
        place(cx, ground + 2, cz - 4, variation % 4 == 0 ? Material.CRAFTING_TABLE : Material.BARREL);
    }

    private void rebuildQingtianTowerForMetropolis(int ground) {
        for (int y = ground + 1; y <= ground + 118; y++) {
            for (int offset = -28; offset <= 28; offset++) {
                Material facade = qingtianFacade(offset, y - ground);
                place(700 + offset, y, -28, facade);
                place(700 + offset, y, 28, facade);
                place(672, y, offset, facade);
                place(728, y, offset, facade);
            }
        }
        for (int floorY = ground; floorY <= ground + 112; floorY += 16) {
            fill(675, floorY, -24, 725, floorY, 24, Material.SMOOTH_STONE);
            if (floorY > ground) clear(698, floorY, -1, 702, floorY, 1);
            fill(700, floorY + 1, 0, 700, floorY + 15, 0, Material.SCAFFOLDING);
            for (int[] lamp : new int[][]{{-20, -20}, {-20, 20}, {20, -20}, {20, 20}}) {
                place(700 + lamp[0], floorY + 1, lamp[1], Material.SEA_LANTERN);
            }
        }
        clear(696, ground + 1, 28, 704, ground + 9, 30);
        fill(692, ground + 10, 27, 708, ground + 11, 31, Material.DEEPSLATE_TILE_SLAB);
        polishQingtianTower();
        place(700, 208, 0, Material.SEA_LANTERN);
    }

    private Material qingtianFacade(int offset, int localY) {
        if (Math.abs(offset) >= 24 || localY % 16 == 0) return Material.POLISHED_BASALT;
        if (localY % 16 >= 5 && localY % 16 <= 8 && Math.floorMod(offset, 8) < 4) {
            return Material.TINTED_GLASS;
        }
        return Material.DEEPSLATE_BRICKS;
    }

    private void suiAnCommandHeadquarters(int cx, int cz, int ground) {
        building(cx, cz, ground, 30, 18, 2,
                Material.MUD_BRICKS, Material.POLISHED_BLACKSTONE_BRICKS, Material.DEEPSLATE_TILE_SLAB);
        interiorHall(cx, cz, ground);
        fill(cx - 31, ground + 1, cz - 9, cx - 31, ground + 13, cz + 9, Material.MUD_BRICKS);
        clear(cx - 31, ground + 1, cz - 3, cx - 30, ground + 7, cz + 3);
        fill(cx - 34, ground, cz - 12, cx - 32, ground + 16, cz + 12, Material.MUD_BRICKS);
    }

    private void stageSuiAnDefenseSupplies(int ground) {
        for (int angle = 0; angle < 96; angle++) {
            if (angle % 8 < 3) continue;
            double radians = angle * Math.PI * 2.0 / 96.0;
            int x = 700 + (int) Math.round(Math.cos(radians) * 188);
            int z = (int) Math.round(Math.sin(radians) * 300);
            if (Math.abs(x - 700) <= 9 || Math.abs(z) <= 9) continue;
            place(x, ground + 1, z, Material.PACKED_MUD);
            if (angle % 3 == 0) place(x, ground + 2, z, Material.MUD_BRICK_WALL);
        }
    }

    private void magicIslandTower(int cx, int cz, int ground) {
        fill(cx - 18, ground, cz - 18, cx + 18, ground, cz + 18, Material.SMOOTH_QUARTZ);
        for (int localY = 1; localY <= 54; localY++) {
            int y = ground + localY;
            for (int offset = -18; offset <= 18; offset++) {
                Material facade = magicTowerFacade(offset, localY);
                place(cx + offset, y, cz - 18, facade);
                place(cx + offset, y, cz + 18, facade);
                place(cx - 18, y, cz + offset, facade);
                place(cx + 18, y, cz + offset, facade);
            }
        }

        for (int level = 1; level <= 3; level++) {
            int floorY = ground + level * 13;
            fill(cx - 16, floorY, cz - 16, cx + 16, floorY, cz + 16, Material.PURPUR_BLOCK);
            clear(cx - 1, floorY, cz - 1, cx + 1, floorY, cz + 1);
            perimeter(cx - 19, floorY + 1, cz - 19, cx + 19, cz + 19, Material.PURPUR_SLAB);
            for (int[] lamp : new int[][]{{-13, -13}, {-13, 13}, {13, -13}, {13, 13}}) {
                place(cx + lamp[0], floorY + 1, cz + lamp[1], Material.SEA_LANTERN);
            }
        }

        clear(cx - 2, ground + 1, cz + 18, cx + 2, ground + 6, cz + 19);
        fill(cx - 5, ground + 7, cz + 18, cx + 5, ground + 8, cz + 20, Material.QUARTZ_BRICKS);
        fill(cx - 5, ground + 1, cz + 20, cx - 5, ground + 6, cz + 20, Material.PURPUR_PILLAR);
        fill(cx + 5, ground + 1, cz + 20, cx + 5, ground + 6, cz + 20, Material.PURPUR_PILLAR);
        place(cx - 5, ground + 7, cz + 21, Material.END_ROD);
        place(cx + 5, ground + 7, cz + 21, Material.END_ROD);

        fill(cx, ground + 1, cz, cx, ground + 52, cz, Material.SCAFFOLDING);
        for (int level = 0; level < 4; level++) {
            int base = ground + level * 13;
            fill(cx - 7, base + 1, cz - 8, cx + 7, base + 1, cz - 6, Material.SMOOTH_QUARTZ);
            place(cx - 5, base + 2, cz - 7, Material.BREWING_STAND);
            place(cx + 5, base + 2, cz - 7, Material.ENCHANTING_TABLE);
            fill(cx - 12, base + 1, cz + 9, cx - 9, base + 3, cz + 11, Material.BOOKSHELF);
            fill(cx + 9, base + 1, cz + 9, cx + 12, base + 3, cz + 11, Material.BOOKSHELF);
        }

        fill(cx - 19, ground + 55, cz - 19, cx + 19, ground + 55, cz + 19, Material.PURPUR_SLAB);
        fill(cx - 14, ground + 55, cz - 14, cx + 14, ground + 55, cz + 14, Material.AMETHYST_BLOCK);
        fill(cx - 9, ground + 56, cz - 9, cx + 9, ground + 56, cz + 9, Material.PURPUR_BLOCK);
        fill(cx - 4, ground + 57, cz - 4, cx + 4, ground + 57, cz + 4, Material.AMETHYST_BLOCK);
        fill(cx - 1, ground + 58, cz - 1, cx + 1, ground + 63, cz + 1, Material.PURPUR_PILLAR);
        place(cx, ground + 64, cz, Material.SEA_LANTERN);
        place(cx, ground + 65, cz, Material.END_ROD);
    }

    private Material magicTowerFacade(int offset, int localY) {
        if (localY % 13 == 0 || localY % 13 == 1) return Material.QUARTZ_BRICKS;
        if (Math.abs(offset) >= 16 || Math.floorMod(offset, 9) == 0) return Material.PURPUR_PILLAR;
        int windowY = Math.floorMod(localY - 1, 13);
        if (windowY >= 4 && windowY <= 8 && Math.floorMod(offset + 18, 9) >= 3
                && Math.floorMod(offset + 18, 9) <= 6) {
            return Material.PURPLE_STAINED_GLASS;
        }
        return Material.PURPUR_BLOCK;
    }

    private void magicIslandPavilion(int cx, int cz, int ground) {
        building(cx, cz, ground, 10, 9, 2,
                Material.CALCITE, Material.QUARTZ_BRICKS, Material.PURPUR_SLAB);
        fill(cx - 7, ground + 1, cz - 5, cx + 7, ground + 1, cz + 5, Material.SMOOTH_QUARTZ);
        fill(cx - 7, ground + 1, cz - 6, cx - 5, ground + 3, cz - 4, Material.BOOKSHELF);
        fill(cx + 5, ground + 1, cz - 6, cx + 7, ground + 3, cz - 4, Material.BOOKSHELF);
        place(cx - 3, ground + 2, cz - 5, Material.BREWING_STAND);
        place(cx + 3, ground + 2, cz - 5, Material.ENCHANTING_TABLE);
        fill(cx - 11, ground + 1, cz + 10, cx - 11, ground + 5, cz + 10, Material.PURPUR_PILLAR);
        fill(cx + 11, ground + 1, cz + 10, cx + 11, ground + 5, cz + 10, Material.PURPUR_PILLAR);
        fill(cx - 12, ground + 6, cz + 9, cx + 12, ground + 6, cz + 11, Material.PURPUR_SLAB);
        place(cx - 11, ground + 6, cz + 12, Material.END_ROD);
        place(cx + 11, ground + 6, cz + 12, Material.END_ROD);
        fill(cx - 8, ground + 18, cz - 7, cx + 8, ground + 18, cz + 7, Material.PURPUR_SLAB);
        fill(cx - 4, ground + 19, cz - 3, cx + 4, ground + 19, cz + 3, Material.AMETHYST_BLOCK);
        place(cx, ground + 20, cz, Material.END_ROD);
    }

    private void clearRoad(int x1, int z1, int x2, int z2, int ground, int halfWidth) {
        int minX = Math.min(x1, x2) - halfWidth;
        int maxX = Math.max(x1, x2) + halfWidth;
        int minZ = Math.min(z1, z2) - halfWidth;
        int maxZ = Math.max(z1, z2) + halfWidth;
        clear(minX, ground + 1, minZ, maxX, ground + 12, maxZ);
    }

    private void prepareLevelSite(int x1, int z1, int x2, int z2, int ground, Material floor) {
        fill(x1, ground - 5, z1, x2, ground, z2, Material.STONE);
        fill(x1, ground, z1, x2, ground, z2, floor);
        clear(x1, ground + 1, z1, x2, ground + 32, z2);
        perimeter(x1, ground + 1, z1, x2, z2, Material.STONE_BRICK_WALL);
    }

    private void steppedPath(int startX, int startZ, int startY, int endX, int endZ, int endY,
                             Material material, int halfWidth) {
        int steps = Math.max(Math.abs(endX - startX), Math.abs(endZ - startZ));
        boolean xDominant = Math.abs(endX - startX) >= Math.abs(endZ - startZ);
        for (int step = 0; step <= steps; step++) {
            double progress = steps == 0 ? 0.0 : (double) step / steps;
            int x = (int) Math.round(startX + (endX - startX) * progress);
            int z = (int) Math.round(startZ + (endZ - startZ) * progress);
            int y = (int) Math.round(startY + (endY - startY) * progress);
            int x1 = xDominant ? x : x - halfWidth;
            int x2 = xDominant ? x : x + halfWidth;
            int z1 = xDominant ? z - halfWidth : z;
            int z2 = xDominant ? z + halfWidth : z;
            clear(x1, y + 1, z1, x2, y + 8, z2);
            fill(x1, y - 3, z1, x2, y - 1, z2, Material.STONE);
            fill(x1, y, z1, x2, y, z2, material);
        }
    }

    private void newCityRoadLoop(int ground) {
        fill(4137, ground, -95, 4463, ground, -89, Material.POLISHED_ANDESITE);
        fill(4137, ground, 89, 4463, ground, 95, Material.POLISHED_ANDESITE);
        fill(4137, ground, -95, 4143, ground, 95, Material.POLISHED_ANDESITE);
        fill(4457, ground, -95, 4463, ground, 95, Material.POLISHED_ANDESITE);
        for (int x = 4140; x <= 4460; x += 10) {
            fill(x, ground, -92, x + 2, ground, -92, Material.SMOOTH_STONE);
            fill(x, ground, 92, x + 2, ground, 92, Material.SMOOTH_STONE);
        }
        for (int z = -90; z <= 90; z += 10) {
            fill(4140, ground, z, 4140, ground, z + 2, Material.SMOOTH_STONE);
            fill(4460, ground, z, 4460, ground, z + 2, Material.SMOOTH_STONE);
        }
    }

    private void trainingQuarter(int ground) {
        clear(4152, ground + 1, -164, 4252, ground + 14, -101);
        fill(4152, ground, -164, 4252, ground, -101, Material.COARSE_DIRT);
        for (int x = 4154; x <= 4250; x += 8) {
            fill(x, ground, -164, x + 2, ground, -101, Material.GRAVEL);
        }

        openShed(4174, -135, ground, 16, 11, Material.DARK_OAK_PLANKS, Material.DEEPSLATE_TILE_SLAB);
        for (int z = -154; z <= -114; z += 10) {
            fill(4224, ground + 1, z, 4224, ground + 3, z, Material.DARK_OAK_FENCE);
            place(4224, ground + 4, z, Material.TARGET);
            fill(4218, ground + 1, z - 2, 4220, ground + 2, z + 2, Material.HAY_BLOCK);
        }
        for (int x = 4198; x <= 4240; x += 14) {
            fill(x, ground + 1, -130, x + 5, ground + 1, -126, Material.MUD_BRICKS);
            fill(x, ground + 2, -130, x, ground + 3, -126, Material.MUD_BRICK_WALL);
        }
        campfireCircle(4202, -151, ground);
        quarterSignpost(4200, -102, ground, Material.RED_BANNER);
    }

    private void constructionQuarter(int ground) {
        clear(4348, ground + 1, -164, 4448, ground + 16, -101);
        fill(4348, ground, -164, 4448, ground, -101, Material.PACKED_MUD);
        for (int x = 4352; x <= 4444; x += 13) {
            for (int z = -159; z <= -105; z += 13) {
                if (Math.floorMod(x + z, 3) == 0) place(x, ground, z, Material.GRAVEL);
            }
        }

        framedWorkshop(4382, -134, ground);
        timberCrane(4424, -137, ground);
        materialStack(4360, -154, ground, Material.STRIPPED_SPRUCE_LOG);
        materialStack(4374, -154, ground, Material.STONE_BRICKS);
        materialStack(4388, -154, ground, Material.CUT_COPPER);
        for (int z = -119; z <= -107; z += 6) {
            fill(4408, ground + 1, z, 4414, ground + 3, z + 3, Material.BARREL);
        }
        quarterSignpost(4400, -102, ground, Material.YELLOW_BANNER);
    }

    private void farmQuarter(int ground) {
        clear(4152, ground + 1, 101, 4252, ground + 13, 164);
        fill(4152, ground, 101, 4252, ground, 164, Material.DIRT);

        for (int z = 108; z <= 151; z += 8) {
            fill(4158, ground, z, 4220, ground, z + 4, Material.FARMLAND);
            fill(4158, ground + 1, z, 4220, ground + 1, z + 4,
                    Math.floorMod(z, 3) == 0 ? Material.CARROTS : Material.WHEAT);
            fill(4158, ground, z + 5, 4220, ground, z + 5, Material.WATER);
        }
        greenhouse(4237, 126, ground);
        openShed(4236, 151, ground, 12, 8, Material.SPRUCE_PLANKS, Material.MOSS_BLOCK);
        for (int x = 4228; x <= 4244; x += 4) {
            place(x, ground + 1, 159, Material.COMPOSTER);
        }
        quarterSignpost(4200, 102, ground, Material.GREEN_BANNER);
    }

    private void expeditionQuarter(int ground) {
        clear(4348, ground + 1, 101, 4448, ground + 15, 164);
        fill(4348, ground, 101, 4448, ground, 164, Material.COARSE_DIRT);

        ridgeTent(4366, 119, ground, 9, 6, Material.WHITE_WOOL, Material.RED_WOOL);
        ridgeTent(4391, 114, ground, 7, 5, Material.LIGHT_GRAY_WOOL, Material.BLUE_WOOL);
        ridgeTent(4427, 121, ground, 10, 6, Material.WHITE_WOOL, Material.GREEN_WOOL);
        ridgeTent(4374, 151, ground, 8, 5, Material.BROWN_WOOL, Material.ORANGE_WOOL);
        ridgeTent(4420, 151, ground, 8, 5, Material.LIGHT_GRAY_WOOL, Material.YELLOW_WOOL);
        campfireCircle(4400, 137, ground);
        supplyWagon(4358, 139, ground);
        supplyWagon(4440, 140, ground);
        watchTower(4401, 156, ground);
        quarterSignpost(4400, 102, ground, Material.BLUE_BANNER);
    }

    private void openShed(int cx, int cz, int ground, int halfX, int halfZ, Material floor, Material roof) {
        fill(cx - halfX, ground, cz - halfZ, cx + halfX, ground, cz + halfZ, floor);
        for (int[] corner : new int[][]{{-halfX, -halfZ}, {-halfX, halfZ}, {halfX, -halfZ}, {halfX, halfZ}}) {
            fill(cx + corner[0], ground + 1, cz + corner[1], cx + corner[0], ground + 6, cz + corner[1], Material.STRIPPED_DARK_OAK_LOG);
        }
        fill(cx - halfX - 1, ground + 7, cz - halfZ - 1, cx + halfX + 1, ground + 7, cz + halfZ + 1, roof);
        fill(cx - halfX + 2, ground + 8, cz - halfZ + 2, cx + halfX - 2, ground + 8, cz + halfZ - 2, roof);
        for (int x = cx - halfX + 3; x <= cx + halfX - 3; x += 6) {
            place(x, ground + 6, cz, Material.LANTERN);
        }
    }

    private void framedWorkshop(int cx, int cz, int ground) {
        fill(cx - 18, ground, cz - 14, cx + 18, ground, cz + 14, Material.STONE_BRICKS);
        for (int x = cx - 18; x <= cx + 18; x += 9) {
            for (int z : new int[]{cz - 14, cz + 14}) {
                fill(x, ground + 1, z, x, ground + 11, z, Material.STRIPPED_SPRUCE_LOG);
            }
            fill(x, ground + 10, cz - 14, x, ground + 10, cz + 14, Material.STRIPPED_SPRUCE_LOG);
        }
        for (int z = cz - 14; z <= cz + 14; z += 7) {
            fill(cx - 18, ground + 5, z, cx + 18, ground + 5, z, Material.STRIPPED_SPRUCE_LOG);
        }
        fill(cx - 19, ground + 11, cz - 15, cx + 19, ground + 11, cz - 7, Material.SCAFFOLDING);
        fill(cx - 19, ground + 12, cz + 7, cx + 19, ground + 12, cz + 15, Material.SCAFFOLDING);
        place(cx, ground + 1, cz, Material.STONECUTTER);
        place(cx + 4, ground + 1, cz, Material.CRAFTING_TABLE);
    }

    private void timberCrane(int cx, int cz, int ground) {
        fill(cx - 6, ground + 1, cz, cx - 6, ground + 15, cz, Material.STRIPPED_DARK_OAK_LOG);
        fill(cx - 6, ground + 14, cz, cx + 13, ground + 14, cz, Material.STRIPPED_DARK_OAK_LOG);
        fill(cx + 12, ground + 14, cz, cx + 12, ground + 8, cz, Material.CHAIN);
        place(cx + 12, ground + 7, cz, Material.IRON_BLOCK);
        fill(cx - 10, ground, cz - 4, cx - 2, ground, cz + 4, Material.STONE_BRICKS);
        fill(cx - 9, ground + 1, cz - 3, cx - 3, ground + 3, cz + 3, Material.SPRUCE_PLANKS);
    }

    private void materialStack(int cx, int cz, int ground, Material material) {
        fill(cx - 5, ground + 1, cz - 4, cx + 5, ground + 3, cz + 4, material);
        fill(cx - 3, ground + 4, cz - 2, cx + 3, ground + 5, cz + 2, material);
    }

    private void greenhouse(int cx, int cz, int ground) {
        fill(cx - 11, ground, cz - 17, cx + 11, ground, cz + 17, Material.STONE_BRICKS);
        for (int y = 1; y <= 6; y++) {
            int inset = Math.max(0, y - 3);
            perimeter(cx - 11 + inset, ground + y, cz - 17, cx + 11 - inset, cz + 17, Material.GLASS);
        }
        fill(cx - 5, ground + 1, cz - 13, cx + 5, ground + 1, cz + 13, Material.MOSS_BLOCK);
        for (int z = cz - 11; z <= cz + 11; z += 5) {
            place(cx - 3, ground + 2, z, Material.FLOWERING_AZALEA);
            place(cx + 3, ground + 2, z, Material.AZALEA);
        }
    }

    private void ridgeTent(int cx, int cz, int ground, int halfX, int halfZ, Material canvas, Material stripe) {
        fill(cx - halfX, ground, cz - halfZ, cx + halfX, ground, cz + halfZ, Material.SPRUCE_PLANKS);
        for (int layer = 0; layer <= halfZ; layer++) {
            int zOffset = halfZ - layer;
            Material layerMaterial = layer == halfZ / 2 ? stripe : canvas;
            fill(cx - halfX - 1, ground + layer + 1, cz - zOffset, cx + halfX + 1, ground + layer + 1, cz - zOffset, layerMaterial);
            fill(cx - halfX - 1, ground + layer + 1, cz + zOffset, cx + halfX + 1, ground + layer + 1, cz + zOffset, layerMaterial);
        }
        fill(cx - halfX, ground + 1, cz - halfZ + 1, cx - halfX, ground + halfZ, cz + halfZ - 1, canvas);
        fill(cx + halfX, ground + 1, cz - halfZ + 1, cx + halfX, ground + halfZ, cz + halfZ - 1, canvas);
        clear(cx - halfX, ground + 1, cz - 1, cx - halfX, ground + 3, cz + 1);
        place(cx, ground + 1, cz, Material.BARREL);
        place(cx + 3, ground + 1, cz, Material.RED_BED);
    }

    private void campfireCircle(int cx, int cz, int ground) {
        fill(cx - 5, ground, cz - 5, cx + 5, ground, cz + 5, Material.GRAVEL);
        fill(cx - 2, ground, cz - 2, cx + 2, ground, cz + 2, Material.COBBLESTONE);
        place(cx, ground + 1, cz, Material.CAMPFIRE);
        for (int[] seat : new int[][]{{-4, 0}, {4, 0}, {0, -4}, {0, 4}}) {
            place(cx + seat[0], ground + 1, cz + seat[1], Material.SPRUCE_SLAB);
        }
    }

    private void supplyWagon(int cx, int cz, int ground) {
        fill(cx - 5, ground + 2, cz - 3, cx + 5, ground + 3, cz + 3, Material.DARK_OAK_PLANKS);
        for (int x : new int[]{cx - 4, cx + 4}) {
            place(x, ground + 1, cz - 4, Material.DARK_OAK_LOG);
            place(x, ground + 1, cz + 4, Material.DARK_OAK_LOG);
        }
        fill(cx - 3, ground + 4, cz - 2, cx + 3, ground + 5, cz + 2, Material.BARREL);
        fill(cx + 6, ground + 2, cz, cx + 11, ground + 2, cz, Material.DARK_OAK_FENCE);
    }

    private void watchTower(int cx, int cz, int ground) {
        for (int[] corner : new int[][]{{-4, -4}, {-4, 4}, {4, -4}, {4, 4}}) {
            fill(cx + corner[0], ground + 1, cz + corner[1], cx + corner[0], ground + 11, cz + corner[1], Material.STRIPPED_SPRUCE_LOG);
        }
        fill(cx - 6, ground + 10, cz - 6, cx + 6, ground + 11, cz + 6, Material.SPRUCE_PLANKS);
        perimeter(cx - 6, ground + 12, cz - 6, cx + 6, cz + 6, Material.SPRUCE_FENCE);
        fill(cx - 7, ground + 14, cz - 7, cx + 7, ground + 14, cz + 7, Material.DEEPSLATE_TILE_SLAB);
        place(cx, ground + 12, cz, Material.LANTERN);
    }

    private void quarterSignpost(int x, int z, int ground, Material banner) {
        fill(x, ground + 1, z, x, ground + 5, z, Material.STRIPPED_DARK_OAK_LOG);
        fill(x - 3, ground + 5, z, x + 3, ground + 5, z, Material.DARK_OAK_PLANKS);
        place(x, ground + 6, z, banner);
        place(x - 4, ground + 5, z, Material.LANTERN);
        place(x + 4, ground + 5, z, Material.LANTERN);
    }

    private void polishQingtianTower() {
        int ground = 78;
        for (int y = 80; y <= 196; y++) {
            for (int[] corner : new int[][]{{-26, -26}, {-26, 26}, {26, -26}, {26, 26}}) {
                place(700 + corner[0], y, corner[1], Material.POLISHED_BASALT);
            }
        }
        for (int y = 95; y <= 191; y += 16) {
            perimeter(669, y, -31, 731, 31, Material.DEEPSLATE_TILE_SLAB);
            for (int x = 672; x <= 728; x += 7) {
                place(x, y + 1, -30, Material.DEEPSLATE_BRICK_WALL);
                place(x, y + 1, 30, Material.DEEPSLATE_BRICK_WALL);
            }
            for (int z = -23; z <= 23; z += 7) {
                place(670, y + 1, z, Material.DEEPSLATE_BRICK_WALL);
                place(730, y + 1, z, Material.DEEPSLATE_BRICK_WALL);
            }
        }
        fill(668, 197, -32, 732, 197, 32, Material.DARK_PRISMARINE_SLAB);
        fill(673, 198, -27, 727, 198, 27, Material.DEEPSLATE_TILE_SLAB);
        fill(679, 199, -21, 721, 199, 21, Material.DARK_PRISMARINE_SLAB);
        fill(686, 200, -14, 714, 200, 14, Material.DEEPSLATE_TILE_SLAB);
        fill(694, 201, -6, 706, 201, 6, Material.DARK_PRISMARINE_SLAB);
        fill(699, 202, -1, 701, 207, 1, Material.PRISMARINE_BRICKS);
        place(700, 198, 0, Material.SEA_LANTERN);
        place(700, 208, 0, Material.SEA_LANTERN);
    }

    private void polishSuiAnHomes() {
        int ground = 78;
        for (int parcelX = 12; parcelX <= 19; parcelX++) {
            for (int parcelZ = -4; parcelZ <= 3; parcelZ++) {
                int margin = 8 + (int) (positiveHash(parcelX, parcelZ, 9) % 3);
                int max = 44 - margin;
                int minX = parcelX * 44 + margin;
                int maxX = parcelX * 44 + max;
                int minZ = parcelZ * 44 + margin;
                int maxZ = parcelZ * 44 + max;
                int cx = (minX + maxX) / 2;
                int cz = (minZ + maxZ) / 2;
                if (Math.abs(cx - 700) >= 165 || Math.abs(cz) >= 165) continue;
                if (Math.abs(cx - 700) <= 68 && Math.abs(cz) <= 68) continue;
                int height = 8 + (int) (positiveHash(parcelX, parcelZ, 13) % 8);
                int roofY = ground + height + 2;
                perimeter(minX - 1, roofY, minZ - 1, maxX + 1, maxZ + 1, Material.DARK_OAK_SLAB);
                fill(minX + 2, roofY + 1, minZ + 2, maxX - 2, roofY + 1, maxZ - 2, Material.SPRUCE_SLAB);
                fill(cx - 1, roofY + 2, minZ + 5, cx + 1, roofY + 3, maxZ - 5, Material.DARK_OAK_PLANKS);
                fill(minX + 3, roofY + 1, minZ + 3, minX + 4, roofY + 5, minZ + 4, Material.BRICKS);

                clear(cx - 1, ground + 1, maxZ, cx + 1, ground + 3, maxZ + 1);
                fill(cx - 3, ground + 4, maxZ, cx + 3, ground + 4, maxZ + 2, Material.DARK_OAK_SLAB);
                place(cx - 3, ground + 1, maxZ + 1, Material.DARK_OAK_FENCE);
                place(cx + 3, ground + 1, maxZ + 1, Material.DARK_OAK_FENCE);
                place(cx, ground + 4, maxZ + 3, Material.LANTERN);
                place(minX + 2, ground + 1, maxZ + 1, Material.FLOWERING_AZALEA_LEAVES);
                place(maxX - 2, ground + 1, maxZ + 1, Material.FLOWERING_AZALEA_LEAVES);
            }
        }
    }

    private void polishNewCityCore() {
        steppedRoof(4300, -66, 29, 19, 94, Material.DEEPSLATE_TILE_SLAB);
        steppedRoof(4250, -63, 17, 15, 88, Material.SMOOTH_QUARTZ_SLAB);
        steppedRoof(4350, -63, 19, 15, 88, Material.DEEPSLATE_TILE_SLAB);
        steppedRoof(4352, 54, 20, 16, 88, Material.WAXED_CUT_COPPER_SLAB);
        steppedRoof(4195, -52, 21, 14, 88, Material.DARK_OAK_SLAB);
        steppedRoof(4195, 50, 21, 14, 88, Material.DARK_OAK_SLAB);

        for (int[] gate : new int[][]{{4120, -15}, {4120, 15}}) {
            steppedRoof(gate[0], gate[1], 9, 9, 94, Material.DEEPSLATE_TILE_SLAB);
        }
        for (int x = 4160; x <= 4230; x += 18) {
            place(x, 77, -18, Material.COARSE_DIRT);
            place(x, 78, -18, Material.FLOWERING_AZALEA);
            place(x, 77, 18, Material.COARSE_DIRT);
            place(x, 78, 18, Material.FLOWERING_AZALEA);
        }
    }

    private void steppedRoof(int cx, int cz, int halfX, int halfZ, int startY, Material material) {
        int layers = Math.min(6, Math.max(2, halfZ / 3));
        for (int layer = 0; layer < layers; layer++) {
            int hx = Math.max(3, halfX - layer * 2);
            int hz = Math.max(2, halfZ - layer * 2);
            fill(cx - hx, startY + layer, cz - hz, cx + hx, startY + layer, cz + hz, material);
        }
        fill(cx - Math.max(3, halfX - layers * 2), startY + layers, cz - 1,
                cx + Math.max(3, halfX - layers * 2), startY + layers + 1, cz + 1, Material.DARK_OAK_PLANKS);
    }

    private void courtyardTree(int x, int z, int ground, Material leaves) {
        fill(x, ground + 1, z, x, ground + 6, z, Material.CHERRY_LOG);
        for (int ox = -3; ox <= 3; ox++) {
            for (int oz = -3; oz <= 3; oz++) {
                if (Math.abs(ox) + Math.abs(oz) <= 5) place(x + ox, ground + 6, z + oz, leaves);
                if (Math.abs(ox) <= 2 && Math.abs(oz) <= 2) place(x + ox, ground + 7, z + oz, leaves);
            }
        }
        place(x, ground + 8, z, leaves);
    }

    private void perimeter(int x1, int y, int z1, int x2, int z2, Material material) {
        for (int x = x1; x <= x2; x++) {
            place(x, y, z1, material);
            place(x, y, z2, material);
        }
        for (int z = z1 + 1; z < z2; z++) {
            place(x1, y, z, material);
            place(x2, y, z, material);
        }
    }

    private long positiveHash(int x, int z, int salt) {
        long value = x * 341873128712L + z * 132897987541L + salt * 42317861L;
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value & Long.MAX_VALUE;
    }

    private void building(int cx, int cz, int ground, int halfX, int halfZ, int floors,
                          Material wall, Material trim, Material roof) {
        int top = ground + floors * 7 + 2;
        clear(cx - halfX, ground + 1, cz - halfZ, cx + halfX, top + 3, cz + halfZ);
        fill(cx - halfX, ground, cz - halfZ, cx + halfX, ground, cz + halfZ, Material.STONE_BRICKS);

        for (int floor = 0; floor < floors; floor++) {
            int base = ground + floor * 7;
            fill(cx - halfX, base, cz - halfZ, cx + halfX, base, cz + halfZ, Material.SPRUCE_PLANKS);
            for (int y = base + 1; y <= base + 6; y++) {
                for (int x = cx - halfX; x <= cx + halfX; x++) {
                    place(x, y, cz - halfZ, facadeMaterial(x - cx, y - base, wall, trim));
                    place(x, y, cz + halfZ, facadeMaterial(x - cx, y - base, wall, trim));
                }
                for (int z = cz - halfZ + 1; z < cz + halfZ; z++) {
                    place(cx - halfX, y, z, facadeMaterial(z - cz, y - base, wall, trim));
                    place(cx + halfX, y, z, facadeMaterial(z - cz, y - base, wall, trim));
                }
            }
        }

        for (int y = ground + 1; y <= ground + 4; y++) {
            for (int x = cx - 2; x <= cx + 2; x++) place(x, y, cz + halfZ, Material.AIR);
        }
        fill(cx - halfX - 1, top, cz - halfZ - 1, cx + halfX + 1, top, cz + halfZ + 1, roof);
        fill(cx - halfX + 2, top + 1, cz - halfZ + 2, cx + halfX - 2, top + 1, cz + halfZ - 2, roof);
        for (int x = cx - halfX; x <= cx + halfX; x += 6) place(x, ground + 1, cz + halfZ + 1, Material.LANTERN);
    }

    private Material facadeMaterial(int offset, int localY, Material wall, Material trim) {
        if (Math.floorMod(offset, 6) == 0) return trim;
        if ((localY == 3 || localY == 4) && Math.floorMod(offset, 6) >= 2 && Math.floorMod(offset, 6) <= 4) {
            return Material.GLASS_PANE;
        }
        return wall;
    }

    private void portico(int cx, int southZ, int ground, Material material) {
        fill(cx - 8, ground, southZ, cx + 8, ground, southZ + 8, Material.POLISHED_ANDESITE);
        for (int x : new int[]{cx - 7, cx - 2, cx + 2, cx + 7}) {
            fill(x, ground + 1, southZ + 2, x, ground + 6, southZ + 2, material);
        }
        fill(cx - 9, ground + 7, southZ, cx + 9, ground + 7, southZ + 5, Material.DEEPSLATE_TILE_SLAB);
    }

    private void interiorHall(int cx, int cz, int ground) {
        fill(cx - 12, ground + 1, cz - 3, cx + 12, ground + 1, cz + 3, Material.POLISHED_ANDESITE);
        fill(cx - 11, ground + 2, cz - 8, cx - 9, ground + 3, cz - 4, Material.DARK_OAK_PLANKS);
        fill(cx + 9, ground + 2, cz - 8, cx + 11, ground + 3, cz - 4, Material.DARK_OAK_PLANKS);
        for (int x = cx - 8; x <= cx + 8; x += 4) {
            place(x, ground + 2, cz + 6, Material.DARK_OAK_FENCE);
            place(x, ground + 3, cz + 6, Material.LANTERN);
        }
    }

    private void clinicInterior(int cx, int cz, int ground) {
        for (int z = cz - 8; z <= cz + 7; z += 5) {
            fill(cx - 11, ground + 1, z, cx - 7, ground + 1, z + 1, Material.WHITE_WOOL);
            fill(cx + 7, ground + 1, z, cx + 11, ground + 1, z + 1, Material.WHITE_WOOL);
        }
        fill(cx - 2, ground + 1, cz - 7, cx + 2, ground + 2, cz - 5, Material.QUARTZ_BLOCK);
        place(cx, ground + 3, cz - 5, Material.BREWING_STAND);
    }

    private void barracksInterior(int cx, int cz, int ground) {
        for (int x = cx - 12; x <= cx + 12; x += 6) {
            fill(x, ground + 1, cz - 8, x + 2, ground + 1, cz - 7, Material.RED_WOOL);
            fill(x, ground + 1, cz + 6, x + 2, ground + 1, cz + 7, Material.RED_WOOL);
        }
        fill(cx - 8, ground + 1, cz - 1, cx + 8, ground + 1, cz + 1, Material.DARK_OAK_PLANKS);
    }

    private void refineryInterior(int cx, int cz, int ground) {
        for (int z = cz - 8; z <= cz + 8; z += 4) {
            place(cx - 12, ground + 1, z, Material.BLAST_FURNACE);
            place(cx + 12, ground + 1, z, Material.SMOKER);
        }
        fill(cx - 6, ground + 1, cz - 2, cx + 6, ground + 1, cz + 2, Material.CUT_COPPER);
        place(cx, ground + 2, cz, Material.SMITHING_TABLE);
        place(cx - 4, ground + 2, cz, Material.ANVIL);
        place(cx + 4, ground + 2, cz, Material.GRINDSTONE);
    }

    private void warehouseInterior(int cx, int cz, int ground) {
        for (int x = cx - 14; x <= cx + 14; x += 7) {
            for (int z = cz - 8; z <= cz + 8; z += 8) {
                fill(x, ground + 1, z, x + 2, ground + 3, z + 2, Material.BARREL);
            }
        }
    }

    private void mirrorCourt(int cx, int cz, int ground) {
        patternedPlaza(cx, cz, ground, 21, 19, Material.SMOOTH_STONE, Material.QUARTZ_BLOCK);
        for (int x = cx - 21; x <= cx + 21; x++) {
            place(x, ground + 1, cz - 19, Material.QUARTZ_BRICKS);
            place(x, ground + 1, cz + 19, Material.QUARTZ_BRICKS);
        }
        for (int z = cz - 19; z <= cz + 19; z++) {
            place(cx - 21, ground + 1, z, Material.QUARTZ_BRICKS);
            place(cx + 21, ground + 1, z, Material.QUARTZ_BRICKS);
        }
        fill(cx - 3, ground + 1, cz - 3, cx + 3, ground + 1, cz + 3, Material.POLISHED_BLACKSTONE);
        place(cx, ground + 2, cz, Material.LODESTONE);
        for (int[] corner : new int[][]{{-15, -13}, {-15, 13}, {15, -13}, {15, 13}}) {
            streetLamp(cx + corner[0], cz + corner[1], ground);
        }
    }

    private void newCityWestGate(int ground) {
        clear(4108, ground + 1, -18, 4134, ground + 24, 18);
        gateTower(4120, -15, ground, Material.MUD_BRICKS, Material.DEEPSLATE_BRICKS);
        gateTower(4120, 15, ground, Material.MUD_BRICKS, Material.DEEPSLATE_BRICKS);
        fill(4117, ground + 1, -9, 4123, ground + 13, 9, Material.MUD_BRICKS);
        clear(4117, ground + 1, -5, 4123, ground + 8, 5);
        fill(4116, ground + 12, -10, 4124, ground + 14, 10, Material.DEEPSLATE_BRICKS);
        place(4116, ground + 15, 0, Material.SEA_LANTERN);
    }

    private void gateTower(int cx, int cz, int ground, Material wall, Material trim) {
        fill(cx - 7, ground, cz - 7, cx + 7, ground, cz + 7, Material.STONE_BRICKS);
        for (int y = ground + 1; y <= ground + 16; y++) {
            for (int x = cx - 7; x <= cx + 7; x++) {
                place(x, y, cz - 7, x == cx || x == cx - 1 ? Material.GLASS_PANE : wall);
                place(x, y, cz + 7, x == cx || x == cx + 1 ? Material.GLASS_PANE : wall);
            }
            for (int z = cz - 6; z <= cz + 6; z++) {
                place(cx - 7, y, z, z == cz ? Material.GLASS_PANE : wall);
                place(cx + 7, y, z, z == cz ? Material.GLASS_PANE : wall);
            }
        }
        fill(cx - 8, ground + 17, cz - 8, cx + 8, ground + 17, cz + 8, trim);
        fill(cx - 5, ground + 18, cz - 5, cx + 5, ground + 18, cz + 5, trim);
    }

    private void market(int cx, int cz, int ground) {
        clear(cx - 35, ground + 1, cz - 28, cx + 35, ground + 12, cz + 28);
        patternedPlaza(cx, cz, ground, 35, 28, Material.PACKED_MUD, Material.STONE_BRICKS);
        for (int x = cx - 27; x <= cx + 27; x += 18) {
            for (int z = cz - 18; z <= cz + 18; z += 18) {
                fill(x - 5, ground + 1, z - 4, x + 5, ground + 1, z + 4, Material.DARK_OAK_PLANKS);
                for (int[] corner : new int[][]{{-5, -4}, {-5, 4}, {5, -4}, {5, 4}}) {
                    fill(x + corner[0], ground + 2, z + corner[1], x + corner[0], ground + 5, z + corner[1], Material.DARK_OAK_FENCE);
                }
                Material canopy = Math.floorMod(x + z, 2) == 0 ? Material.RED_WOOL : Material.WHITE_WOOL;
                fill(x - 6, ground + 6, z - 5, x + 6, ground + 6, z + 5, canopy);
                place(x, ground + 2, z, Material.BARREL);
            }
        }
    }

    private void jiuhuiAssemblyHall() {
        int y = 28;
        clear(1218, y + 1, -18, 1282, y + 18, 18);
        fill(1218, y, -18, 1282, y, 18, Material.DEEPSLATE_TILES);
        fill(1218, y + 18, -18, 1282, y + 18, 18, Material.REINFORCED_DEEPSLATE);
        for (int x = 1222; x <= 1278; x += 8) {
            fill(x, y + 1, -15, x, y + 13, -15, Material.POLISHED_DEEPSLATE);
            fill(x, y + 1, 15, x, y + 13, 15, Material.POLISHED_DEEPSLATE);
            place(x, y + 12, -14, Material.SOUL_LANTERN);
            place(x, y + 12, 14, Material.SOUL_LANTERN);
        }
        fill(1263, y + 1, -6, 1276, y + 2, 6, Material.POLISHED_BLACKSTONE_BRICKS);
        fill(1268, y + 3, -3, 1274, y + 5, 3, Material.DEEPSLATE_BRICKS);
    }

    private void mountainPassInn() {
        int ground = EvilIslandShape.mountainPassTerraceHeight(1524);
        building(1524, -113, ground, 9, 9, 2,
                Material.COBBLED_DEEPSLATE, Material.SPRUCE_LOG, Material.DARK_OAK_SLAB);
        fill(1518, ground + 1, -117, 1530, ground + 1, -109, Material.DARK_OAK_PLANKS);
        place(1524, ground + 2, -113, Material.CAMPFIRE);
        place(1519, ground + 1, -108, Material.BARREL);
        place(1529, ground + 1, -108, Material.BREWING_STAND);
    }

    private void rongxuControlRoom() {
        int y = 101;
        clear(1311, y + 1, 600, 1329, y + 10, 642);
        fill(1311, y, 600, 1329, y, 642, Material.SMOOTH_STONE);
        fill(1311, y + 10, 600, 1329, y + 10, 642, Material.IRON_BLOCK);
        for (int z = 604; z <= 638; z += 8) {
            fill(1312, y + 1, z, 1314, y + 3, z + 2, Material.COPPER_BLOCK);
            place(1313, y + 4, z + 1, Material.REDSTONE_LAMP);
            fill(1326, y + 1, z, 1328, y + 3, z + 2, Material.DISPENSER);
        }
        fill(1317, y + 1, 616, 1323, y + 2, 626, Material.TINTED_GLASS);
        place(1320, y + 3, 621, Material.DAYLIGHT_DETECTOR);
    }

    private void magicIslandCourt() {
        int ground = EvilIslandShape.surfaceHeight(3420, 1750);
        patternedPlaza(3420, 1750, ground, 42, 42, Material.QUARTZ_BLOCK, Material.AMETHYST_BLOCK);
        for (int angle = 0; angle < 8; angle++) {
            double radians = angle * Math.PI / 4.0;
            int x = 3420 + (int) Math.round(Math.cos(radians) * 34);
            int z = 1750 + (int) Math.round(Math.sin(radians) * 34);
            fill(x, ground + 1, z, x, ground + 7, z, Material.PURPUR_PILLAR);
            place(x, ground + 8, z, Material.END_ROD);
        }
    }

    private void dragonPalaceChamber() {
        int y = 27;
        fill(2483, y, -1917, 2517, y, -1883, Material.PRISMARINE_BRICKS);
        for (int x = 2486; x <= 2514; x += 7) {
            fill(x, y + 1, -1914, x, y + 8, -1914, Material.GOLD_BLOCK);
            fill(x, y + 1, -1886, x, y + 8, -1886, Material.GOLD_BLOCK);
            place(x, y + 9, -1914, Material.SEA_LANTERN);
            place(x, y + 9, -1886, Material.SEA_LANTERN);
        }
        fill(2495, y + 1, -1905, 2505, y + 1, -1895, Material.DARK_PRISMARINE);
        place(2500, y + 1, -1900, Material.LODESTONE);
    }

    private void patternedPlaza(int cx, int cz, int y, int halfX, int halfZ, Material base, Material accent) {
        for (int x = cx - halfX; x <= cx + halfX; x++) {
            for (int z = cz - halfZ; z <= cz + halfZ; z++) {
                boolean line = Math.floorMod(x - cx, 12) == 0 || Math.floorMod(z - cz, 12) == 0;
                place(x, y, z, line ? accent : base);
            }
        }
    }

    private void fountain(int cx, int cz, int ground) {
        fill(cx - 7, ground + 1, cz - 7, cx + 7, ground + 1, cz + 7, Material.SMOOTH_STONE);
        fill(cx - 5, ground + 2, cz - 5, cx + 5, ground + 2, cz + 5, Material.WATER);
        fill(cx - 1, ground + 2, cz - 1, cx + 1, ground + 7, cz + 1, Material.PRISMARINE_BRICKS);
        place(cx, ground + 8, cz, Material.SEA_LANTERN);
    }

    private void streetLamp(int x, int z, int ground) {
        place(x, ground + 1, z, Material.POLISHED_BLACKSTONE_BRICK_WALL);
        fill(x, ground + 2, z, x, ground + 4, z, Material.DARK_OAK_FENCE);
        place(x, ground + 5, z, Material.LANTERN);
    }

    private void clear(int x1, int y1, int z1, int x2, int y2, int z2) {
        fill(x1, y1, z1, x2, y2, z2, Material.AIR);
    }

    private void fill(int x1, int y1, int z1, int x2, int y2, int z2, Material material) {
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) place(x, y, z, material);
            }
        }
    }

    private void place(int x, int y, int z, Material material) {
        queue.addLast(new Placement(x, y, z, material));
    }
}
