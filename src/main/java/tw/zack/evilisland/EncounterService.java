package tw.zack.evilisland;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import tw.zack.evilisland.model.ObjectiveStage;
import tw.zack.evilisland.model.SpeciesType;

import java.util.Locale;
import java.util.Random;

public final class EncounterService implements Listener {
    private final EvilIslandPlugin plugin;
    private final PlayerProfileService profiles;
    private final DaoFieldService daoFields;
    private final GameItemService items;
    private final SpeciesService species;
    private final WeaponService weapons;
    private final NamespacedKey guardKey;
    private final Random random = new Random();

    public EncounterService(EvilIslandPlugin plugin, PlayerProfileService profiles, DaoFieldService daoFields,
                            GameItemService items, SpeciesService species, WeaponService weapons) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.daoFields = daoFields;
        this.items = items;
        this.species = species;
        this.weapons = weapons;
        guardKey = new NamespacedKey(plugin, "new_city_guard");
    }

    public boolean startPatrol(Player player) {
        Location center = daoFields.patrolCenter(player.getWorld());
        if (center == null) {
            return false;
        }
        long existing = center.getWorld().getNearbyEntities(center, 36, 20, 36).stream()
                .filter(this::isEncounterEnemy)
                .count();
        if (existing > 0) {
            player.sendMessage(EvilIslandPlugin.message("東境巡防點仍有 " + existing + " 名敵對妖物。"));
            return true;
        }

        int count = plugin.getConfig().getInt("encounters.patrol.zaochi-count", 3);
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0 * i / Math.max(1, count) + random.nextDouble() * 0.35;
            Location spawn = ground(center.clone().add(Math.cos(angle) * 6.0, 0, Math.sin(angle) * 6.0));
            species.spawnZaochi(spawn);
        }
        profiles.setObjective(player, ObjectiveStage.HUNT_ZAOCHI);
        player.sendMessage(EvilIslandPlugin.message("鑿齒小隊出現在東門外高道息區。撼山巡防員會守住城門。", NamedTextColor.RED));
        player.sendMessage(EvilIslandPlugin.message("巡防座標：" + center.getBlockX() + ", " + center.getBlockY() + ", " + center.getBlockZ(), NamedTextColor.GRAY));
        return true;
    }

    public void spawnXingtian(Player player) {
        Location center = daoFields.patrolCenter(player.getWorld());
        if (center == null) {
            return;
        }
        boolean exists = center.getWorld().getNearbyEntities(center, 64, 32, 64).stream()
                .anyMatch(entity -> species.type(entity) == SpeciesType.XINGTIAN);
        if (exists) {
            player.sendMessage(EvilIslandPlugin.message("刑天統領已在東境活動。"));
            return;
        }
        Location spawn = ground(center.clone().add(10, 0, 0));
        species.spawnXingtian(spawn);
        species.spawnZaochi(ground(spawn.clone().add(-4, 0, 3)));
        species.spawnZaochi(ground(spawn.clone().add(-4, 0, -3)));
        spawn.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, spawn.clone().add(0, 1, 0), 4, 1.2, 0.5, 1.2, 0.02);
        Bukkit.getServer().broadcast(EvilIslandPlugin.message("刑天統領率眾逼近新城東境。", NamedTextColor.DARK_RED));
    }

    public void setupGuard() {
        Location post = daoFields.guardPost();
        if (post == null) {
            return;
        }
        for (IronGolem golem : post.getWorld().getEntitiesByClass(IronGolem.class)) {
            if (golem.getPersistentDataContainer().has(guardKey, PersistentDataType.BYTE)) {
                golem.remove();
            }
        }
        IronGolem guard = post.getWorld().spawn(post, IronGolem.class);
        guard.getPersistentDataContainer().set(guardKey, PersistentDataType.BYTE, (byte) 1);
        guard.customName(Component.text("撼山巡防員", NamedTextColor.GREEN));
        guard.setCustomNameVisible(true);
        guard.setPlayerCreated(true);
        guard.setPersistent(true);
        setAttribute(guard, Attribute.GENERIC_MAX_HEALTH, 160.0);
        guard.setHealth(160.0);
    }

    public boolean isEncounterEnemy(Entity entity) {
        return species.isSpecies(entity);
    }

    @EventHandler(ignoreCancelled = true)
    public void onGuardInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || !event.getRightClicked().getPersistentDataContainer().has(guardKey, PersistentDataType.BYTE)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!profiles.isMeasured(player)) {
            player.sendMessage(EvilIslandPlugin.message("先到聚炁鏡庭接受炁息測定。"));
            return;
        }
        if (!profiles.isFormulaLocked(player)) {
            player.sendMessage(EvilIslandPlugin.message("你的炁息尚未完成存想定型。"));
            return;
        }
        if (!weapons.hasWeapon(player)) {
            player.sendMessage(EvilIslandPlugin.message("巡防前先領取一件歲安軍團登記兵器。", NamedTextColor.YELLOW));
            weapons.openArmory(player);
            return;
        }
        if (profiles.objective(player) == ObjectiveStage.COMPLETE) {
            player.sendMessage(EvilIslandPlugin.message("本輪東境巡防已經完成。"));
            return;
        }
        if (profiles.transformations(player) > 0) {
            spawnXingtian(player);
        } else {
            startPatrol(player);
        }
    }

    public void spawnForAdmin(Player player, String type) {
        String normalized = type.toLowerCase(Locale.ROOT);
        Location location = ground(player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(5)));
        if (normalized.equals(SpeciesType.XINGTIAN.id())) {
            species.spawnXingtian(location);
        } else {
            species.spawnZaochi(location);
        }
    }

    @EventHandler
    public void onEnemyDeath(EntityDeathEvent event) {
        SpeciesType type = species.type(event.getEntity());
        if (type == null) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        int purity = type == SpeciesType.XINGTIAN ? 3 : 1;
        event.getEntity().getWorld().dropItemNaturally(event.getEntity().getLocation(), items.createRemains(type.id(), purity));

        Player killer = event.getEntity().getKiller();
        if (killer == null || !profiles.isEnlisted(killer)) {
            return;
        }
        if (type == SpeciesType.ZAOCHI) {
            profiles.recordZaochiKill(killer);
            killer.sendMessage(EvilIslandPlugin.message("鑿齒倒下，留下尚待煉化的遺骸。"));
        } else {
            profiles.setObjective(killer, ObjectiveStage.COMPLETE);
            Bukkit.getServer().broadcast(EvilIslandPlugin.message(killer.getName() + " 擊倒刑天統領，東境巡防暫告完成。", NamedTextColor.GREEN));
        }
    }

    private Location ground(Location location) {
        World world = location.getWorld();
        int y = world.getHighestBlockYAt(location.getBlockX(), location.getBlockZ()) + 1;
        return new Location(world, location.getBlockX() + 0.5, y, location.getBlockZ() + 0.5);
    }

    private void setAttribute(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

}
