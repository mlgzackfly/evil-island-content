package tw.zack.evilisland;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.scheduler.BukkitTask;
import tw.zack.evilisland.model.CityProject;
import tw.zack.evilisland.model.ConstructionBlockSnapshot;
import tw.zack.evilisland.model.ConstructionPlot;
import tw.zack.evilisland.model.ConstructionPreviewBlock;
import tw.zack.evilisland.model.ConstructionPreviewPlan;
import tw.zack.evilisland.persistence.ConstructionRepository;
import tw.zack.evilisland.world.WorldAtlasService;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ConstructionService {
    private record RelativeBlock(int x, int y, int z, Material material) { }
    private record Placement(Block block, BlockData data) { }

    private final EvilIslandPlugin plugin;
    private final ConstructionRepository repository;
    private final WorldAtlasService atlas;
    private final DaoFieldService daoFields;
    private final Map<CityProject, ConstructionPlot> plots = new EnumMap<>(CityProject.class);
    private final ArrayDeque<Placement> queue = new ArrayDeque<>();
    private BukkitTask task;

    public ConstructionService(EvilIslandPlugin plugin, ConstructionRepository repository,
                               WorldAtlasService atlas, DaoFieldService daoFields) {
        this.plugin = plugin;
        this.repository = repository;
        this.atlas = atlas;
        this.daoFields = daoFields;
    }

    public void load(Map<CityProject, Integer> levels) {
        plots.putAll(repository.loadPlots());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (CityProject project : CityProject.values()) {
                int level = levels.getOrDefault(project, 0);
                if (level > 0) upgrade(project, level);
            }
        }, 140L);
    }

    public void upgrade(CityProject project, int level) {
        if (level <= 0) return;
        ConstructionPlot plot = plots.get(project);
        if (plot != null && plot.status().equals("pending")) {
            plot = findPlot(project);
            if (plot != null) {
                plots.put(project, plot);
                repository.savePlot(plot);
            }
        }
        if (plot == null) {
            plot = findPlot(project);
            if (plot == null) {
                savePending(project);
                plugin.getLogger().warning(project.display() + "找不到不與道路或建築衝突的工程地塊，保留展示標記。");
                return;
            }
            plots.put(project, plot);
            repository.savePlot(plot);
        }
        World world = Bukkit.getWorld(plot.world());
        if (world == null) return;
        List<ConstructionBlockSnapshot> owned = repository.loadBlocks(project);
        Map<String, ConstructionBlockSnapshot> ownedByPosition = new HashMap<>();
        for (ConstructionBlockSnapshot block : owned) ownedByPosition.put(key(block.x(), block.y(), block.z()), block);

        List<ConstructionBlockSnapshot> additions = new ArrayList<>();
        List<Placement> placements = new ArrayList<>();
        for (RelativeBlock relative : blueprint(project, level)) {
            int x = plot.x() + relative.x();
            int y = plot.y() + relative.y();
            int z = plot.z() + relative.z();
            Block block = world.getBlockAt(x, y, z);
            BlockData desired = Bukkit.createBlockData(relative.material());
            ConstructionBlockSnapshot previous = ownedByPosition.get(key(x, y, z));
            if (previous != null) {
                if (block.getBlockData().getAsString().equals(previous.placedData())) continue;
                if (!block.getBlockData().getAsString().equals(previous.originalData())) {
                    mark(plot, "conflict", Math.min(plot.level(), level));
                    return;
                }
            } else if (!replaceable(block.getType())) {
                mark(plot, "conflict", Math.min(plot.level(), level));
                return;
            }
            additions.add(new ConstructionBlockSnapshot(project, world.getName(), x, y, z,
                    previous == null ? block.getBlockData().getAsString() : previous.originalData(),
                    desired.getAsString()));
            placements.add(new Placement(block, desired));
        }
        repository.saveBlocks(additions);
        queue.addAll(placements);
        mark(plot, placements.isEmpty() ? "complete" : "building", level);
        startQueue();
    }

    public String status(CityProject project) {
        ConstructionPlot plot = plots.get(project);
        return plot == null ? "待選址" : switch (plot.status()) {
            case "complete" -> "實體工程已完成";
            case "building" -> "實體工程施工中";
            case "conflict" -> "地塊有變動，暫停施工";
            default -> "待選址";
        };
    }

    public int runSelfTest() {
        int checks = 0;
        if (blueprint(CityProject.WALLS, 1).size() > 20) checks++;
        if (blueprint(CityProject.WALLS, 3).size() > blueprint(CityProject.WALLS, 1).size()) checks++;
        if (blueprint(CityProject.QI_MIRROR, 3).stream().anyMatch(block -> block.material() == Material.LODESTONE)) checks++;
        if (blueprint(CityProject.WORKSHOP, 2).stream().anyMatch(block -> block.material() == Material.SMITHING_TABLE)) checks++;
        return checks;
    }

    public int blueprintSize(CityProject project, int level) {
        return blueprint(project, level).size();
    }

    public Optional<ConstructionPreviewPlan> planAcceptance(CityProject project, int level,
                                                            Set<String> reservedColumns) {
        Location center = daoFields.cityCenter();
        if (center == null || center.getWorld() == null) return Optional.empty();
        int scale = atlas.coordinateScale();
        int[] preferred = preferredOffset(project);
        int step = 8 * scale;
        for (int radius = 0; radius <= 7; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                    int x = center.getBlockX() + preferred[0] * scale + dx * step;
                    int z = center.getBlockZ() + preferred[1] * scale + dz * step;
                    ConstructionPlot plot = safePlot(project, center.getWorld(), x, z, reservedColumns);
                    if (plot == null) continue;
                    List<ConstructionPreviewBlock> blocks = new ArrayList<>();
                    boolean valid = true;
                    for (RelativeBlock relative : blueprint(project, level)) {
                        Block target = center.getWorld().getBlockAt(plot.x() + relative.x(),
                                plot.y() + relative.y(), plot.z() + relative.z());
                        if (!replaceable(target.getType())) {
                            valid = false;
                            break;
                        }
                        blocks.add(new ConstructionPreviewBlock(center.getWorld().getName(), target.getX(),
                                target.getY(), target.getZ(),
                                Bukkit.createBlockData(relative.material()).getAsString()));
                    }
                    if (valid) return Optional.of(new ConstructionPreviewPlan(project,
                            Math.max(1, Math.min(3, level)), center.getWorld().getName(), plot.x(), plot.y(),
                            plot.z(), blocks));
                }
            }
        }
        return Optional.empty();
    }

    private ConstructionPlot findPlot(CityProject project) {
        Location center = daoFields.cityCenter();
        if (center == null || center.getWorld() == null) return null;
        int scale = atlas.coordinateScale();
        int[] preferred = preferredOffset(project);
        int step = 8 * scale;
        for (int radius = 0; radius <= 4; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                    int x = center.getBlockX() + preferred[0] * scale + dx * step;
                    int z = center.getBlockZ() + preferred[1] * scale + dz * step;
                    ConstructionPlot candidate = safePlot(project, center.getWorld(), x, z);
                    if (candidate != null) return candidate;
                }
            }
        }
        return null;
    }

    private ConstructionPlot safePlot(CityProject project, World world, int centerX, int centerZ) {
        return safePlot(project, world, centerX, centerZ, Set.of());
    }

    private ConstructionPlot safePlot(CityProject project, World world, int centerX, int centerZ,
                                      Set<String> reservedColumns) {
        Location city = daoFields.cityCenter();
        if (city == null || Math.abs(centerX - city.getBlockX()) < 12 || Math.abs(centerZ - city.getBlockZ()) < 12) {
            return null;
        }
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        Set<Material> forbiddenGround = Set.of(Material.POLISHED_ANDESITE, Material.SMOOTH_STONE,
                Material.STONE_BRICKS, Material.DEEPSLATE_BRICKS, Material.MUD_BRICKS,
                Material.DARK_OAK_PLANKS, Material.FARMLAND, Material.WATER, Material.GRAVEL);
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (reservedColumns.contains(columnKey(centerX + dx, centerZ + dz))) return null;
                int y = world.getHighestBlockYAt(centerX + dx, centerZ + dz);
                Material ground = world.getBlockAt(centerX + dx, y, centerZ + dz).getType();
                if (forbiddenGround.contains(ground)) return null;
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                for (int dy = 1; dy <= 9; dy++) {
                    if (!replaceable(world.getBlockAt(centerX + dx, y + dy, centerZ + dz).getType())) return null;
                }
            }
        }
        if (maxY - minY > 1) return null;
        return new ConstructionPlot(project, world.getName(), centerX, maxY + 1, centerZ, 0, 0, "ready");
    }

    private void savePending(CityProject project) {
        Location center = daoFields.cityCenter();
        if (center == null || center.getWorld() == null) return;
        ConstructionPlot pending = new ConstructionPlot(project, center.getWorld().getName(), center.getBlockX(),
                center.getBlockY(), center.getBlockZ(), 0, 0, "pending");
        plots.put(project, pending);
        repository.savePlot(pending);
    }

    private void mark(ConstructionPlot old, String status, int level) {
        ConstructionPlot updated = new ConstructionPlot(old.project(), old.world(), old.x(), old.y(), old.z(),
                old.rotation(), level, status);
        plots.put(old.project(), updated);
        repository.savePlot(updated);
    }

    private void startQueue() {
        if (task != null || queue.isEmpty()) {
            if (queue.isEmpty()) completeBuildingPlots();
            return;
        }
        int limit = Math.max(10, plugin.getConfig().getInt("development.construction.blocks-per-tick", 80));
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int placed = 0;
            while (placed++ < limit && !queue.isEmpty()) {
                Placement placement = queue.removeFirst();
                placement.block().setBlockData(placement.data(), false);
            }
            if (!queue.isEmpty()) return;
            task.cancel();
            task = null;
            completeBuildingPlots();
        }, 1L, 1L);
    }

    private void completeBuildingPlots() {
        for (ConstructionPlot plot : new ArrayList<>(plots.values())) {
            if (plot.status().equals("building")) mark(plot, "complete", plot.level());
        }
    }

    private List<RelativeBlock> blueprint(CityProject project, int requestedLevel) {
        int level = Math.max(1, Math.min(3, requestedLevel));
        List<RelativeBlock> blocks = new ArrayList<>();
        Material base = switch (project) {
            case WALLS -> Material.STONE_BRICKS;
            case QI_MIRROR -> Material.QUARTZ_BRICKS;
            case WORKSHOP -> Material.BRICKS;
            case SCOUT_POST -> Material.STRIPPED_OAK_LOG;
            case AIR_DEFENSE -> Material.DEEPSLATE_BRICKS;
        };
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            if (Math.abs(x) == 3 || Math.abs(z) == 3) blocks.add(new RelativeBlock(x, 0, z, base));
        }
        for (int[] corner : new int[][]{{-3, -3}, {-3, 3}, {3, -3}, {3, 3}}) {
            for (int y = 1; y <= 2 + level; y++) blocks.add(new RelativeBlock(corner[0], y, corner[1], base));
        }
        if (level >= 2) {
            for (int x = -3; x <= 3; x++) blocks.add(new RelativeBlock(x, 3, -3, roof(project)));
            for (int z = -2; z <= 3; z++) blocks.add(new RelativeBlock(3, 3, z, roof(project)));
        }
        if (level >= 3) {
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                if ((Math.abs(x) + Math.abs(z)) % 2 == 0) blocks.add(new RelativeBlock(x, 4, z, roof(project)));
            }
        }
        addIdentityBlocks(blocks, project, level);
        return deduplicate(blocks);
    }

    private void addIdentityBlocks(List<RelativeBlock> blocks, CityProject project, int level) {
        switch (project) {
            case WALLS -> {
                for (int x = -2; x <= 2; x++) blocks.add(new RelativeBlock(x, 1, 0, Material.MUD_BRICKS));
                if (level >= 3) blocks.add(new RelativeBlock(0, 4, 0, Material.BELL));
            }
            case QI_MIRROR -> {
                blocks.add(new RelativeBlock(0, 1, 0, Material.LODESTONE));
                if (level >= 2) for (int[] p : new int[][]{{2, 1, 0}, {-2, 1, 0}, {0, 1, 2}, {0, 1, -2}})
                    blocks.add(new RelativeBlock(p[0], p[1], p[2], Material.SEA_LANTERN));
            }
            case WORKSHOP -> {
                blocks.add(new RelativeBlock(0, 1, 0, Material.SMITHING_TABLE));
                if (level >= 2) blocks.add(new RelativeBlock(2, 1, 1, Material.ANVIL));
                if (level >= 3) blocks.add(new RelativeBlock(-2, 1, 1, Material.BLAST_FURNACE));
            }
            case SCOUT_POST -> {
                for (int y = 1; y <= 3 + level; y++) blocks.add(new RelativeBlock(0, y, 0, Material.LADDER));
                blocks.add(new RelativeBlock(0, 4 + level, 0, Material.CAMPFIRE));
            }
            case AIR_DEFENSE -> {
                blocks.add(new RelativeBlock(0, 1, 0, Material.DISPENSER));
                if (level >= 2) blocks.add(new RelativeBlock(0, 2, 0, Material.IRON_BARS));
                if (level >= 3) blocks.add(new RelativeBlock(0, 3, 0, Material.LIGHTNING_ROD));
            }
        }
    }

    private Material roof(CityProject project) {
        return switch (project) {
            case WALLS, AIR_DEFENSE -> Material.DEEPSLATE_TILE_SLAB;
            case QI_MIRROR -> Material.SMOOTH_QUARTZ_SLAB;
            case WORKSHOP -> Material.WAXED_CUT_COPPER_SLAB;
            case SCOUT_POST -> Material.DARK_OAK_SLAB;
        };
    }

    private List<RelativeBlock> deduplicate(List<RelativeBlock> blocks) {
        Map<String, RelativeBlock> unique = new HashMap<>();
        for (RelativeBlock block : blocks) unique.put(key(block.x(), block.y(), block.z()), block);
        return List.copyOf(unique.values());
    }

    private int[] preferredOffset(CityProject project) {
        return switch (project) {
            case WALLS -> new int[]{168, 68};
            case QI_MIRROR -> new int[]{-38, 76};
            case WORKSHOP -> new int[]{112, -150};
            case SCOUT_POST -> new int[]{84, 145};
            case AIR_DEFENSE -> new int[]{-114, -142};
        };
    }

    private boolean replaceable(Material material) {
        return material.isAir() || material == Material.TALL_GRASS
                || material == Material.SNOW || material == Material.VINE;
    }

    private String key(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }

    public static String columnKey(int x, int z) {
        return x + ":" + z;
    }
}
