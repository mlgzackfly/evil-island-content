package tw.zack.evilisland;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Pillager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vindicator;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import tw.zack.evilisland.model.NpcRole;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class CompanionService implements Listener {
    private final EvilIslandPlugin plugin;
    private final NamespacedKey companionKey;
    private final NamespacedKey ownerKey;
    private final NamespacedKey sessionKey;
    private final NamespacedKey downedUntilKey;
    private final NamespacedKey roleKey;
    private final Set<UUID> tracked = new HashSet<>();
    private Predicate<Entity> enemyResolver = ignored -> false;
    private Consumer<NpcRole> injuryListener = ignored -> { };

    public CompanionService(EvilIslandPlugin plugin) {
        this.plugin = plugin;
        companionKey = new NamespacedKey(plugin, "patrol_companion");
        ownerKey = new NamespacedKey(plugin, "companion_owner");
        sessionKey = new NamespacedKey(plugin, "patrol_session");
        downedUntilKey = new NamespacedKey(plugin, "companion_downed_until");
        roleKey = new NamespacedKey(plugin, "companion_role");
    }

    public void setEnemyResolver(Predicate<Entity> enemyResolver) {
        this.enemyResolver = enemyResolver;
    }

    public void setInjuryListener(Consumer<NpcRole> injuryListener) {
        this.injuryListener = injuryListener;
    }

    public LivingEntity spawn(Location location, Player owner, UUID sessionId) {
        return spawn(location, owner.getUniqueId(), sessionId, NpcRole.YANGWU);
    }

    public LivingEntity spawn(Location location, UUID ownerId, UUID sessionId) {
        return spawn(location, ownerId, sessionId, NpcRole.YANGWU);
    }

    public LivingEntity spawn(Location location, Player owner, UUID sessionId, NpcRole role) {
        return spawn(location, owner.getUniqueId(), sessionId, role);
    }

    public LivingEntity spawn(Location location, UUID ownerId, UUID sessionId, NpcRole role) {
        Mob companion = role == NpcRole.DOUTIAN
                ? location.getWorld().spawn(location, Vindicator.class)
                : location.getWorld().spawn(location, Pillager.class);
        PersistentDataContainer data = companion.getPersistentDataContainer();
        data.set(companionKey, PersistentDataType.BYTE, (byte) 1);
        data.set(ownerKey, PersistentDataType.STRING, ownerId.toString());
        data.set(sessionKey, PersistentDataType.STRING, sessionId.toString());
        data.set(roleKey, PersistentDataType.STRING, role.id());
        companion.customName(Component.text(role.display(), NamedTextColor.GREEN));
        companion.setCustomNameVisible(true);
        companion.setPersistent(true);
        companion.setRemoveWhenFarAway(false);
        companion.setCanPickupItems(false);
        companion.getPathfinder().setCanFloat(true);
        String configPath = configPath(role);
        double defaultHealth = role == NpcRole.HANSHAN ? 64.0 : role == NpcRole.YANGWU ? 56.0
                : role == NpcRole.DOUTIAN ? 48.0 : 36.0;
        double defaultSpeed = role == NpcRole.HANSHAN ? 0.27 : role == NpcRole.YANGWU ? 0.30
                : role == NpcRole.DOUTIAN ? 0.33 : 0.34;
        setAttribute(companion, Attribute.GENERIC_MAX_HEALTH,
                plugin.getConfig().getDouble(configPath + ".health", defaultHealth));
        setAttribute(companion, Attribute.GENERIC_MOVEMENT_SPEED,
                plugin.getConfig().getDouble(configPath + ".speed", defaultSpeed));
        setAttribute(companion, Attribute.GENERIC_FOLLOW_RANGE, 36.0);
        if (role == NpcRole.DOUTIAN) setAttribute(companion, Attribute.GENERIC_ATTACK_DAMAGE,
                plugin.getConfig().getDouble(configPath + ".attack", 6.0));
        companion.setHealth(attributeValue(companion, Attribute.GENERIC_MAX_HEALTH, defaultHealth));
        companion.setInvulnerable(role == NpcRole.WUJI);
        equip(companion, role);
        tracked.add(companion.getUniqueId());
        return companion;
    }

    public void recover(World world) {
        for (Entity entity : world.getEntities()) {
            track(entity);
        }
    }

    public void tick() {
        long now = System.currentTimeMillis();
        for (UUID id : Set.copyOf(tracked)) {
            Entity entity = Bukkit.getEntity(id);
            if (!(entity instanceof Mob companion) || !entity.isValid() || entity.isDead() || !isCompanion(entity)) {
                tracked.remove(id);
                continue;
            }
            long downedUntil = downedUntil(companion);
            if (downedUntil > 0L) {
                companion.getWorld().spawnParticle(Particle.TOTEM, companion.getLocation().add(0, 1, 0),
                        2, 0.35, 0.5, 0.35, 0.01);
                if (now >= downedUntil) {
                    revive(companion, false);
                }
                continue;
            }

            Player owner = owner(companion);
            if (owner == null || !owner.isOnline() || owner.isDead() || !owner.getWorld().equals(companion.getWorld())) {
                companion.setTarget(null);
                companion.getPathfinder().stopPathfinding();
                continue;
            }

            double teleportDistance = plugin.getConfig().getDouble("companions.follow.teleport-distance", 26.0);
            double distanceSquared = companion.getLocation().distanceSquared(owner.getLocation());
            if (distanceSquared > teleportDistance * teleportDistance) {
                Location destination = owner.getLocation().clone().add(-1.2, 0, -1.2);
                companion.teleport(destination);
                companion.setTarget(null);
                continue;
            }

            NpcRole role = role(companion);
            if (role == NpcRole.WUJI) {
                companion.setTarget(null);
                follow(companion, owner, distanceSquared);
                companion.getWorld().spawnParticle(Particle.END_ROD, companion.getLocation().add(0, 1.7, 0),
                        1, 0.15, 0.2, 0.15, 0.0);
                continue;
            }

            if (role == NpcRole.HANSHAN) {
                companion.setTarget(null);
                follow(companion, owner, distanceSquared);
                owner.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 15, 0,
                        true, false, true));
                double radius = plugin.getConfig().getDouble("companions.hanshan.guard-radius", 6.0);
                for (Entity nearby : companion.getNearbyEntities(radius, 4.0, radius)) {
                    if (nearby instanceof LivingEntity living && eligibleEnemy(companion, living)) {
                        living.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 15, 0,
                                true, false, true));
                    }
                }
                companion.getWorld().spawnParticle(Particle.BLOCK_CRACK,
                        companion.getLocation().add(0, 0.15, 0), 2, 0.6, 0.1, 0.6, 0.0,
                        Material.STONE_BRICKS.createBlockData());
                continue;
            }

            LivingEntity target = companion.getTarget();
            double targetRange = plugin.getConfig().getDouble(configPath(role) + ".target-range",
                    role == NpcRole.DOUTIAN ? 14.0 : 20.0);
            if (target == null || !target.isValid() || target.isDead() || !eligibleEnemy(companion, target)
                    || target.getLocation().distanceSquared(companion.getLocation()) > targetRange * targetRange) {
                target = nearestEnemy(companion, targetRange);
                companion.setTarget(target);
            }
            if (target == null) {
                follow(companion, owner, distanceSquared);
            }
        }
    }

    public boolean isCompanion(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(companionKey, PersistentDataType.BYTE);
    }

    public boolean isCombatReady(LivingEntity entity) {
        NpcRole role = role(entity);
        return isCompanion(entity) && role != NpcRole.WUJI && entity.isValid() && !entity.isDead()
                && !entity.isInvulnerable() && downedUntil(entity) <= 0L;
    }

    public NpcRole role(Entity entity) {
        if (!isCompanion(entity)) return null;
        NpcRole parsed = NpcRole.parse(
                entity.getPersistentDataContainer().get(roleKey, PersistentDataType.STRING));
        return parsed == null ? NpcRole.YANGWU : parsed;
    }

    public UUID sessionId(Entity entity) {
        if (entity == null) {
            return null;
        }
        return parseUuid(entity.getPersistentDataContainer().get(sessionKey, PersistentDataType.STRING));
    }

    public void remove(UUID entityId) {
        Entity entity = Bukkit.getEntity(entityId);
        if (entity != null && isCompanion(entity)) {
            entity.remove();
        }
        tracked.remove(entityId);
    }

    public void clearRuntimeState() {
        tracked.clear();
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            track(entity);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFriendlyFire(EntityDamageByEntityEvent event) {
        LivingEntity source = source(event.getDamager());
        if (isCompanion(event.getEntity()) && source instanceof Player) {
            event.setCancelled(true);
            return;
        }
        if (event.getEntity() instanceof Player && source != null && isCompanion(source)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCompanionDamaged(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Mob companion) || !isCompanion(companion)
                || event.getFinalDamage() < companion.getHealth()) {
            return;
        }
        event.setCancelled(true);
        down(companion);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCompanionTargets(EntityTargetLivingEntityEvent event) {
        if (!isCompanion(event.getEntity())) {
            return;
        }
        if (event.getTarget() == null) {
            return;
        }
        if (!(event.getEntity() instanceof Mob companion) || !eligibleEnemy(companion, event.getTarget())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCompanionInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Mob companion)
                || !isCompanion(companion)) {
            return;
        }
        event.setCancelled(true);
        Player owner = owner(companion);
        if (owner == null || !owner.getUniqueId().equals(event.getPlayer().getUniqueId())) {
            event.getPlayer().sendMessage(EvilIslandPlugin.message("這名" + role(companion).display()
                    + "隸屬其他編組。"));
            return;
        }
        NpcRole role = role(companion);
        if (role == NpcRole.WUJI) {
            event.getPlayer().sendMessage(EvilIslandPlugin.message("無跡正在比對地面足跡與斥候記號。"));
            return;
        }
        if (downedUntil(companion) <= 0L) {
            String status = role == NpcRole.HANSHAN ? "正在穩固防線並壓低敵軍速度。"
                    : role == NpcRole.DOUTIAN ? "正在近身壓制高威脅目標。" : "正在遠程掩護隊伍。";
            event.getPlayer().sendMessage(EvilIslandPlugin.message(role.display() + status));
            return;
        }
        revive(companion, true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCompanionShoots(EntityShootBowEvent event) {
        if (isCompanion(event.getEntity()) && role(event.getEntity()) == NpcRole.YANGWU
                && event.getProjectile() instanceof AbstractArrow arrow) {
            arrow.setDamage(plugin.getConfig().getDouble("companions.yangwu.attack", 5.0));
        }
    }

    private void down(Mob companion) {
        NpcRole role = role(companion);
        long recovery = plugin.getConfig().getLong(configPath(role) + ".downed-ms", 12000L);
        companion.getPersistentDataContainer().set(downedUntilKey, PersistentDataType.LONG,
                System.currentTimeMillis() + Math.max(1000L, recovery));
        companion.setHealth(1.0);
        companion.setTarget(null);
        companion.setAI(false);
        companion.setInvulnerable(true);
        companion.customName(Component.text(role.display() + "（倒地）", NamedTextColor.RED));
        companion.getWorld().playSound(companion.getLocation(), Sound.ENTITY_PILLAGER_HURT, 1.0f, 0.6f);
        Player owner = owner(companion);
        if (owner != null) {
            owner.sendMessage(EvilIslandPlugin.message(role.display() + "倒地；靠近並右鍵可立即救援。",
                    NamedTextColor.RED));
        }
        injuryListener.accept(role(companion));
    }

    private void revive(Mob companion, boolean rescued) {
        NpcRole role = role(companion);
        companion.getPersistentDataContainer().remove(downedUntilKey);
        companion.setInvulnerable(false);
        companion.setAI(true);
        double maxHealth = attributeValue(companion, Attribute.GENERIC_MAX_HEALTH, 56.0);
        double ratio = rescued
                ? plugin.getConfig().getDouble(configPath(role) + ".rescue-health-ratio", 0.55)
                : plugin.getConfig().getDouble(configPath(role) + ".auto-recover-health-ratio", 0.35);
        companion.setHealth(Math.max(1.0, Math.min(maxHealth, maxHealth * ratio)));
        companion.customName(Component.text(role.display(), NamedTextColor.GREEN));
        companion.getWorld().spawnParticle(Particle.HEART, companion.getLocation().add(0, 1, 0),
                8, 0.45, 0.65, 0.45, 0.02);
        companion.getWorld().playSound(companion.getLocation(), Sound.ITEM_TOTEM_USE, 0.65f, 1.25f);
        Player owner = owner(companion);
        if (owner != null) {
            owner.sendMessage(EvilIslandPlugin.message(rescued
                    ? role.display() + "已重新加入任務。" : role.display() + "自行恢復並重新加入任務。",
                    NamedTextColor.GREEN));
        }
    }

    private LivingEntity nearestEnemy(Mob companion, double range) {
        LivingEntity best = null;
        double bestDistance = range * range;
        for (Entity entity : companion.getNearbyEntities(range, range * 0.6, range)) {
            if (!(entity instanceof LivingEntity living) || living.isDead() || !eligibleEnemy(companion, living)) {
                continue;
            }
            double distance = living.getLocation().distanceSquared(companion.getLocation());
            if (distance < bestDistance && companion.hasLineOfSight(living)) {
                best = living;
                bestDistance = distance;
            }
        }
        return best;
    }

    private Player owner(Entity entity) {
        UUID ownerId = parseUuid(entity.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING));
        return ownerId == null ? null : Bukkit.getPlayer(ownerId);
    }

    private boolean eligibleEnemy(Mob companion, Entity enemy) {
        UUID companionSession = sessionId(companion);
        return enemyResolver.test(enemy) && companionSession != null && companionSession.equals(sessionId(enemy));
    }

    private long downedUntil(Entity entity) {
        Long value = entity.getPersistentDataContainer().get(downedUntilKey, PersistentDataType.LONG);
        return value == null ? 0L : value;
    }

    private LivingEntity source(Entity damager) {
        if (damager instanceof LivingEntity living) {
            return living;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity living) {
            return living;
        }
        return null;
    }

    private void track(Entity entity) {
        if (entity instanceof Mob && isCompanion(entity)) {
            tracked.add(entity.getUniqueId());
        }
    }

    private void equip(Mob companion, NpcRole role) {
        EntityEquipment equipment = companion.getEquipment();
        if (equipment == null) {
            return;
        }
        Material mainHand = switch (role) {
            case HANSHAN -> Material.SHIELD;
            case YANGWU -> Material.CROSSBOW;
            case WUJI -> Material.SPYGLASS;
            case DOUTIAN -> Material.IRON_AXE;
        };
        Material chest = role == NpcRole.WUJI ? Material.LEATHER_CHESTPLATE : Material.IRON_CHESTPLATE;
        equipment.setItemInMainHand(new ItemStack(mainHand));
        equipment.setChestplate(new ItemStack(chest));
        equipment.setItemInMainHandDropChance(0.0f);
        equipment.setChestplateDropChance(0.0f);
    }

    private String configPath(NpcRole role) {
        return "companions." + (role == null ? NpcRole.YANGWU.id() : role.id());
    }

    private void follow(Mob companion, Player owner, double distanceSquared) {
        double followDistance = plugin.getConfig().getDouble("companions.follow.distance", 6.0);
        if (distanceSquared > followDistance * followDistance) {
            companion.getPathfinder().moveTo(owner.getLocation(),
                    plugin.getConfig().getDouble("companions.follow.speed", 1.12));
        }
    }

    private void setAttribute(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private double attributeValue(LivingEntity entity, Attribute attribute, double fallback) {
        AttributeInstance instance = entity.getAttribute(attribute);
        return instance == null ? fallback : instance.getValue();
    }

    private UUID parseUuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
