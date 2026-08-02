package tw.zack.evilisland;

import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import tw.zack.evilisland.world.EvilIslandWorldGenerator;
import tw.zack.evilisland.world.WorldAtlasService;

public final class DaoFieldService {
    public record Reading(int dao, String region) {
    }

    private final EvilIslandPlugin plugin;
    private final WorldAtlasService atlas;

    public DaoFieldService(EvilIslandPlugin plugin, WorldAtlasService atlas) {
        this.plugin = plugin;
        this.atlas = atlas;
    }

    public boolean isConfigured() {
        return cityCenter() != null && refinery() != null && mirror() != null;
    }

    public void setupNewCity(Player player) {
        Location base = player.getLocation().getBlock().getLocation();
        Location refinery = base.clone().add(3, 0, 0);
        Location mirror = base.clone().add(-3, 0, 0);
        refinery.getBlock().setType(Material.SMITHING_TABLE);
        mirror.getBlock().setType(Material.LODESTONE);

        FileConfiguration config = plugin.getConfig();
        setLocation(config, "new-city.center", base);
        setLocation(config, "new-city.refinery", refinery);
        setLocation(config, "new-city.mirror", mirror);
        plugin.saveConfig();
    }

    public Reading reading(Location location) {
        Location mirror = mirror();
        double mirrorRadius = plugin.getConfig().getDouble("new-city.mirror-radius", 6.0)
                * atlas.coordinateScale();
        if (mirror != null && sameWorld(mirror, location) && mirror.distanceSquared(location) <= mirrorRadius * mirrorRadius) {
            return new Reading(plugin.getConfig().getInt("dao.mirror-field", 67), "聚炁鏡場");
        }

        if (atlas.palaceRealm() != null && atlas.palaceRealm().equals(location.getWorld())) {
            return new Reading(96, "龍宮內層領域");
        }
        if (location.getWorld() != null && atlas.isMainWorld(location.getWorld())) {
            return atlasReading(location.getBlockX(), location.getBlockZ());
        }

        Location center = cityCenter();
        if (center == null || !sameWorld(center, location)) {
            return new Reading(plugin.getConfig().getInt("dao.wilderness", 82), "高道息荒原");
        }

        double horizontalDistance = horizontalDistance(center, location);
        double cityRadius = plugin.getConfig().getDouble("new-city.city-radius", 24.0)
                * atlas.coordinateScale();
        double bufferRadius = plugin.getConfig().getDouble("new-city.buffer-radius", 72.0)
                * atlas.coordinateScale();
        if (horizontalDistance <= cityRadius) {
            return new Reading(plugin.getConfig().getInt("dao.city", 12), "息壤城區");
        }
        if (horizontalDistance <= bufferRadius) {
            return new Reading(plugin.getConfig().getInt("dao.buffer", 43), "東門緩衝帶");
        }
        return new Reading(plugin.getConfig().getInt("dao.wilderness", 82), "高道息荒原");
    }

    private Reading atlasReading(int x, int z) {
        int modelX = atlas.modelX(x);
        int modelZ = atlas.modelZ(z);
        String region = EvilIslandWorldGenerator.canonicalRegion(modelX, modelZ);
        if (region.equals("東大陸") && horizontalDistance(modelX, modelZ, 4300, 0) <= 230) {
            return new Reading(plugin.getConfig().getInt("dao.city", 12), "東大陸新城息壤區");
        }
        if (region.equals("歲安城")) return new Reading(14, "歲安城息壤區");
        if (region.equals("宇定高原")) return new Reading(24, region);
        if (region.equals("九回山")) return new Reading(46, region);
        if (region.equals("魔法島")) return new Reading(74, region);
        if (region.equals("龍宮海域")) return new Reading(91, region);
        if (region.equals("西方荒野")) return new Reading(84, region);
        if (region.equals("噩盡島平原")) return new Reading(61, region);
        if (region.equals("東大陸")) return new Reading(79, "東大陸荒原");
        return new Reading(38, region);
    }

    public Location patrolCenter(World world) {
        Location center = cityCenter();
        if (center == null || !center.getWorld().equals(world)) {
            return null;
        }
        double distance = (plugin.getConfig().getDouble("new-city.buffer-radius", 72.0) + 12.0)
                * atlas.coordinateScale();
        int x = center.getBlockX() + (int) Math.round(distance);
        int z = center.getBlockZ();
        int y = world.getHighestBlockYAt(x, z) + 1;
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    public Location guardPost() {
        Location center = cityCenter();
        if (center == null) {
            return null;
        }
        World world = center.getWorld();
        int x = center.getBlockX() + Math.max(5,
                (plugin.getConfig().getInt("new-city.city-radius", 24) - 4) * atlas.coordinateScale());
        int z = center.getBlockZ();
        int y = world.getHighestBlockYAt(x, z) + 1;
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    public boolean isRefinery(Block block) {
        return block != null && sameScaledBlock(block.getLocation(), refinery());
    }

    public boolean isMirror(Block block) {
        return block != null && sameScaledBlock(block.getLocation(), mirror());
    }

    public Location cityCenter() {
        return getLocation("new-city.center");
    }

    public Location refinery() {
        return getLocation("new-city.refinery");
    }

    public Location mirror() {
        return getLocation("new-city.mirror");
    }

    private Location getLocation(String path) {
        String worldName = plugin.getConfig().getString(path + ".world");
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (world == null || !plugin.getConfig().contains(path + ".x")) return null;
        return new Location(world,
                plugin.getConfig().getDouble(path + ".x"),
                plugin.getConfig().getDouble(path + ".y"),
                plugin.getConfig().getDouble(path + ".z"));
    }

    private void setLocation(FileConfiguration config, String path, Location location) {
        config.set(path, null);
        config.set(path + ".world", location.getWorld().getName());
        config.set(path + ".x", location.getX());
        config.set(path + ".y", location.getY());
        config.set(path + ".z", location.getZ());
    }

    private boolean sameBlock(Location first, Location second) {
        return first != null && second != null && sameWorld(first, second)
                && first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY()
                && first.getBlockZ() == second.getBlockZ();
    }

    private boolean sameScaledBlock(Location first, Location second) {
        if (first == null || second == null || !sameWorld(first, second)
                || first.getBlockY() != second.getBlockY()) {
            return false;
        }
        int scale = atlas.coordinateScale();
        return first.getBlockX() >= second.getBlockX()
                && first.getBlockX() < second.getBlockX() + scale
                && first.getBlockZ() >= second.getBlockZ()
                && first.getBlockZ() < second.getBlockZ() + scale;
    }

    private boolean sameWorld(Location first, Location second) {
        return first.getWorld() != null && first.getWorld().equals(second.getWorld());
    }

    private double horizontalDistance(Location first, Location second) {
        double dx = first.getX() - second.getX();
        double dz = first.getZ() - second.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private double horizontalDistance(int x, int z, int centerX, int centerZ) {
        double dx = x - centerX;
        double dz = z - centerZ;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
