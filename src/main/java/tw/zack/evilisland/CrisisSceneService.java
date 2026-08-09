package tw.zack.evilisland;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import tw.zack.evilisland.model.CrisisSceneBlockSnapshot;
import tw.zack.evilisland.model.CrisisSceneSnapshot;
import tw.zack.evilisland.model.CrisisSceneState;
import tw.zack.evilisland.model.ExplorationSite;
import tw.zack.evilisland.model.LivingEventSnapshot;
import tw.zack.evilisland.model.LivingEventState;
import tw.zack.evilisland.model.LivingEventType;
import tw.zack.evilisland.persistence.CrisisSceneRepository;
import tw.zack.evilisland.persistence.DatabaseManager;
import tw.zack.evilisland.world.WorldAtlasService;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class CrisisSceneService {
    private static final Set<Material> UNSAFE_GROUND = Set.of(
            Material.WATER, Material.LAVA, Material.GRAVEL, Material.SAND, Material.RED_SAND,
            Material.POLISHED_ANDESITE, Material.SMOOTH_STONE, Material.STONE_BRICKS,
            Material.DEEPSLATE_BRICKS, Material.MUD_BRICKS, Material.DARK_OAK_PLANKS,
            Material.FARMLAND, Material.RAIL, Material.POWERED_RAIL);
    private static final Set<Material> REPLACEABLE = Set.of(
            Material.AIR, Material.CAVE_AIR, Material.VOID_AIR, Material.TALL_GRASS,
            Material.FERN, Material.LARGE_FERN, Material.SNOW, Material.VINE,
            Material.DEAD_BUSH);

    private final EvilIslandPlugin plugin;
    private final DatabaseManager database;
    private final CrisisSceneRepository repository;
    private final WorldAtlasService atlas;
    private final DynmapMarkers dynmap;
    private final Map<UUID, CrisisSceneSnapshot> scenes = new HashMap<>();
    private final Set<UUID> pending = new HashSet<>();
    private final Set<UUID> closed = new HashSet<>();

    public CrisisSceneService(EvilIslandPlugin plugin, DatabaseManager database,
                              CrisisSceneRepository repository, WorldAtlasService atlas) {
        this.plugin = plugin;
        this.database = database;
        this.repository = repository;
        this.atlas = atlas;
        this.dynmap = new DynmapMarkers(plugin);
    }

    public void load() {
        scenes.clear();
        scenes.putAll(repository.loadScenes());
        scenes.values().forEach(dynmap::upsert);
    }

    public void reconcile(List<LivingEventSnapshot> history, LivingEventSnapshot active) {
        Map<UUID, LivingEventSnapshot> events = new HashMap<>();
        if (history != null) history.forEach(event -> events.put(event.id(), event));
        for (CrisisSceneSnapshot scene : List.copyOf(scenes.values())) {
            LivingEventSnapshot event = events.get(scene.eventId());
            if (scene.state() == CrisisSceneState.ACTIVE && event != null
                    && event.state() != LivingEventState.ACTIVE) {
                finish(event);
            } else {
                dynmap.upsert(scene);
            }
        }
        if (active != null) activate(active);
    }

    public void activate(LivingEventSnapshot event) {
        if (event == null || event.state() != LivingEventState.ACTIVE || pending.contains(event.id())) return;
        CrisisSceneSnapshot existing = scenes.get(event.id());
        if (existing != null) {
            verifyExisting(event, existing);
            return;
        }
        Location target = siteLocation(event.type().region());
        if (target == null || target.getWorld() == null) return;
        pending.add(event.id());
        target.getWorld().getChunkAtAsync(target).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                if (!closed.contains(event.id()) && !scenes.containsKey(event.id())) {
                    removeOlderTrace(event.type().region(), event.id());
                    place(event, target);
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Cannot create crisis scene " + event.id(), exception);
            } finally {
                pending.remove(event.id());
            }
        }));
    }

    public void finish(LivingEventSnapshot event) {
        if (event == null || event.state() == LivingEventState.ACTIVE) return;
        closed.add(event.id());
        CrisisSceneSnapshot scene = scenes.get(event.id());
        if (scene == null) return;
        CrisisSceneState target = event.state() == LivingEventState.RESOLVED
                ? CrisisSceneState.RESOLVED : CrisisSceneState.EXPIRED;
        transition(scene, target);
    }

    public int blueprintSize(LivingEventType type) {
        return blueprint(type).size();
    }

    public String blueprintSignature(LivingEventType type) {
        return blueprint(type).stream().map(block -> block.dx + ":" + block.dy + ":" + block.dz + ":"
                + block.active.name()).sorted().reduce((left, right) -> left + "|" + right).orElse("");
    }

    public boolean outcomesDiffer(LivingEventType type) {
        return blueprint(type).stream().anyMatch(block -> block.resolved != block.expired
                && block.active != block.resolved && block.active != block.expired);
    }

    public String sceneSummary(UUID eventId) {
        CrisisSceneSnapshot scene = scenes.get(eventId);
        if (scene == null) return pending.contains(eventId) ? "現場正在定位" : "現場尚未安全建立";
        return "現場：X " + scene.x() + " Y " + scene.y() + " Z " + scene.z()
                + "　" + scene.state().display();
    }

    public Location sceneLocation(UUID eventId) {
        CrisisSceneSnapshot scene = scenes.get(eventId);
        if (scene == null) return null;
        World world = Bukkit.getWorld(scene.world());
        return world == null ? null : new Location(world, scene.x() + 0.5, scene.y() + 1.0, scene.z() + 0.5);
    }

    public int runSelfTest(LivingEventSnapshot active) {
        int checks = 0;
        if (java.util.Arrays.stream(LivingEventType.values()).allMatch(type -> blueprintSize(type) >= 18)) checks++;
        if (java.util.Arrays.stream(LivingEventType.values()).map(this::blueprintSignature).distinct().count()
                == LivingEventType.values().length) checks++;
        if (java.util.Arrays.stream(LivingEventType.values()).allMatch(this::outcomesDiffer)) checks++;
        if (scenes.values().stream().allMatch(scene -> !repository.loadBlocks(scene.eventId()).isEmpty())) checks++;
        if (active != null && scenes.containsKey(active.id())) checks++;
        if (active != null && scenes.get(active.id()).state() == CrisisSceneState.ACTIVE) checks++;
        return checks;
    }

    public void flush() {
        List<CrisisSceneSnapshot> snapshots = List.copyOf(scenes.values());
        database.submit(() -> snapshots.forEach(repository::saveScene)).join();
    }

    private void place(LivingEventSnapshot event, Location target) {
        List<SceneBlock> base = blueprint(event.type());
        Location anchor = findSafeAnchor(target, base);
        if (anchor == null || anchor.getWorld() == null) {
            plugin.getLogger().warning("No safe plot found for crisis scene " + event.type().id());
            return;
        }
        World world = anchor.getWorld();
        List<SceneBlock> blueprint = withFoundations(event.type(), anchor, base);
        long now = System.currentTimeMillis();
        CrisisSceneSnapshot scene = new CrisisSceneSnapshot(event.id(), event.type(), CrisisSceneState.ACTIVE,
                world.getName(), anchor.getBlockX(), anchor.getBlockY(), anchor.getBlockZ(), now);
        List<CrisisSceneBlockSnapshot> blocks = new ArrayList<>();
        for (SceneBlock planned : blueprint) {
            Block block = world.getBlockAt(scene.x() + planned.dx, scene.y() + planned.dy, scene.z() + planned.dz);
            String activeData = data(planned.active);
            blocks.add(new CrisisSceneBlockSnapshot(event.id(), world.getName(), block.getX(), block.getY(),
                    block.getZ(), block.getBlockData().getAsString(), activeData, data(planned.resolved),
                    data(planned.expired), activeData));
        }
        repository.saveScene(scene);
        repository.saveBlocks(blocks);
        scenes.put(event.id(), scene);
        for (CrisisSceneBlockSnapshot block : blocks) {
            world.getBlockAt(block.x(), block.y(), block.z()).setBlockData(Bukkit.createBlockData(block.activeData()), false);
        }
        dynmap.upsert(scene);
        plugin.getLogger().info("Placed " + event.type().id() + " crisis scene with " + blocks.size()
                + " owned blocks at " + scene.x() + "," + scene.y() + "," + scene.z() + ".");
    }

    private void verifyExisting(LivingEventSnapshot event, CrisisSceneSnapshot scene) {
        World world = Bukkit.getWorld(scene.world());
        if (world == null) return;
        world.getChunkAtAsync(scene.x() >> 4, scene.z() >> 4).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
            boolean conflict = false;
            List<CrisisSceneBlockSnapshot> updated = new ArrayList<>();
            List<CrisisSceneBlockSnapshot> stored = repository.loadBlocks(event.id());
            if (stored.isEmpty()) {
                repository.deleteScene(event.id());
                scenes.remove(event.id());
                activate(event);
                return;
            }
            for (CrisisSceneBlockSnapshot snapshot : stored) {
                Block block = world.getBlockAt(snapshot.x(), snapshot.y(), snapshot.z());
                String current = block.getBlockData().getAsString();
                if (current.equals(snapshot.originalData())) {
                    block.setBlockData(Bukkit.createBlockData(snapshot.activeData()), false);
                    updated.add(snapshot.withPlacedData(snapshot.activeData()));
                } else if (current.equals(snapshot.placedData()) || current.equals(snapshot.activeData())) {
                    updated.add(snapshot.withPlacedData(snapshot.activeData()));
                } else {
                    conflict = true;
                    updated.add(snapshot);
                }
            }
            repository.saveBlocks(updated);
            CrisisSceneSnapshot verified = scene.withState(conflict ? CrisisSceneState.CONFLICT
                    : CrisisSceneState.ACTIVE, System.currentTimeMillis());
            repository.saveScene(verified);
            scenes.put(event.id(), verified);
            dynmap.upsert(verified);
        }));
    }

    private void transition(CrisisSceneSnapshot scene, CrisisSceneState target) {
        World world = Bukkit.getWorld(scene.world());
        if (world == null) return;
        world.getChunkAtAsync(scene.x() >> 4, scene.z() >> 4).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
            boolean conflict = false;
            List<CrisisSceneBlockSnapshot> updated = new ArrayList<>();
            for (CrisisSceneBlockSnapshot snapshot : repository.loadBlocks(scene.eventId())) {
                Block block = world.getBlockAt(snapshot.x(), snapshot.y(), snapshot.z());
                String current = block.getBlockData().getAsString();
                String desired = snapshot.dataFor(target);
                if (current.equals(snapshot.placedData())) {
                    block.setBlockData(Bukkit.createBlockData(desired), false);
                    updated.add(snapshot.withPlacedData(desired));
                } else if (current.equals(desired)) {
                    updated.add(snapshot.withPlacedData(desired));
                } else {
                    conflict = true;
                    updated.add(snapshot);
                }
            }
            repository.saveBlocks(updated);
            CrisisSceneSnapshot changed = scene.withState(conflict ? CrisisSceneState.CONFLICT : target,
                    System.currentTimeMillis());
            repository.saveScene(changed);
            scenes.put(scene.eventId(), changed);
            dynmap.upsert(changed);
        }));
    }

    private void removeOlderTrace(ExplorationSite region, UUID except) {
        for (CrisisSceneSnapshot old : List.copyOf(scenes.values())) {
            if (old.eventId().equals(except) || old.type().region() != region
                    || old.state() == CrisisSceneState.ACTIVE) continue;
            World world = Bukkit.getWorld(old.world());
            if (world == null) continue;
            world.getChunkAt(old.x() >> 4, old.z() >> 4).load();
            boolean conflict = false;
            for (CrisisSceneBlockSnapshot snapshot : repository.loadBlocks(old.eventId())) {
                Block block = world.getBlockAt(snapshot.x(), snapshot.y(), snapshot.z());
                String current = block.getBlockData().getAsString();
                if (current.equals(snapshot.placedData())) {
                    block.setBlockData(Bukkit.createBlockData(snapshot.originalData()), false);
                } else if (!current.equals(snapshot.originalData())) {
                    conflict = true;
                }
            }
            if (conflict) {
                CrisisSceneSnapshot changed = old.withState(CrisisSceneState.CONFLICT, System.currentTimeMillis());
                repository.saveScene(changed);
                scenes.put(old.eventId(), changed);
                dynmap.upsert(changed);
            } else {
                repository.deleteScene(old.eventId());
                scenes.remove(old.eventId());
                dynmap.delete(old.eventId());
            }
        }
    }

    private Location findSafeAnchor(Location target, List<SceneBlock> blueprint) {
        World world = target.getWorld();
        int step = Math.max(8, plugin.getConfig().getInt("living-world.scenes.search-step", 10));
        int rings = Math.max(1, plugin.getConfig().getInt("living-world.scenes.search-rings", 12));
        List<int[]> offsets = new ArrayList<>();
        offsets.add(new int[]{0, 0});
        for (int ring = 1; ring <= rings; ring++) {
            for (int x = -ring; x <= ring; x++) {
                offsets.add(new int[]{x * step, -ring * step});
                offsets.add(new int[]{x * step, ring * step});
            }
            for (int z = -ring + 1; z < ring; z++) {
                offsets.add(new int[]{-ring * step, z * step});
                offsets.add(new int[]{ring * step, z * step});
            }
        }
        for (int[] offset : offsets) {
            int x = target.getBlockX() + offset[0];
            int z = target.getBlockZ() + offset[1];
            Integer platformGround = safePlot(world, x, z, blueprint);
            if (platformGround != null) {
                return new Location(world, x, platformGround + 1, z);
            }
        }
        return null;
    }

    private Integer safePlot(World world, int anchorX, int anchorZ, List<SceneBlock> blueprint) {
        Set<String> columns = new HashSet<>();
        for (SceneBlock planned : blueprint) columns.add(planned.dx + ":" + planned.dz);
        int minimum = world.getMaxHeight();
        int maximum = world.getMinHeight();
        for (String column : columns) {
            String[] parts = column.split(":");
            int x = anchorX + Integer.parseInt(parts[0]);
            int z = anchorZ + Integer.parseInt(parts[1]);
            int localGround = world.getHighestBlockYAt(x, z);
            Material ground = world.getBlockAt(x, localGround, z).getType();
            if (!ground.isSolid() || UNSAFE_GROUND.contains(ground)) return null;
            minimum = Math.min(minimum, localGround);
            maximum = Math.max(maximum, localGround);
        }
        int maxSlope = Math.max(0, plugin.getConfig().getInt("living-world.scenes.max-slope", 4));
        if (maximum - minimum > maxSlope) return null;
        for (SceneBlock planned : blueprint) {
            Material current = world.getBlockAt(anchorX + planned.dx, maximum + 1 + planned.dy,
                    anchorZ + planned.dz).getType();
            if (!REPLACEABLE.contains(current)) return null;
        }
        return maximum;
    }

    private List<SceneBlock> withFoundations(LivingEventType type, Location anchor, List<SceneBlock> base) {
        List<SceneBlock> result = new ArrayList<>(base);
        Set<String> floorColumns = new HashSet<>();
        for (SceneBlock planned : base) {
            if (planned.dy == 0) floorColumns.add(planned.dx + ":" + planned.dz);
        }
        Material support = support(type);
        Material decay = decay(type);
        World world = anchor.getWorld();
        for (String column : floorColumns) {
            String[] parts = column.split(":");
            int dx = Integer.parseInt(parts[0]);
            int dz = Integer.parseInt(parts[1]);
            int ground = world.getHighestBlockYAt(anchor.getBlockX() + dx, anchor.getBlockZ() + dz);
            for (int y = ground + 1; y < anchor.getBlockY(); y++) {
                result.add(new SceneBlock(dx, y - anchor.getBlockY(), dz, support, support, decay));
            }
        }
        return List.copyOf(result);
    }

    private Location siteLocation(ExplorationSite site) {
        Location landmark = atlas.landmarkLocation(site.landmark());
        if (landmark == null || landmark.getWorld() == null) return null;
        return landmark.clone().add(site.offsetX() * atlas.coordinateScale(), 0,
                site.offsetZ() * atlas.coordinateScale());
    }

    private List<SceneBlock> blueprint(LivingEventType type) {
        List<SceneBlock> result = new ArrayList<>();
        Material floor = switch (type.arc()) {
            case SAFE_ROAD -> Material.SPRUCE_PLANKS;
            case BORDER_SIGNALS -> Material.COBBLESTONE;
            case OUTLAND_RELATIONS -> Material.COARSE_DIRT;
            case COASTAL_WARNING -> Material.PRISMARINE;
        };
        Material support = support(type);
        Material decay = decay(type);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (Math.abs(x) == 2 && Math.abs(z) == 2) continue;
                result.add(new SceneBlock(x, 0, z, floor, floor, decay));
            }
        }
        result.add(new SceneBlock(-2, 1, 0, support, Material.AIR, decay));
        result.add(new SceneBlock(2, 1, 0, support, Material.AIR, decay));
        result.add(new SceneBlock(-2, 2, 0, support, Material.AIR, Material.AIR));
        result.add(new SceneBlock(2, 2, 0, support, Material.AIR, Material.AIR));
        Material identity = identity(type);
        int markerX = type.ordinal() % 3 - 1;
        int markerZ = (type.ordinal() / 3) % 3 - 1;
        result.add(new SceneBlock(markerX, 1, markerZ, identity, Material.LIME_CONCRETE,
                Material.RED_CONCRETE));
        result.add(new SceneBlock(0, 1, 0, support, support, decay));
        result.add(new SceneBlock(0, 2, 0, identity, Material.LANTERN, Material.SOUL_LANTERN));
        return List.copyOf(result);
    }

    private Material identity(LivingEventType type) {
        return switch (type) {
            case SUPPLY_CART_BLOCKED -> Material.BARREL;
            case LOST_SIGNAL -> Material.LODESTONE;
            case RONGXU_SHELTER -> Material.WHITE_WOOL;
            case HUNTING_BOUNDARY -> Material.TARGET;
            case WESTERN_CARAVAN -> Material.BELL;
            case TIDAL_WARNING -> Material.SEA_LANTERN;
            case WALL_PROBE -> Material.DISPENSER;
            case WIND_RAID -> Material.LIGHTNING_ROD;
            case STOLEN_COMPONENTS -> Material.CUT_COPPER;
            case CIVILIAN_WITHDRAWAL -> Material.YELLOW_WOOL;
            case WESTERN_MUSTER -> Material.BLACK_WOOL;
            case SHORELINE_BREACH -> Material.IRON_BARS;
        };
    }

    private Material support(LivingEventType type) {
        return switch (type.arc()) {
            case SAFE_ROAD -> Material.STRIPPED_SPRUCE_LOG;
            case BORDER_SIGNALS -> Material.STONE_BRICKS;
            case OUTLAND_RELATIONS -> Material.OAK_LOG;
            case COASTAL_WARNING -> Material.DARK_PRISMARINE;
        };
    }

    private Material decay(LivingEventType type) {
        return switch (type.arc()) {
            case SAFE_ROAD -> Material.MUD_BRICKS;
            case BORDER_SIGNALS -> Material.CRACKED_STONE_BRICKS;
            case OUTLAND_RELATIONS -> Material.PODZOL;
            case COASTAL_WARNING -> Material.MOSSY_COBBLESTONE;
        };
    }

    private String data(Material material) {
        return material.createBlockData().getAsString();
    }

    private record SceneBlock(int dx, int dy, int dz, Material active, Material resolved, Material expired) { }

    private static final class DynmapMarkers {
        private final EvilIslandPlugin plugin;
        private boolean warned;

        private DynmapMarkers(EvilIslandPlugin plugin) {
            this.plugin = plugin;
        }

        private void upsert(CrisisSceneSnapshot scene) {
            Plugin dynmap = Bukkit.getPluginManager().getPlugin("dynmap");
            if (dynmap == null || !dynmap.isEnabled()) return;
            try {
                Object api = invoke(dynmap, "getMarkerAPI");
                Object set = invoke(api, "getMarkerSet", "evil_island_crises");
                if (set == null) {
                    set = invoke(api, "createMarkerSet", "evil_island_crises", "噩盡島區域危機", null, true);
                }
                String id = markerId(scene.eventId());
                Object marker = invoke(set, "findMarker", id);
                String label = scene.type().display() + "・" + scene.state().display();
                if (marker == null) {
                    Object icon = invoke(api, "getMarkerIcon", "warning");
                    if (icon == null) icon = invoke(api, "getMarkerIcon", "default");
                    marker = invoke(set, "createMarker", id, label, scene.world(), scene.x() + 0.5,
                            (double) scene.y(), scene.z() + 0.5, icon, true);
                } else {
                    invoke(marker, "setLocation", scene.world(), scene.x() + 0.5,
                            (double) scene.y(), scene.z() + 0.5);
                    invoke(marker, "setLabel", label);
                }
                if (marker != null) invoke(marker, "setDescription", scene.type().summary() + "<br>區域："
                        + scene.type().region().display() + "<br>狀態：" + scene.state().display());
            } catch (ReflectiveOperationException | RuntimeException exception) {
                warn(exception);
            }
        }

        private void delete(UUID eventId) {
            Plugin dynmap = Bukkit.getPluginManager().getPlugin("dynmap");
            if (dynmap == null || !dynmap.isEnabled()) return;
            try {
                Object api = invoke(dynmap, "getMarkerAPI");
                Object set = invoke(api, "getMarkerSet", "evil_island_crises");
                if (set == null) return;
                Object marker = invoke(set, "findMarker", markerId(eventId));
                if (marker != null) invoke(marker, "deleteMarker");
            } catch (ReflectiveOperationException | RuntimeException exception) {
                warn(exception);
            }
        }

        private void warn(Exception exception) {
            if (warned) return;
            warned = true;
            plugin.getLogger().log(Level.WARNING, "Dynmap crisis markers are unavailable", exception);
        }

        private static String markerId(UUID id) {
            return "crisis-" + id;
        }

        private static Object invoke(Object target, String name, Object... args) throws ReflectiveOperationException {
            for (Method method : target.getClass().getMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
                Class<?>[] types = method.getParameterTypes();
                boolean compatible = true;
                for (int index = 0; index < args.length; index++) {
                    if (args[index] == null) continue;
                    Class<?> type = wrap(types[index]);
                    if (!type.isAssignableFrom(args[index].getClass())) {
                        compatible = false;
                        break;
                    }
                }
                if (compatible) {
                    if (!method.canAccess(target)) method.setAccessible(true);
                    return method.invoke(target, args);
                }
            }
            throw new NoSuchMethodException(target.getClass().getName() + "#" + name);
        }

        private static Class<?> wrap(Class<?> type) {
            if (!type.isPrimitive()) return type;
            if (type == boolean.class) return Boolean.class;
            if (type == int.class) return Integer.class;
            if (type == long.class) return Long.class;
            if (type == double.class) return Double.class;
            if (type == float.class) return Float.class;
            if (type == short.class) return Short.class;
            if (type == byte.class) return Byte.class;
            if (type == char.class) return Character.class;
            return type;
        }
    }
}
