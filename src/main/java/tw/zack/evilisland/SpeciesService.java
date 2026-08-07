package tw.zack.evilisland;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.PiglinBrute;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vindicator;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import tw.zack.evilisland.model.SpeciesTactics;
import tw.zack.evilisland.model.SpeciesType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public final class SpeciesService implements Listener {
    private static final Particle.DustOptions ZAOCHI_WARNING = new Particle.DustOptions(Color.fromRGB(230, 130, 35), 1.15f);
    private static final Particle.DustOptions XINGTIAN_WARNING = new Particle.DustOptions(Color.fromRGB(190, 25, 25), 1.35f);

    private final EvilIslandPlugin plugin;
    private final PlayerProfileService profiles;
    private final NamespacedKey speciesKey;
    private final NamespacedKey homeXKey;
    private final NamespacedKey homeYKey;
    private final NamespacedKey homeZKey;
    private final NamespacedKey damageScaleKey;
    private final Set<UUID> tracked = new HashSet<>();
    private final Map<UUID, CombatState> states = new HashMap<>();
    private final Map<UUID, BossBar> bossBars = new HashMap<>();
    private final Random random = new Random();
    private Predicate<LivingEntity> companionResolver = ignored -> false;
    private BiPredicate<Entity, LivingEntity> encounterTargetResolver = (enemy, target) -> true;
    private BiPredicate<Entity, Entity> encounterGroupResolver = (first, second) -> true;

    public SpeciesService(EvilIslandPlugin plugin, PlayerProfileService profiles) {
        this.plugin = plugin;
        this.profiles = profiles;
        speciesKey = new NamespacedKey(plugin, "species_type");
        homeXKey = new NamespacedKey(plugin, "species_home_x");
        homeYKey = new NamespacedKey(plugin, "species_home_y");
        homeZKey = new NamespacedKey(plugin, "species_home_z");
        damageScaleKey = new NamespacedKey(plugin, "species_damage_scale");
    }

    public void recover(World world) {
        for (Entity entity : world.getEntities()) {
            track(entity);
        }
    }

    public void setCompanionResolver(Predicate<LivingEntity> companionResolver) {
        this.companionResolver = companionResolver;
    }

    public void setEncounterTargetResolver(BiPredicate<Entity, LivingEntity> encounterTargetResolver) {
        this.encounterTargetResolver = encounterTargetResolver;
    }

    public void setEncounterGroupResolver(BiPredicate<Entity, Entity> encounterGroupResolver) {
        this.encounterGroupResolver = encounterGroupResolver;
    }

    public LivingEntity spawnZaochi(Location location) {
        return spawnZaochi(location, 1.0, 1.0);
    }

    public LivingEntity spawnZaochi(Location location, double healthMultiplier, double damageMultiplier) {
        PiglinBrute mob = location.getWorld().spawn(location, PiglinBrute.class);
        mob.setImmuneToZombification(true);
        configure(mob, SpeciesType.ZAOCHI,
                plugin.getConfig().getDouble("encounters.zaochi.health", 34.0) * healthMultiplier,
                plugin.getConfig().getDouble("encounters.zaochi.attack", 6.0) * damageMultiplier,
                plugin.getConfig().getDouble("encounters.zaochi.speed", 0.30));
        mob.getPersistentDataContainer().set(damageScaleKey, PersistentDataType.DOUBLE,
                Math.max(1.0, damageMultiplier));
        equip(mob, new ItemStack(Material.STONE_AXE));
        return mob;
    }

    public LivingEntity spawnXingtian(Location location) {
        return spawnXingtian(location, 1.0, 1.0);
    }

    public LivingEntity spawnXingtian(Location location, double healthMultiplier, double damageMultiplier) {
        Vindicator mob = location.getWorld().spawn(location, Vindicator.class);
        configure(mob, SpeciesType.XINGTIAN,
                plugin.getConfig().getDouble("encounters.xingtian.health", 140.0) * healthMultiplier,
                plugin.getConfig().getDouble("encounters.xingtian.attack", 13.0) * damageMultiplier,
                plugin.getConfig().getDouble("encounters.xingtian.speed", 0.27));
        mob.getPersistentDataContainer().set(damageScaleKey, PersistentDataType.DOUBLE,
                Math.max(1.0, damageMultiplier));
        equip(mob, new ItemStack(Material.IRON_AXE));
        ensureBossBar(mob);
        return mob;
    }

    public void setXingtianDisplayName(LivingEntity entity, String displayName) {
        if (!(entity instanceof Mob mob) || type(entity) != SpeciesType.XINGTIAN) {
            return;
        }
        mob.customName(Component.text(displayName, NamedTextColor.DARK_RED));
        ensureBossBar(mob).setTitle(displayName);
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
                forget(id);
                continue;
            }
            SpeciesType type = type(entity);
            if (type == null) {
                forget(id);
                continue;
            }
            CombatState state = states.computeIfAbsent(id, ignored -> new CombatState(readHome(mob)));
            double leash = plugin.getConfig().getDouble("encounters.ai.leash-distance", 42.0);
            if (!sameWorld(state.home, mob.getLocation())
                    || state.home.distanceSquared(mob.getLocation()) > leash * leash) {
                returnHome(mob, state, now, leash);
                continue;
            }

            double range = type == SpeciesType.XINGTIAN
                    ? plugin.getConfig().getDouble("encounters.xingtian.target-range", 42.0)
                    : plugin.getConfig().getDouble("encounters.zaochi.target-range", 30.0);
            LivingEntity target = nearestTarget(mob, state, range, leash);
            if (target == null) {
                returnHome(mob, state, now, leash);
                if (type == SpeciesType.XINGTIAN) {
                    updateBossBar(mob, null);
                }
                continue;
            }

            state.lastTargetAt = now;
            if (!target.equals(mob.getTarget())) {
                mob.setTarget(target);
            }
            if (type == SpeciesType.ZAOCHI) {
                tickZaochi(mob, target, state, now);
            } else {
                tickXingtian(mob, target, state, now);
            }
        }
    }

    public void clearRuntimeState() {
        for (BossBar bar : bossBars.values()) {
            bar.removeAll();
        }
        tracked.clear();
        states.clear();
        bossBars.clear();
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            track(entity);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSpeciesDamaged(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || type(mob) == null) {
            return;
        }
        LivingEntity attacker = attackingEntity(event.getDamager());
        if (attacker == null) {
            return;
        }
        if (!eligibleTarget(mob, attacker)) {
            event.setCancelled(true);
            return;
        }
        CombatState state = states.computeIfAbsent(mob.getUniqueId(), ignored -> new CombatState(readHome(mob)));
        state.lastTargetAt = System.currentTimeMillis();
        mob.setTarget(attacker);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpeciesTargets(EntityTargetLivingEntityEvent event) {
        if (type(event.getEntity()) == null || event.getTarget() == null) {
            return;
        }
        if (!(event.getEntity() instanceof Mob mob) || !eligibleTarget(mob, event.getTarget())) {
            event.setCancelled(true);
        }
    }

    private void tickZaochi(Mob mob, LivingEntity target, CombatState state, long now) {
        if (resolveZaochiLeap(mob, target, state, now)) {
            return;
        }

        double groupRadius = plugin.getConfig().getDouble("encounters.zaochi.group-radius", 9.0);
        List<Mob> pack = nearbyPack(mob, SpeciesType.ZAOCHI, groupRadius);
        if (pack.size() >= 3) {
            mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 15, 0, true, false));
            mob.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 15, 0, true, false));
        }

        double maxHealth = attributeValue(mob, Attribute.GENERIC_MAX_HEALTH, mob.getHealth());
        double woundedThreshold = plugin.getConfig().getDouble("encounters.zaochi.wounded-threshold", 0.28);
        if (SpeciesTactics.isEnraged(mob.getHealth(), maxHealth, woundedThreshold)) {
            mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 15, 1, true, false));
            mob.getWorld().spawnParticle(Particle.CRIT, mob.getLocation().add(0, 1, 0), 3, 0.3, 0.35, 0.3, 0.01);
        }

        double distance = mob.getLocation().distance(target.getLocation());
        if (now >= state.nextRepathAt && distance > 3.2 && distance < 10.0 && pack.size() > 1) {
            state.nextRepathAt = now + plugin.getConfig().getLong("encounters.ai.repath-ms", 900L);
            moveToFormation(mob, target, SpeciesTactics.formationLane(mob.getUniqueId()));
        }

        double leapMin = plugin.getConfig().getDouble("encounters.zaochi.leap-min-range", 5.0);
        double leapMax = plugin.getConfig().getDouble("encounters.zaochi.leap-max-range", 12.0);
        if (distance >= leapMin && distance <= leapMax && mob.isOnGround() && mob.hasLineOfSight(target)
                && now >= state.nextLeapAt) {
            state.leapTarget = target.getUniqueId();
            state.leapExecuteAt = now + plugin.getConfig().getLong("encounters.zaochi.leap-windup-ms", 650L);
            mob.getPathfinder().stopPathfinding();
            mob.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 20, 4, true, false));
            mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_GOAT_PREPARE_RAM, 0.7f, 0.85f);
        }
    }

    private boolean resolveZaochiLeap(Mob mob, LivingEntity fallbackTarget, CombatState state, long now) {
        if (state.leapImpactUntil > now) {
            LivingEntity hit = nearbyEligibleTarget(mob, 1.55);
            if (hit != null) {
                double damage = scaledDamage(mob, "encounters.zaochi.leap-damage", 5.0);
                hit.damage(damage, mob);
                pushAway(hit, mob.getLocation(), 0.82, 0.30);
                state.leapImpactUntil = 0L;
            }
        }
        if (state.leapExecuteAt == 0L) {
            return false;
        }

        Entity storedTarget = state.leapTarget == null ? null : Bukkit.getEntity(state.leapTarget);
        LivingEntity target = storedTarget instanceof LivingEntity living ? living : fallbackTarget;
        Location marker = target == null ? mob.getLocation() : target.getLocation();
        mob.getWorld().spawnParticle(Particle.REDSTONE, marker.clone().add(0, 0.15, 0), 3, 0.45, 0.05, 0.45, 0.0, ZAOCHI_WARNING);
        mob.getWorld().spawnParticle(Particle.CLOUD, mob.getLocation(), 3, 0.25, 0.08, 0.25, 0.01);
        if (now < state.leapExecuteAt) {
            return true;
        }

        state.leapExecuteAt = 0L;
        state.leapTarget = null;
        long minimum = plugin.getConfig().getLong("encounters.zaochi.leap-cooldown-min-ms", 3000L);
        long spread = plugin.getConfig().getLong("encounters.zaochi.leap-cooldown-random-ms", 1600L);
        state.nextLeapAt = now + minimum + (spread <= 0 ? 0 : random.nextLong(spread + 1));
        if (target == null || !eligibleTarget(mob, target) || !mob.hasLineOfSight(target)) {
            return false;
        }
        Vector leap = horizontalDirection(mob.getLocation(), target.getLocation()).multiply(0.92).setY(0.46);
        mob.setVelocity(leap);
        state.leapImpactUntil = now + 1200L;
        mob.getWorld().spawnParticle(Particle.CLOUD, mob.getLocation(), 14, 0.35, 0.15, 0.35, 0.03);
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_GOAT_LONG_JUMP, 0.75f, 0.82f);
        return true;
    }

    private void tickXingtian(Mob mob, LivingEntity target, CombatState state, long now) {
        updateBossBar(mob, target instanceof Player player ? player : null);
        double maxHealth = attributeValue(mob, Attribute.GENERIC_MAX_HEALTH, mob.getHealth());
        double threshold = plugin.getConfig().getDouble("encounters.xingtian.enraged-threshold", 0.45);
        boolean enraged = SpeciesTactics.isEnraged(mob.getHealth(), maxHealth, threshold);
        if (enraged && !state.enraged) {
            state.enraged = true;
            mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.0f, 0.65f);
            mob.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, mob.getLocation().add(0, 1, 0), 5, 1.2, 0.8, 1.2, 0.03);
            for (Player player : nearbyEligiblePlayers(mob, 40.0, 20.0)) {
                player.sendMessage(EvilIslandPlugin.message("刑天統領震怒，攻勢明顯加快。", NamedTextColor.DARK_RED));
            }
        }
        if (enraged) {
            mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 15, 0, true, false));
            mob.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 15, 0, true, false));
        }

        commandPack(mob, target, state, enraged);
        if (resolveXingtianSlam(mob, state, now, enraged)) {
            return;
        }
        if (resolveXingtianCharge(mob, target, state, now, enraged)) {
            return;
        }

        double distance = mob.getLocation().distance(target.getLocation());
        double slamRadius = plugin.getConfig().getDouble("encounters.xingtian.slam-radius", 4.5);
        if (distance <= slamRadius + 0.35 && now >= state.nextSlamAt) {
            state.slamExecuteAt = now + plugin.getConfig().getLong("encounters.xingtian.slam-windup-ms", 1050L);
            mob.getPathfinder().stopPathfinding();
            mob.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 30, 8, true, false));
            mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.8f, 0.72f);
            return;
        }

        double chargeMin = plugin.getConfig().getDouble("encounters.xingtian.charge-min-range", 7.0);
        double chargeMax = plugin.getConfig().getDouble("encounters.xingtian.charge-max-range", 18.0);
        if (distance >= chargeMin && distance <= chargeMax && mob.isOnGround() && mob.hasLineOfSight(target)
                && now >= state.nextChargeAt) {
            state.chargeTarget = target.getUniqueId();
            state.chargeExecuteAt = now + plugin.getConfig().getLong("encounters.xingtian.charge-windup-ms", 800L);
            mob.getPathfinder().stopPathfinding();
            mob.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 25, 6, true, false));
            mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_GOAT_SCREAMING_PREPARE_RAM, 0.9f, 0.62f);
        }
    }

    private boolean resolveXingtianSlam(Mob mob, CombatState state, long now, boolean enraged) {
        if (state.slamExecuteAt == 0L) {
            return false;
        }
        long windup = plugin.getConfig().getLong("encounters.xingtian.slam-windup-ms", 1050L);
        double radius = plugin.getConfig().getDouble("encounters.xingtian.slam-radius", 4.5);
        double progress = Math.max(0.18, 1.0 - (state.slamExecuteAt - now) / (double) Math.max(1L, windup));
        particleRing(mob.getLocation(), radius * progress, XINGTIAN_WARNING);
        if (now < state.slamExecuteAt) {
            return true;
        }

        state.slamExecuteAt = 0L;
        long baseCooldown = plugin.getConfig().getLong("encounters.xingtian.slam-cooldown-ms", 5400L);
        double multiplier = plugin.getConfig().getDouble("encounters.xingtian.enraged-cooldown-multiplier", 0.72);
        state.nextSlamAt = now + SpeciesTactics.scaledCooldown(baseCooldown, enraged, multiplier);
        mob.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, mob.getLocation().add(0, 0.4, 0), 4, 1.4, 0.2, 1.4, 0.03);
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 0.68f);
        double damage = scaledDamage(mob, "encounters.xingtian.slam-damage", 8.0);
        for (LivingEntity target : nearbyEligibleTargets(mob, radius, 2.8)) {
            target.damage(damage, mob);
            pushAway(target, mob.getLocation(), 1.12, 0.42);
        }
        return true;
    }

    private boolean resolveXingtianCharge(Mob mob, LivingEntity fallbackTarget, CombatState state, long now, boolean enraged) {
        if (state.chargeImpactUntil > now) {
            LivingEntity hit = nearbyEligibleTarget(mob, 1.9);
            if (hit != null) {
                hit.damage(scaledDamage(mob, "encounters.xingtian.charge-damage", 10.0), mob);
                pushAway(hit, mob.getLocation(), 1.25, 0.48);
                state.chargeImpactUntil = 0L;
            }
        }
        if (state.chargeExecuteAt == 0L) {
            return false;
        }

        Entity storedTarget = state.chargeTarget == null ? null : Bukkit.getEntity(state.chargeTarget);
        LivingEntity target = storedTarget instanceof LivingEntity living ? living : fallbackTarget;
        Location marker = target == null ? mob.getLocation() : target.getLocation();
        mob.getWorld().spawnParticle(Particle.REDSTONE, marker.clone().add(0, 0.15, 0), 4, 0.6, 0.05, 0.6, 0.0, XINGTIAN_WARNING);
        if (now < state.chargeExecuteAt) {
            return true;
        }

        state.chargeExecuteAt = 0L;
        state.chargeTarget = null;
        long baseCooldown = plugin.getConfig().getLong("encounters.xingtian.charge-cooldown-ms", 6800L);
        double multiplier = plugin.getConfig().getDouble("encounters.xingtian.enraged-cooldown-multiplier", 0.72);
        state.nextChargeAt = now + SpeciesTactics.scaledCooldown(baseCooldown, enraged, multiplier);
        if (target == null || !eligibleTarget(mob, target) || !mob.hasLineOfSight(target)) {
            return false;
        }
        mob.setVelocity(horizontalDirection(mob.getLocation(), target.getLocation()).multiply(1.28).setY(0.24));
        state.chargeImpactUntil = now + 1450L;
        mob.getWorld().spawnParticle(Particle.CLOUD, mob.getLocation(), 22, 0.6, 0.2, 0.6, 0.04);
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.85f, 0.82f);
        return true;
    }

    private void commandPack(Mob commander, LivingEntity target, CombatState commanderState, boolean enraged) {
        double radius = plugin.getConfig().getDouble("encounters.xingtian.command-radius", 13.0);
        for (Entity entity : commander.getNearbyEntities(radius, 7.0, radius)) {
            if (!(entity instanceof Mob ally) || type(ally) != SpeciesType.ZAOCHI
                    || !encounterGroupResolver.test(commander, ally)) {
                continue;
            }
            ally.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 20, enraged ? 1 : 0, true, false));
            ally.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20, 0, true, false));
            ally.setTarget(target);
            CombatState allyState = states.computeIfAbsent(ally.getUniqueId(), ignored -> new CombatState(readHome(ally)));
            allyState.lastTargetAt = commanderState.lastTargetAt;
        }
    }

    private void moveToFormation(Mob mob, LivingEntity target, int lane) {
        if (lane == 0) {
            return;
        }
        Vector towardTarget = horizontalDirection(mob.getLocation(), target.getLocation());
        Vector side = new Vector(-towardTarget.getZ(), 0, towardTarget.getX());
        double flankDistance = plugin.getConfig().getDouble("encounters.zaochi.flank-distance", 3.4);
        Location destination = target.getLocation().clone()
                .add(side.multiply(lane * flankDistance))
                .subtract(towardTarget.multiply(2.2));
        mob.getPathfinder().moveTo(destination, 1.12);
    }

    private void returnHome(Mob mob, CombatState state, long now, double leash) {
        mob.setTarget(null);
        state.cancelWindups();
        if (!sameWorld(state.home, mob.getLocation())) {
            return;
        }
        double distanceSquared = state.home.distanceSquared(mob.getLocation());
        if (distanceSquared > leash * leash * 4.0) {
            mob.teleport(state.home);
            mob.setVelocity(new Vector());
            return;
        }
        if (distanceSquared > 2.25) {
            if (now >= state.nextRepathAt) {
                state.nextRepathAt = now + plugin.getConfig().getLong("encounters.ai.repath-ms", 900L);
                mob.getPathfinder().moveTo(state.home, plugin.getConfig().getDouble("encounters.ai.return-speed", 1.15));
            }
            return;
        }

        long disengage = plugin.getConfig().getLong("encounters.ai.disengage-ms", 10000L);
        if (now - state.lastTargetAt < disengage || now < state.nextRecoveryAt) {
            return;
        }
        state.nextRecoveryAt = now + 1000L;
        double maxHealth = attributeValue(mob, Attribute.GENERIC_MAX_HEALTH, mob.getHealth());
        mob.setHealth(Math.min(maxHealth, mob.getHealth() + Math.max(1.0, maxHealth * 0.06)));
    }

    private LivingEntity nearestTarget(Mob mob, CombatState state, double range, double leash) {
        LivingEntity current = mob.getTarget() != null && eligibleTarget(mob, mob.getTarget()) ? mob.getTarget() : null;
        LivingEntity best = null;
        double bestDistance = range * range;
        for (Player player : mob.getWorld().getPlayers()) {
            if (!eligibleTarget(mob, player) || state.home.distanceSquared(player.getLocation()) > leash * leash * 1.35) {
                continue;
            }
            double distance = player.getLocation().distanceSquared(mob.getLocation());
            if (distance < bestDistance) {
                best = player;
                bestDistance = distance;
            }
        }
        for (Entity entity : mob.getNearbyEntities(range, range * 0.7, range)) {
            if (!(entity instanceof LivingEntity living) || !eligibleTarget(mob, living)
                    || state.home.distanceSquared(living.getLocation()) > leash * leash * 1.35) {
                continue;
            }
            double distance = living.getLocation().distanceSquared(mob.getLocation());
            if (distance < bestDistance) {
                best = living;
                bestDistance = distance;
            }
        }
        if (current != null && current.getWorld().equals(mob.getWorld())) {
            double currentDistance = current.getLocation().distanceSquared(mob.getLocation());
            if (currentDistance <= bestDistance * 1.35 && currentDistance <= range * range * 1.2) {
                return current;
            }
        }
        return best;
    }

    private boolean eligibleTarget(Mob enemy, LivingEntity target) {
        if (!encounterTargetResolver.test(enemy, target)) {
            return false;
        }
        if (target instanceof Player player) {
            return profiles.isEnlisted(player) && !player.isDead()
                    && player.getGameMode() != GameMode.CREATIVE
                    && player.getGameMode() != GameMode.SPECTATOR;
        }
        return target != null && target.isValid() && !target.isDead() && companionResolver.test(target);
    }

    private LivingEntity attackingEntity(Entity damager) {
        if (damager instanceof LivingEntity living) {
            return living;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity living) {
            return living;
        }
        return null;
    }

    private List<Mob> nearbyPack(Mob center, SpeciesType expected, double radius) {
        List<Mob> pack = new ArrayList<>();
        pack.add(center);
        for (Entity entity : center.getNearbyEntities(radius, radius * 0.55, radius)) {
            if (entity instanceof Mob mob && type(entity) == expected
                    && encounterGroupResolver.test(center, mob)) {
                pack.add(mob);
            }
        }
        return pack;
    }

    private LivingEntity nearbyEligibleTarget(Mob mob, double radius) {
        LivingEntity best = null;
        double bestDistance = radius * radius;
        for (Entity entity : mob.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof LivingEntity target) || !eligibleTarget(mob, target)) {
                continue;
            }
            double distance = target.getLocation().distanceSquared(mob.getLocation());
            if (distance <= bestDistance) {
                best = target;
                bestDistance = distance;
            }
        }
        return best;
    }

    private List<LivingEntity> nearbyEligibleTargets(Mob mob, double horizontal, double vertical) {
        List<LivingEntity> targets = new ArrayList<>();
        for (Entity entity : mob.getNearbyEntities(horizontal, vertical, horizontal)) {
            if (entity instanceof LivingEntity target && eligibleTarget(mob, target)) {
                targets.add(target);
            }
        }
        return targets;
    }

    private List<Player> nearbyEligiblePlayers(Mob mob, double horizontal, double vertical) {
        List<Player> players = new ArrayList<>();
        for (Entity entity : mob.getNearbyEntities(horizontal, vertical, horizontal)) {
            if (entity instanceof Player player && eligibleTarget(mob, player)) {
                players.add(player);
            }
        }
        return players;
    }

    private void updateBossBar(Mob mob, Player target) {
        BossBar bar = ensureBossBar(mob);
        double maxHealth = attributeValue(mob, Attribute.GENERIC_MAX_HEALTH, mob.getHealth());
        bar.setProgress(SpeciesTactics.healthRatio(mob.getHealth(), maxHealth));
        Set<Player> visible = new HashSet<>(nearbyEligiblePlayers(mob, 48.0, 24.0));
        if (target != null && eligibleTarget(mob, target)) {
            visible.add(target);
        }
        for (Player player : List.copyOf(bar.getPlayers())) {
            if (!visible.contains(player)) {
                bar.removePlayer(player);
            }
        }
        for (Player player : visible) {
            bar.addPlayer(player);
        }
        bar.setVisible(!visible.isEmpty());
    }

    private BossBar ensureBossBar(Mob mob) {
        return bossBars.computeIfAbsent(mob.getUniqueId(), ignored -> {
            BossBar bar = Bukkit.createBossBar("刑天統領", BarColor.RED, BarStyle.SEGMENTED_10);
            bar.setVisible(false);
            return bar;
        });
    }

    private void particleRing(Location center, double radius, Particle.DustOptions dust) {
        for (int index = 0; index < 18; index++) {
            double angle = Math.PI * 2.0 * index / 18.0;
            Location point = center.clone().add(Math.cos(angle) * radius, 0.12, Math.sin(angle) * radius);
            center.getWorld().spawnParticle(Particle.REDSTONE, point, 1, 0, 0, 0, 0, dust);
        }
    }

    private void pushAway(LivingEntity target, Location origin, double force, double vertical) {
        Vector away = horizontalDirection(origin, target.getLocation()).multiply(force).setY(vertical);
        target.setVelocity(away);
    }

    private Vector horizontalDirection(Location from, Location to) {
        Vector direction = to.toVector().subtract(from.toVector()).setY(0);
        if (direction.lengthSquared() < 0.0001) {
            return new Vector(1, 0, 0);
        }
        return direction.normalize();
    }

    private boolean sameWorld(Location first, Location second) {
        return first.getWorld() != null && first.getWorld().equals(second.getWorld());
    }

    private void configure(Mob mob, SpeciesType type, double health, double attack, double speed) {
        mob.getPersistentDataContainer().set(speciesKey, PersistentDataType.STRING, type.id());
        saveHome(mob, mob.getLocation());
        mob.customName(Component.text(type.display(), type == SpeciesType.XINGTIAN
                ? NamedTextColor.DARK_RED : NamedTextColor.RED));
        mob.setCustomNameVisible(true);
        mob.setPersistent(true);
        mob.setRemoveWhenFarAway(false);
        mob.setCanPickupItems(false);
        mob.getPathfinder().setCanFloat(true);
        setAttribute(mob, Attribute.GENERIC_MAX_HEALTH, health);
        setAttribute(mob, Attribute.GENERIC_ATTACK_DAMAGE, attack);
        setAttribute(mob, Attribute.GENERIC_MOVEMENT_SPEED, speed);
        mob.setHealth(health);
        tracked.add(mob.getUniqueId());
        states.put(mob.getUniqueId(), new CombatState(mob.getLocation()));
    }

    private void track(Entity entity) {
        if (!(entity instanceof Mob mob) || type(entity) == null) {
            return;
        }
        tracked.add(entity.getUniqueId());
        states.computeIfAbsent(entity.getUniqueId(), ignored -> new CombatState(readHome(mob)));
        if (type(entity) == SpeciesType.XINGTIAN) {
            ensureBossBar(mob);
        }
    }

    private void forget(UUID id) {
        tracked.remove(id);
        states.remove(id);
        BossBar bar = bossBars.remove(id);
        if (bar != null) {
            bar.removeAll();
        }
    }

    private void saveHome(Mob mob, Location home) {
        PersistentDataContainer data = mob.getPersistentDataContainer();
        data.set(homeXKey, PersistentDataType.DOUBLE, home.getX());
        data.set(homeYKey, PersistentDataType.DOUBLE, home.getY());
        data.set(homeZKey, PersistentDataType.DOUBLE, home.getZ());
    }

    private Location readHome(Mob mob) {
        PersistentDataContainer data = mob.getPersistentDataContainer();
        Double x = data.get(homeXKey, PersistentDataType.DOUBLE);
        Double y = data.get(homeYKey, PersistentDataType.DOUBLE);
        Double z = data.get(homeZKey, PersistentDataType.DOUBLE);
        if (x == null || y == null || z == null) {
            Location home = mob.getLocation();
            saveHome(mob, home);
            return home;
        }
        return new Location(mob.getWorld(), x, y, z);
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

    private double attributeValue(LivingEntity entity, Attribute attribute, double fallback) {
        AttributeInstance instance = entity.getAttribute(attribute);
        return instance == null ? fallback : instance.getValue();
    }

    private double scaledDamage(Mob mob, String path, double fallback) {
        Double scale = mob.getPersistentDataContainer().get(damageScaleKey, PersistentDataType.DOUBLE);
        return plugin.getConfig().getDouble(path, fallback) * (scale == null ? 1.0 : Math.max(1.0, scale));
    }

    private static final class CombatState {
        private final Location home;
        private long lastTargetAt;
        private long nextRepathAt;
        private long nextRecoveryAt;
        private long nextLeapAt;
        private long leapExecuteAt;
        private long leapImpactUntil;
        private UUID leapTarget;
        private long nextSlamAt;
        private long slamExecuteAt;
        private long nextChargeAt;
        private long chargeExecuteAt;
        private long chargeImpactUntil;
        private UUID chargeTarget;
        private boolean enraged;

        private CombatState(Location home) {
            this.home = home.clone();
        }

        private void cancelWindups() {
            leapExecuteAt = 0L;
            leapImpactUntil = 0L;
            leapTarget = null;
            slamExecuteAt = 0L;
            chargeExecuteAt = 0L;
            chargeImpactUntil = 0L;
            chargeTarget = null;
        }
    }
}
