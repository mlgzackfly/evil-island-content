package tw.zack.evilisland.world;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Barrel;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import tw.zack.evilisland.EvilIslandPlugin;

public final class WorldAtlasService implements Listener {
    private final EvilIslandPlugin plugin;
    private final NamespacedKey hasEnteredKey;
    private final int coordinateScale;
    private World mainWorld;
    private World palaceRealm;

    public WorldAtlasService(EvilIslandPlugin plugin) {
        this.plugin = plugin;
        this.hasEnteredKey = new NamespacedKey(plugin, "entered_evil_island");
        this.coordinateScale = Math.max(1, plugin.getConfig().getInt("atlas.coordinate-scale", 1));
    }

    public void loadWorlds() {
        FileConfiguration config = plugin.getConfig();
        String worldName = config.getString("atlas.world-name", "evil_island");
        long seed = config.getLong("atlas.seed", EvilIslandWorldGenerator.DEFAULT_SEED);
        mainWorld = new WorldCreator(worldName)
                .type(WorldType.NORMAL)
                .environment(World.Environment.NORMAL)
                .seed(seed)
                .generator(new EvilIslandWorldGenerator(seed, coordinateScale))
                .createWorld();
        if (mainWorld == null) {
            throw new IllegalStateException("Unable to create Evil Island world " + worldName);
        }

        mainWorld.getWorldBorder().setCenter(worldX(1000), worldZ(0));
        mainWorld.getWorldBorder().setSize(config.getDouble("atlas.border-size", 12000.0) * coordinateScale);
        mainWorld.setSpawnLocation(landmarkLocation(WorldLandmark.NEW_CITY));
        mainWorld.setGameRule(GameRule.KEEP_INVENTORY, true);
        mainWorld.setGameRule(GameRule.DO_INSOMNIA, false);
        mainWorld.setGameRule(GameRule.DO_PATROL_SPAWNING, false);

        if (config.getBoolean("atlas.create-palace-realm", true)) {
            String realmName = config.getString("atlas.palace-realm-name", "evil_island_palace_realm");
            palaceRealm = new WorldCreator(realmName)
                    .type(WorldType.NORMAL)
                    .environment(World.Environment.NORMAL)
                    .seed(seed + 97)
                    .generator(new PalaceRealmGenerator())
                    .createWorld();
            if (palaceRealm != null) {
                palaceRealm.getWorldBorder().setCenter(0, 0);
                palaceRealm.getWorldBorder().setSize(420);
                palaceRealm.setSpawnLocation(0, 93, 0);
                palaceRealm.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                palaceRealm.setTime(18000);
                palaceRealm.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            }
        }

        configureNewCityLocations();
        mainWorld.getChunkAt(worldX(WorldLandmark.NEW_CITY.x()) >> 4,
                worldZ(WorldLandmark.NEW_CITY.z()) >> 4).load();
        provisionInteractiveSites();
        plugin.getLogger().info("Loaded atlas world '" + mainWorld.getName() + "' with "
                + WorldLandmark.values().length + " indexed landmarks at " + coordinateScale + "x scale.");
    }

