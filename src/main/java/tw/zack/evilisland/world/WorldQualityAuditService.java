package tw.zack.evilisland.world;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitTask;
import tw.zack.evilisland.EvilIslandPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class WorldQualityAuditService {
    private final EvilIslandPlugin plugin;
    private final WorldAtlasService atlas;
    private final LandmarkDetailService details;
    private final List<String> results = new ArrayList<>();
    private int checks;
    private int failures;
    private BukkitTask task;

    public WorldQualityAuditService(EvilIslandPlugin plugin, WorldAtlasService atlas,
                                    LandmarkDetailService details) {
        this.plugin = plugin;
        this.atlas = atlas;
        this.details = details;
    }

    public void schedule() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (details.isBuilding()) return;
            task.cancel();
            task = null;
            runAudit();
        }, 40L, 20L);
    }

    private void runAudit() {
        World world = atlas.mainWorld();
        results.clear();
        checks = 0;
        failures = 0;

        auditNewCity(world);
        auditSuiAn(world);
        auditMountainPass(world);
        auditUndergroundSites(world);
        auditMagicIsland(world);
        auditDragonPalace(world);

        if (atlas.palaceRealm() != null) {
            expectMaterial("palace-realm gate", atlas.palaceRealm(), 0, 92, 0, Material.LODESTONE);
        } else {
            checks++;
            fail("palace-realm gate", "palace realm is unavailable");
        }

        String summary = "Map quality audit: " + (checks - failures) + "/" + checks + " checks passed";
        results.add(0, summary);
        results.add(1, "Generated: " + Instant.now());
        writeReport();
        if (failures == 0) plugin.getLogger().info(summary + ".");
        else plugin.getLogger().warning(summary + "; see map-audit.txt for " + failures + " failure(s).");
    }

    private void auditNewCity(World world) {
        auditLine("new-city north loop", world, 4140, -92, 4460, -92, 76, 3);
        auditLine("new-city south loop", world, 4140, 92, 4460, 92, 76, 3);
        auditLine("new-city west loop", world, 4140, -88, 4140, 88, 76, 3);
        auditLine("new-city east loop", world, 4460, -88, 4460, 88, 76, 3);
        auditLine("new-city central avenue", world, 4130, 0, 4465, 0, 76, 3);
        expectMaterial("new-city refinery", world, 4352, 78, 54, Material.SMITHING_TABLE);
        expectMaterial("new-city qi mirror", world, 4250, 78, 55, Material.LODESTONE);
        auditClearBox("new-city west gate", world, 4117, 77, -5, 4123, 84, 5);
    }

    private void auditSuiAn(World world) {
        auditLine("suian north radial avenue", world, 700, -390, 700, -80, 78, 4);
        auditLine("suian south radial avenue", world, 700, 80, 700, 390, 78, 4);
        auditLine("suian west radial avenue", world, 440, 0, 620, 0, 78, 4);
        auditLine("suian east radial avenue", world, 780, 0, 960, 0, 78, 4);
        expectMaterial("suian inner ring", world, 700, 78, 108, Material.POLISHED_ANDESITE);
        expectMaterial("suian second ring", world, 700, 78, 194, Material.POLISHED_ANDESITE);
        expectMaterial("suian defense ring", world, 700, 78, 292, Material.POLISHED_ANDESITE);
        expectMaterial("suian outer ring north", world, 700, 78, -396,
                Material.POLISHED_BLACKSTONE_BRICKS);
        expectMaterial("suian outer ring east", world, 948, 78, 0,
                Material.POLISHED_BLACKSTONE_BRICKS);
        expectMaterial("suian staged wall supplies", world, 833, 79, 212, Material.PACKED_MUD);
        expectMaterial("suian old-gate ruin at headquarters", world, 522, 90, -43,
                Material.MUD_BRICKS);
        auditOldSuiAnWallRemoved(world);
        auditSuiAnBuildingDensity(world);
        auditContinuousColumn("qingtian southwest support", world, 674, 80, -26, 196);
        auditContinuousColumn("qingtian southeast support", world, 726, 80, -26, 196);
        expectMaterial("qingtian beacon", world, 700, 208, 0, Material.SEA_LANTERN);
    }

    private void auditOldSuiAnWallRemoved(World world) {
        int wallBlocks = 0;
        int samples = 0;
        for (int y = 79; y <= 93; y++) {
            for (int x = 520; x <= 880; x++) {
                if (isOldWallMaterial(world.getBlockAt(atlas.worldX(x), y, atlas.worldZ(-180)).getType())) wallBlocks++;
                if (isOldWallMaterial(world.getBlockAt(atlas.worldX(x), y, atlas.worldZ(180)).getType())) wallBlocks++;
                samples += 2;
            }
            for (int z = -179; z <= 179; z++) {
                if (isOldWallMaterial(world.getBlockAt(atlas.worldX(520), y, atlas.worldZ(z)).getType())) wallBlocks++;
                if (isOldWallMaterial(world.getBlockAt(atlas.worldX(880), y, atlas.worldZ(z)).getType())) wallBlocks++;
                samples += 2;
            }
        }
        double ratio = wallBlocks / (double) samples;
        checkAggregate("suian old fixed wall demolished",
                "old-wall-material ratio=" + String.format("%.3f", ratio), ratio < 0.20);
    }

    private boolean isOldWallMaterial(Material material) {
        return material == Material.MUD_BRICKS || material == Material.PACKED_MUD
                || material == Material.MUD_BRICK_WALL;
    }

    private void auditSuiAnBuildingDensity(World world) {
        int occupied = 0;
        int samples = 0;
        for (int x = 452; x <= 948; x += 4) {
            for (int z = -396; z <= 396; z += 4) {
                if (EvilIslandShape.suiAnRadius(x, z) >= 0.88) continue;
                samples++;
                if (!world.getBlockAt(atlas.worldX(x), 99, atlas.worldZ(z)).isPassable()) occupied++;
            }
        }
        double ratio = occupied / (double) samples;
        checkAggregate("suian three-to-four-storey density",
                "upper-floor occupancy=" + String.format("%.3f", ratio), ratio >= 0.18);
    }

    private void auditMountainPass(World world) {
        auditClearBox("mountain-pass underpass", world, 1537, 100, -83, 1543, 107, -77);
        auditLine("mountain-pass north-south road", world, 1540, -134, 1540, -89, 99, 3);
        auditLine("mountain-pass south road", world, 1540, -71, 1540, -26, 99, 3);
        auditSteppedPath("mountain-pass inn approach", world,
                1518, -99, EvilIslandShape.surfaceHeight(1518, -115),
                1527, -85, 117, 2);
        for (int x = 1527; x <= 1553; x++) {
            int pathY;
            if (x <= 1535) pathY = 117 - (x - 1527);
            else if (x <= 1544) pathY = 108;
            else pathY = 108 - (x - 1544);
            auditWalkable("mountain-pass bridge x=" + x, world, x, pathY, -80, 2);
        }
        int[][] houses = {
                {1524, -113}, {1497, -113}, {1497, -47}, {1524, -47}, {1551, -113},
                {1551, -47}, {1572, -113}, {1572, -47}, {1591, -113}, {1591, -47}
        };
        for (int[] house : houses) auditMountainHouse(world, house[0], house[1]);
    }

    private void auditMountainHouse(World world, int cx, int cz) {
        int ground = EvilIslandShape.mountainPassTerraceHeight(cx);
        auditWalkable("mountain house entrance " + cx + "," + cz,
                world, cx, ground, cz + 10, 3);
        expectSolid("mountain house foundation " + cx + "," + cz, world, cx, ground, cz);
    }

    private void auditUndergroundSites(World world) {
        expectMaterial("jiuhui assembly floor", world, 1250, 28, 0, Material.DEEPSLATE_TILES);
        auditClearBox("jiuhui assembly passage", world, 1222, 29, -12, 1260, 40, 12);
        expectMaterial("rongxu control floor", world, 1320, 101, 610, Material.SMOOTH_STONE);
        auditClearBox("rongxu central passage", world, 1317, 102, 608, 1323, 108, 614);
    }

    private void auditMagicIsland(World world) {
        int ground = EvilIslandShape.surfaceHeight(3420, 1750);
        expectMaterial("magic-island tower crown", world, 3420, ground + 55, 1750, Material.AMETHYST_BLOCK);
        auditContinuousColumn("magic-island east tower shell", world, 3438, ground + 1, 1750, ground + 54);
        auditContinuousColumn("magic-island west tower shell", world, 3402, ground + 1, 1750, ground + 54);
        auditContinuousColumn("magic-island north tower shell", world, 3420, ground + 1, 1732, ground + 54);
        auditWalkable("magic-island tower entrance", world, 3420, ground, 1768, 5);
        expectMaterial("magic-island tower ascent", world, 3420, ground + 40, 1750, Material.SCAFFOLDING);
        expectSolid("magic-island plaza north", world, 3420, ground, 1708);
        expectSolid("magic-island plaza south", world, 3420, ground, 1792);
        expectSolid("magic-island plaza west", world, 3378, ground, 1750);
        expectSolid("magic-island plaza east", world, 3462, ground, 1750);
        auditSteppedPath("magic-island west approach", world,
                3363, 1750, EvilIslandShape.surfaceHeight(3352, 1750),
                3378, 1750, ground, 2);
        auditSteppedPath("magic-island east approach", world,
                3462, 1750, ground,
                3477, 1750, EvilIslandShape.surfaceHeight(3488, 1750), 2);
        auditMagicPavilion(world, 3352, 1750);
        auditMagicPavilion(world, 3488, 1750);
    }

    private void auditMagicPavilion(World world, int cx, int cz) {
        int ground = EvilIslandShape.surfaceHeight(cx, cz);
        expectSolid("magic pavilion foundation " + cx, world, cx, ground, cz);
        auditWalkable("magic pavilion entrance " + cx, world, cx, ground, cz + 10, 4);
    }

    private void auditDragonPalace(World world) {
        expectMaterial("dragon-palace realm gate", world, 2500, 28, -1900, Material.LODESTONE);
        expectSolid("dragon-palace chamber floor", world, 2490, 27, -1900);
        auditClearBox("dragon-palace central chamber", world, 2496, 29, -1904, 2504, 34, -1896);
    }

    private void auditLine(String label, World world, int x1, int z1, int x2, int z2,
                           int floorY, int clearance) {
        x1 = atlas.worldX(x1);
        z1 = atlas.worldZ(z1);
        x2 = atlas.worldX(x2);
        z2 = atlas.worldZ(z2);
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(z2 - z1));
        int blocked = 0;
        int unsupported = 0;
        String firstBlocked = null;
        String firstUnsupported = null;
        for (int step = 0; step <= steps; step++) {
            double progress = steps == 0 ? 0 : (double) step / steps;
            int x = (int) Math.round(x1 + (x2 - x1) * progress);
            int z = (int) Math.round(z1 + (z2 - z1) * progress);
            if (world.getBlockAt(x, floorY, z).isPassable()) {
                unsupported++;
                if (firstUnsupported == null) firstUnsupported = x + "," + floorY + "," + z;
            }
            for (int y = floorY + 1; y <= floorY + clearance; y++) {
                if (!world.getBlockAt(x, y, z).isPassable()) {
                    blocked++;
                    if (firstBlocked == null) {
                        firstBlocked = x + "," + y + "," + z + " " + world.getBlockAt(x, y, z).getType();
                    }
                    break;
                }
            }
        }
        String detail = "blocked=" + blocked + ", unsupported=" + unsupported;
        if (firstBlocked != null) detail += ", first-blocked=" + firstBlocked;
        if (firstUnsupported != null) detail += ", first-unsupported=" + firstUnsupported;
        checkAggregate(label, detail,
                blocked == 0 && unsupported == 0);
    }

    private void auditWalkable(String label, World world, int x, int floorY, int z, int clearance) {
        x = atlas.worldX(x);
        z = atlas.worldZ(z);
        boolean floor = !world.getBlockAt(x, floorY, z).isPassable();
        boolean clear = true;
        for (int y = floorY + 1; y <= floorY + clearance; y++) {
            if (!world.getBlockAt(x, y, z).isPassable()) clear = false;
        }
        checkAggregate(label, "floor=" + floor + ", clear=" + clear, floor && clear);
    }

    private void auditSteppedPath(String label, World world,
                                  int startX, int startZ, int startY,
                                  int endX, int endZ, int endY, int halfWidth) {
        int steps = Math.max(Math.abs(endX - startX), Math.abs(endZ - startZ));
        boolean xDominant = Math.abs(endX - startX) >= Math.abs(endZ - startZ);
        int blocked = 0;
        int unsupported = 0;
        String firstFailure = null;
        for (int step = 0; step <= steps; step++) {
            double progress = steps == 0 ? 0.0 : (double) step / steps;
            int x = (int) Math.round(startX + (endX - startX) * progress);
            int z = (int) Math.round(startZ + (endZ - startZ) * progress);
            int y = (int) Math.round(startY + (endY - startY) * progress);
            for (int offset = -halfWidth; offset <= halfWidth; offset++) {
                int modelX = xDominant ? x : x + offset;
                int modelZ = xDominant ? z + offset : z;
                for (int dx = 0; dx < atlas.coordinateScale(); dx++) {
                    for (int dz = 0; dz < atlas.coordinateScale(); dz++) {
                        int px = atlas.worldX(modelX) + dx;
                        int pz = atlas.worldZ(modelZ) + dz;
                        if (world.getBlockAt(px, y, pz).isPassable()) {
                            unsupported++;
                            if (firstFailure == null) firstFailure = "floor " + px + "," + y + "," + pz;
                        }
                        for (int py = y + 1; py <= y + 3; py++) {
                            if (!world.getBlockAt(px, py, pz).isPassable()) {
                                blocked++;
                                if (firstFailure == null) {
                                    firstFailure = "headroom " + px + "," + py + "," + pz;
                                }
                                break;
                            }
                        }
                    }
                }
            }
        }
        String detail = "blocked=" + blocked + ", unsupported=" + unsupported;
        if (firstFailure != null) detail += ", first=" + firstFailure;
        checkAggregate(label, detail, blocked == 0 && unsupported == 0);
    }

    private void auditClearBox(String label, World world, int x1, int y1, int z1,
                               int x2, int y2, int z2) {
        int modelMinX = Math.min(x1, x2);
        int modelMaxX = Math.max(x1, x2);
        int modelMinZ = Math.min(z1, z2);
        int modelMaxZ = Math.max(z1, z2);
        x1 = atlas.worldX(modelMinX);
        x2 = atlas.worldX(modelMaxX) + atlas.coordinateScale() - 1;
        z1 = atlas.worldZ(modelMinZ);
        z2 = atlas.worldZ(modelMaxZ) + atlas.coordinateScale() - 1;
        int blocked = 0;
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    if (!world.getBlockAt(x, y, z).isPassable()) blocked++;
                }
            }
        }
        checkAggregate(label, "blocked blocks=" + blocked, blocked == 0);
    }

    private void auditContinuousColumn(String label, World world, int x, int y1, int z, int y2) {
        x = atlas.worldX(x);
        z = atlas.worldZ(z);
        int gaps = 0;
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
            if (world.getBlockAt(x, y, z).isPassable()) gaps++;
        }
        checkAggregate(label, "gaps=" + gaps, gaps == 0);
    }

    private void expectMaterial(String label, World world, int x, int y, int z, Material expected) {
        x = atlas.worldX(x);
        z = atlas.worldZ(z);
        Material actual = world.getBlockAt(x, y, z).getType();
        checkAggregate(label, "expected=" + expected + ", actual=" + actual, actual == expected);
    }

    private void expectSolid(String label, World world, int x, int y, int z) {
        x = atlas.worldX(x);
        z = atlas.worldZ(z);
        Block block = world.getBlockAt(x, y, z);
        checkAggregate(label, "actual=" + block.getType(), !block.isPassable());
    }

    private void checkAggregate(String label, String detail, boolean passed) {
        checks++;
        if (passed) results.add("PASS | " + label + " | " + detail);
        else fail(label, detail);
    }

    private void fail(String label, String detail) {
        failures++;
        results.add("FAIL | " + label + " | " + detail);
    }

    private void writeReport() {
        File report = new File(plugin.getDataFolder(), "map-audit.txt");
        try {
            Files.write(report.toPath(), results, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            plugin.getLogger().warning("Unable to write map audit report: " + exception.getMessage());
        }
    }
}
