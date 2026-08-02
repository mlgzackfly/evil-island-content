package tw.zack.evilisland;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.PiglinBrute;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vindicator;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import tw.zack.evilisland.model.SpeciesType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class SpeciesService {
    private final EvilIslandPlugin plugin;
    private final PlayerProfileService profiles;
    private final NamespacedKey speciesKey;
    private final Set<UUID> tracked = new HashSet<>();
    private final Map<UUID, Long> leapReadyAt = new HashMap<>();
    private final Map<UUID, Long> slamReadyAt = new HashMap<>();
    private final Random random = new Random();

    public SpeciesService(EvilIslandPlugin plugin, PlayerProfileService profiles) {
        this.plugin = plugin;
        this.profiles = profiles;
        speciesKey = new NamespacedKey(plugin, "species_type");
    }

    public void recover(World world) {
        for (Entity entity : world.getEntities()) {
            if (type(entity) != null) {
                tracked.add(entity.getUniqueId());
            }
        }
    }

    public LivingEntity spawnZaochi(Location location) {
        PiglinBrute mob = location.getWorld().spawn(location, PiglinBrute.class);
        mob.setImmuneToZombification(true);
        configure(mob, SpeciesType.ZAOCHI,
                plugin.getConfig().getDouble("encounters.zaochi.health", 34.0),
                plugin.getConfig().getDouble("encounters.zaochi.attack", 6.0),
                plugin.getConfig().getDouble("encounters.zaochi.speed", 0.30));
        equip(mob, new ItemStack(Material.STONE_AXE));
        return mob;
    }

    public LivingEntity spawnXingtian(Location location) {
        Vindicator mob = location.getWorld().spawn(location, Vindicator.class);
        configure(mob, SpeciesType.XINGTIAN,
                plugin.getConfig().getDouble("encounters.xingtian.health", 140.0),
                plugin.getConfig().getDouble("encounters.xingtian.attack", 13.0),
                plugin.getConfig().getDouble("encounters.xingtian.speed", 0.27));
        equip(mob, new ItemStack(Material.IRON_AXE));
        return mob;
    }

    public boolean isSpecies(Entity entity) {
        return type(entity) != null;
    }

    public SpeciesType type(Entity entity) {
        String id = entity.getPersistentDataContainer().get(speciesKey, PersistentDataType.STRING);
        return SpeciesType.parse(id);
    }

    public void tick() {
        long now = System.currentTimeMillis();
        for (UUID id : Set.copyOf(tracked)) {
            Entity entity = Bukkit.getEntity(id);
            if (!(entity instanceof Mob mob) || !entity.isValid() || entity.isDead()) {
                tracked.remove(id);
                leapReadyAt.remove(id);
                slamReadyAt.remove(id);
                continue;
            }
            SpeciesType type = type(entity);
            if (type == null) {
                tracked.remove(id);
                continue;
            }
            Player target = nearestTarget(mob, type == SpeciesType.XINGTIAN ? 42.0 : 30.0);
            if (target != null && !target.equals(mob.getTarget())) {
                mob.setTarget(target);
            }
            if (target == null) {
                continue;
            }
            if (type == SpeciesType.ZAOCHI) {
                tickZaochi(mob, target, now);
            } else {
                tickXingtian(mob, target, now);
            }
        }
    }

    public void clearRuntimeState() {
        tracked.clear();
        leapReadyAt.clear();
        slamReadyAt.clear();
    }

    private void tickZaochi(Mob mob, Player target, long now) {
        long allies = mob.getNearbyEntities(7, 4, 7).stream()
                .filter(entity -> type(entity) == SpeciesType.ZAOCHI)
                .count();
        if (allies >= 2) {
            mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 15, 0, true, false));
            mob.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 15, 0, true, false));
        }

        double distance = mob.getLocation().distance(target.getLocation());
        if (distance < 5.0 || distance > 12.0 || !mob.isOnGround()
                || leapReadyAt.getOrDefault(mob.getUniqueId(), 0L) > now) {
            return;
        }
        Vector leap = target.getLocation().toVector().subtract(mob.getLocation().toVector()).normalize().multiply(0.72);
        mob.setVelocity(leap.setY(0.48));
        leapReadyAt.put(mob.getUniqueId(), now + 2800L + random.nextInt(1800));
        mob.getWorld().spawnParticle(Particle.CLOUD, mob.getLocation(), 12, 0.35, 0.15, 0.35, 0.02);
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_RAVAGER_STEP, 0.55f, 1.35f);
    }

    private void tickXingtian(Mob mob, Player target, long now) {
        for (Entity entity : mob.getNearbyEntities(12, 7, 12)) {
            if (entity instanceof LivingEntity ally && type(entity) == SpeciesType.ZAOCHI) {
                ally.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 20, 0, true, false));
            }
        }

        if (mob.getLocation().distanceSquared(target.getLocation()) > 20.25
                || slamReadyAt.getOrDefault(mob.getUniqueId(), 0L) > now) {
            return;
        }
        slamReadyAt.put(mob.getUniqueId(), now + 5200L);
        mob.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, mob.getLocation().add(0, 0.4, 0), 3,
                1.4, 0.2, 1.4, 0.02);
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.75f, 0.72f);
        for (Entity entity : mob.getNearbyEntities(4.5, 2.5, 4.5)) {
            if (!(entity instanceof Player player) || !profiles.isEnlisted(player)) {
                continue;
            }
            player.damage(plugin.getConfig().getDouble("encounters.xingtian.slam-damage", 8.0), mob);
            Vector away = player.getLocation().toVector().subtract(mob.getLocation().toVector()).normalize();
            player.setVelocity(away.multiply(1.05).setY(0.38));
        }
    }

    private Player nearestTarget(Mob mob, double range) {
        Player best = null;
        double bestDistance = range * range;
        for (Player player : mob.getWorld().getPlayers()) {
            if (!profiles.isEnlisted(player) || player.isDead()) {
                continue;
            }
            double distance = player.getLocation().distanceSquared(mob.getLocation());
            if (distance < bestDistance) {
                best = player;
                bestDistance = distance;
            }
        }
        return best;
    }

    private void configure(Mob mob, SpeciesType type, double health, double attack, double speed) {
        mob.getPersistentDataContainer().set(speciesKey, PersistentDataType.STRING, type.id());
        mob.customName(Component.text(type.display(), type == SpeciesType.XINGTIAN
                ? NamedTextColor.DARK_RED : NamedTextColor.RED));
        mob.setCustomNameVisible(true);
        mob.setPersistent(true);
        mob.setRemoveWhenFarAway(false);
        mob.setCanPickupItems(false);
        setAttribute(mob, Attribute.GENERIC_MAX_HEALTH, health);
        setAttribute(mob, Attribute.GENERIC_ATTACK_DAMAGE, attack);
        setAttribute(mob, Attribute.GENERIC_MOVEMENT_SPEED, speed);
        mob.setHealth(health);
        tracked.add(mob.getUniqueId());
    }

    private void equip(Mob mob, ItemStack weapon) {
        EntityEquipment equipment = mob.getEquipment();
        if (equipment == null) {
            return;
        }
        equipment.setItemInMainHand(weapon);
        equipment.setItemInMainHandDropChance(0.0f);
    }

    private void setAttribute(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }
}