    @EventHandler
    public void onRealmGate(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        Location clicked = event.getClickedBlock().getLocation();
        Player player = event.getPlayer();
        if (isMainWorld(clicked.getWorld())
                && isMainModelBlock(clicked, 2500, 28, -1900) && palaceRealm != null) {
            event.setCancelled(true);
            player.teleportAsync(new Location(palaceRealm, 0.5, 93, 0.5));
            player.sendMessage(EvilIslandPlugin.message("龍宮的界門將你送入內層領域。", NamedTextColor.AQUA));
        } else if (palaceRealm != null && palaceRealm.equals(clicked.getWorld()) && isBlock(clicked, 0, 92, 0)) {
            event.setCancelled(true);
            player.teleportAsync(new Location(mainWorld,
                    worldX(2500) + coordinateScale / 2.0, 29,
                    worldZ(-1900) + coordinateScale / 2.0));
            player.sendMessage(EvilIslandPlugin.message("你穿過界門，返回龍宮。", NamedTextColor.AQUA));
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getConfig().getBoolean("atlas.auto-enter-new-players", true)) return;
        if (player.getPersistentDataContainer().has(hasEnteredKey, PersistentDataType.BYTE)) return;

        player.getPersistentDataContainer().set(hasEnteredKey, PersistentDataType.BYTE, (byte) 1);
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.teleportAsync(landmarkLocation(WorldLandmark.NEW_CITY));
            player.sendMessage(EvilIslandPlugin.message("你在東大陸新城醒來。海的西方，是噩盡島。", NamedTextColor.AQUA));
            player.sendMessage(Component.text("沿西門道路前往港口；渡海後可抵歲安城、宇定高原與西方荒野。", NamedTextColor.GRAY));
        });
    }

    public Location landmarkLocation(WorldLandmark landmark) {
        int y = switch (landmark) {
            case NEW_CITY -> 77;
            case SUI_AN -> 79;
            case QINGTIAN_TOWER -> 199;
            case JIUHUI_CITY -> 30;
            case RONGXU_CAVE -> 103;
            case DRAGON_PALACE -> 29;
            default -> mainWorld == null ? 80
                    : mainWorld.getHighestBlockYAt(worldX(landmark.x()), worldZ(landmark.z())) + 1;
        };
        return new Location(mainWorld,
                worldX(landmark.x()) + coordinateScale / 2.0,
                y,
                worldZ(landmark.z()) + coordinateScale / 2.0);
    }

    public World mainWorld() {
        return mainWorld;
    }

    public World palaceRealm() {
        return palaceRealm;
    }

    public boolean isMainWorld(World world) {
        return mainWorld != null && mainWorld.equals(world);
    }

    public int coordinateScale() {
        return coordinateScale;
    }

    public int worldX(int modelX) {
        return modelX * coordinateScale;
    }

    public int worldZ(int modelZ) {
        return modelZ * coordinateScale;
    }

    public int modelX(int worldX) {
        return Math.floorDiv(worldX, coordinateScale);
    }

    public int modelZ(int worldZ) {
        return Math.floorDiv(worldZ, coordinateScale);
    }

    public String worldStatePath(String key) {
        return "atlas.world-state." + mainWorld.getName() + "." + key;
    }

    public void scheduleLandmarkVerification() {
        Bukkit.getScheduler().runTask(plugin, this::verifyLandmarks);
    }

    private void provisionInteractiveSites() {
        String siteVersionPath = worldStatePath("site-version");
        int siteVersion = plugin.getConfig().getInt(siteVersionPath, 0);
        if (siteVersion < 1) {
            buildNewCityPort();
            buildSuiAnRiverDock();
            plugin.getConfig().set(siteVersionPath, 1);
            plugin.saveConfig();
        }

        mainWorld.getBlockAt(worldX(2500), 28, worldZ(-1900)).setType(Material.LODESTONE);
        if (palaceRealm != null) palaceRealm.getBlockAt(0, 92, 0).setType(Material.LODESTONE);
    }

    private void buildNewCityPort() {
        for (int x = 3970; x <= 4125; x++) {
            int y;
            if (x <= 4010) {
                y = 64;
            } else if (x < 4070) {
                y = 64 + (x - 4010) * 13 / 60;
            } else {
                y = 77;
            }
            for (int z = -4; z <= 4; z++) {
                setScaledBlock(x, y, z, Material.DARK_OAK_PLANKS);
                setScaledBlock(x, y + 1, z, Material.AIR);
                setScaledBlock(x, y + 2, z, Material.AIR);
                if (Math.abs(z) == 4 && x % 6 == 0) {
                    setScaledBlock(x, y + 1, z, Material.DARK_OAK_FENCE);
                }
            }
        }
        stockBoatBarrel(mainWorld, 4000, 65, 6);
    }

    private void buildSuiAnRiverDock() {
        for (int x = 410; x <= 555; x++) {
            int y = x <= 460 ? 63 : 63 + (x - 460) * 15 / 95;
            for (int z = -4; z <= 4; z++) {
                setScaledBlock(x, y, z, Material.DARK_OAK_PLANKS);
                setScaledBlock(x, y + 1, z, Material.AIR);
                setScaledBlock(x, y + 2, z, Material.AIR);
            }
        }
        stockBoatBarrel(mainWorld, 420, 64, 6);
    }

    private void stockBoatBarrel(World world, int x, int y, int z) {
        int scaledX = worldX(x);
        int scaledZ = worldZ(z);
        world.getBlockAt(scaledX, y, scaledZ).setType(Material.BARREL);
        if (world.getBlockAt(scaledX, y, scaledZ).getState() instanceof Barrel barrel) {
            barrel.getInventory().clear();
            for (int slot = 0; slot < 9; slot++) {
                barrel.getInventory().setItem(slot, new ItemStack(Material.DARK_OAK_BOAT));
            }
            barrel.update(true);
        }
    }

    private void verifyLandmarks() {
        int passed = 0;
        passed += verify("東大陸新城煉化臺", 4352, 78, 54, Material.SMITHING_TABLE);
        passed += verify("東大陸新城聚炁鏡", 4250, 78, 55, Material.LODESTONE);
        passed += verify("擎天塔塔頂", 700, 198, 0, Material.SEA_LANTERN);
        passed += verify("歲安城外環大道", 700, 78, -396, Material.POLISHED_BLACKSTONE_BRICKS);
        passed += verify("九回城地面", 1250, 28, 0, Material.DEEPSLATE_TILES);
        int passY = EvilIslandShape.surfaceHeight(1540, -80);
        passed += verify("山口鎮主路", 1540, passY, -80, Material.POLISHED_BLACKSTONE_BRICKS);
        passed += verify("絨須洞通道", 1320, 101, 620, Material.SMOOTH_STONE);
        int magicTop = EvilIslandShape.surfaceHeight(3420, 1750) + 55;
        passed += verify("魔法島中央塔", 3420, magicTop, 1750, Material.AMETHYST_BLOCK);
        passed += verify("龍宮界門", 2500, 28, -1900, Material.LODESTONE);
        if (palaceRealm != null && palaceRealm.getBlockAt(0, 92, 0).getType() == Material.LODESTONE) passed++;
        plugin.getLogger().info("Atlas landmark block verification: " + passed + "/10 passed.");
    }

    private int verify(String label, int x, int y, int z, Material expected) {
        int scaledX = worldX(x);
        int scaledZ = worldZ(z);
        Material actual = mainWorld.getBlockAt(scaledX, y, scaledZ).getType();
        if (actual == expected) return 1;
        plugin.getLogger().warning(label + " verification failed at "
                + scaledX + "," + y + "," + scaledZ
                + ": expected " + expected + ", got " + actual);
        return 0;
    }

    private boolean isBlock(Location location, int x, int y, int z) {
        return location.getBlockX() == x && location.getBlockY() == y && location.getBlockZ() == z;
    }

    private boolean isMainModelBlock(Location location, int modelX, int y, int modelZ) {
        return modelX(location.getBlockX()) == modelX
                && location.getBlockY() == y
                && modelZ(location.getBlockZ()) == modelZ;
    }

    private void configureNewCityLocations() {
        FileConfiguration config = plugin.getConfig();
        setLocation(config, "new-city.center", worldX(4300) + coordinateScale / 2.0,
                77, worldZ(0) + coordinateScale / 2.0);
        setLocation(config, "new-city.refinery", worldX(4352), 78, worldZ(54));
        setLocation(config, "new-city.mirror", worldX(4250), 78, worldZ(55));
        plugin.saveConfig();
    }

    private void setLocation(FileConfiguration config, String path, double x, double y, double z) {
        config.set(path, null);
        config.set(path + ".world", mainWorld.getName());
        config.set(path + ".x", x);
        config.set(path + ".y", y);
        config.set(path + ".z", z);
    }

    private void setScaledBlock(int modelX, int y, int modelZ, Material material) {
        int baseX = worldX(modelX);
        int baseZ = worldZ(modelZ);
        for (int dx = 0; dx < coordinateScale; dx++) {
            for (int dz = 0; dz < coordinateScale; dz++) {
                mainWorld.getBlockAt(baseX + dx, y, baseZ + dz).setType(material, false);
            }
        }
    }
}
