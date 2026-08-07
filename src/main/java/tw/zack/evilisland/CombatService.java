package tw.zack.evilisland;

import net.kyori.adventure.text.Component;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import tw.zack.evilisland.model.Formula;
import tw.zack.evilisland.model.QiTendency;
import tw.zack.evilisland.model.CityProject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CombatService implements Listener {
    private final EvilIslandPlugin plugin;
    private final PlayerProfileService profiles;
    private final DaoFieldService daoFields;
    private final EncounterService encounters;
    private final GameItemService items;
    private final DevelopmentService development;
    private final Map<UUID, Long> abilityCooldowns = new HashMap<>();
    private final Map<UUID, Long> guardUntil = new HashMap<>();
    private final Set<UUID> activeCasts = new HashSet<>();

    public CombatService(EvilIslandPlugin plugin, PlayerProfileService profiles, DaoFieldService daoFields,
                         EncounterService encounters, GameItemService items, DevelopmentService development) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.daoFields = daoFields;
        this.encounters = encounters;
        this.items = items;
        this.development = development;
    }

    public void tickPlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!profiles.isEnlisted(player)) {
                continue;
            }
            DaoFieldService.Reading reading = daoFields.reading(player.getLocation());
            int regen = Math.max(1, (int) Math.round(plugin.getConfig().getDouble("qi.base-regen", 0.5)
                    + reading.dao() * plugin.getConfig().getDouble("qi.dao-regen-factor", 0.04)));
            if (reading.region().contains("新城") || reading.region().contains("聚炁鏡")) {
                regen += development.projectLevel(CityProject.QI_MIRROR);
            }
            boolean lowDaoWeakness = profiles.transformations(player) > 0
                    && reading.dao() < plugin.getConfig().getInt("progression.low-dao-threshold", 20);
            if (lowDaoWeakness) {
                profiles.addQi(player, -plugin.getConfig().getInt("progression.low-dao-qi-drain", 2));
                player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 50, 0, true, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 50, 0, true, false));
            } else {
                profiles.addQi(player, regen);
            }

            if (profiles.formula(player) == Formula.QING && profiles.qi(player) > 10) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 35, 0, true, false));
            }
            QiTendency tendency = profiles.tendency(player);
            String tendencyName = tendency == null ? "未測定" : tendency.display();
            String suffix = lowDaoWeakness ? " | 易質衰弱" : "";
            player.sendActionBar(Component.text(
                    "道息 " + reading.dao() + " | 炁息 " + profiles.qi(player) + "/" + profiles.maxQi(player)
                            + " | " + tendencyName + "・" + profiles.formulaPath(player).display() + suffix
            ));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onAbility(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getPlayer().isSneaking()) {
            return;
        }
        String action = event.getAction().name();
        if (!action.contains("RIGHT_CLICK")) {
            return;
        }
        if (event.getClickedBlock() != null
                && (daoFields.isRefinery(event.getClickedBlock()) || daoFields.isMirror(event.getClickedBlock()))) {
            return;
        }
        event.setCancelled(true);
        cast(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker && encounters.isEncounterEnemy(event.getEntity())) {
            boolean registeredWeapon = items.isOwnedWeapon(attacker.getInventory().getItemInMainHand(), attacker.getUniqueId());
            if (!registeredWeapon) {
                int dao = daoFields.reading(event.getEntity().getLocation()).dao();
                event.setDamage(event.getDamage() * Math.max(0.18, 1.0 - dao / 100.0));
            } else if (profiles.isEnlisted(attacker) && !activeCasts.contains(attacker.getUniqueId())) {
                applyFormulaStrike(attacker, event);
            }
        }

        if (!(event.getEntity() instanceof Player defender) || !profiles.isEnlisted(defender)) {
            return;
        }
        if (encounters.isEncounterEnemy(event.getDamager())) {
            int dao = daoFields.reading(defender.getLocation()).dao();
            event.setDamage(event.getDamage() * (0.72 + dao / 210.0));
        }
        if (profiles.tendency(defender) == QiTendency.INWARD) {
            event.setDamage(event.getDamage() * 0.90);
        }
        if (profiles.formula(defender) == Formula.ROU
                && guardUntil.getOrDefault(defender.getUniqueId(), 0L) > System.currentTimeMillis()) {
            event.setDamage(event.getDamage() * 0.35);
            if (event.getDamager() instanceof LivingEntity attacker) {
                activeCasts.add(defender.getUniqueId());
                attacker.damage(Math.max(1.0, event.getDamage() * 0.55), defender);
                activeCasts.remove(defender.getUniqueId());
                attacker.setVelocity(attacker.getLocation().toVector().subtract(defender.getLocation().toVector()).normalize().multiply(0.65));
            }
            defender.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, defender.getLocation().add(0, 1, 0), 18, 0.5, 0.7, 0.5, 0.02);
        }
    }

    public void clearRuntimeState() {
        abilityCooldowns.clear();
        guardUntil.clear();
        activeCasts.clear();
    }

    private void cast(Player player) {
        if (!profiles.isEnlisted(player)) {
            player.sendMessage(EvilIslandPlugin.message("先完成炁息測定與軍團報到。"));
            return;
        }
        Formula formula = profiles.formula(player);
        long now = System.currentTimeMillis();
        long readyAt = abilityCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (readyAt > now) {
            player.sendActionBar(Component.text("運炁尚需 " + ((readyAt - now + 999) / 1000) + " 秒恢復"));
            return;
        }
        int cost = plugin.getConfig().getInt("abilities." + formula.id() + ".cost", defaultCost(formula));
        if (!profiles.spendQi(player, cost)) {
            player.sendMessage(EvilIslandPlugin.message("炁息不足，需要 " + cost + "。"));
            return;
        }
        long cooldown = plugin.getConfig().getLong("abilities." + formula.id() + ".cooldown-ms", defaultCooldown(formula));
        abilityCooldowns.put(player.getUniqueId(), now + cooldown);

        switch (formula) {
            case BAO -> castBao(player);
            case QING -> castQing(player);
            case ROU -> castRou(player);
            case NING -> castNing(player);
        }
    }

    private void castBao(Player player) {
        double radius = plugin.getConfig().getDouble("abilities.bao.radius", 5.0);
        if (profiles.tendency(player) == QiTendency.OUTWARD) {
            radius *= 1.2;
        }
        activeCasts.add(player.getUniqueId());
        for (Entity entity : player.getNearbyEntities(radius, radius * 0.65, radius)) {
            if (entity instanceof LivingEntity target && encounters.isEncounterEnemy(target)) {
                target.damage(plugin.getConfig().getDouble("abilities.bao.damage", 8.0), player);
                Vector push = target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(0.75);
                target.setVelocity(push.setY(0.28));
            }
        }
        activeCasts.remove(player.getUniqueId());
        player.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, player.getLocation().add(0, 1, 0), 3, radius / 3, 0.5, radius / 3, 0.02);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.65f, 1.45f);
    }

    private void castQing(Player player) {
        Vector direction = player.getLocation().getDirection().normalize();
        double force = profiles.tendency(player) == QiTendency.OUTWARD ? 1.65 : 1.85;
        player.setVelocity(direction.multiply(force).setY(Math.max(0.22, direction.getY())));
        player.setFallDistance(0);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 34, 0.45, 0.25, 0.45, 0.035);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.45f, 1.75f);
    }

    private void castRou(Player player) {
        guardUntil.put(player.getUniqueId(), System.currentTimeMillis() + plugin.getConfig().getLong("abilities.rou.guard-ms", 2400));
        player.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, player.getLocation().add(0, 1, 0), 42, 0.8, 1.0, 0.8, 0.03);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.7f, 1.25f);
        player.sendActionBar(Component.text("柔訣存想：來勢可卸"));
    }

    private void castNing(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 120, 1, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 100, 0, true, true));
        for (Entity entity : player.getNearbyEntities(5, 3, 5)) {
            if (entity instanceof LivingEntity target && encounters.isEncounterEnemy(target)) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 80, 1, true, true));
            }
        }
        player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0, 1, 0), 36, 0.8, 0.9, 0.8, 0.02);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.8f, 0.72f);
    }

    private void applyFormulaStrike(Player player, EntityDamageByEntityEvent event) {
        Formula formula = profiles.formula(player);
        if (formula == Formula.BAO && profiles.spendQi(player, 4)) {
            event.setDamage(event.getDamage() + (profiles.tendency(player) == QiTendency.INWARD ? 2.6 : 2.2));
            event.getEntity().getWorld().spawnParticle(Particle.SWEEP_ATTACK, event.getEntity().getLocation().add(0, 1, 0), 1);
        } else if (formula == Formula.QING && player.isSprinting() && profiles.spendQi(player, 3)) {
            event.setDamage(event.getDamage() + 1.6);
            player.setFallDistance(0);
        } else if (formula == Formula.NING && profiles.spendQi(player, 4)) {
            event.setDamage(event.getDamage() + 1.0);
            if (event.getEntity() instanceof LivingEntity target) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 30, 0, true, false));
            }
        }
    }

    private int defaultCost(Formula formula) {
        return switch (formula) {
            case BAO -> 24;
            case QING -> 9;
            case ROU -> 14;
            case NING -> 20;
        };
    }

    private long defaultCooldown(Formula formula) {
        return switch (formula) {
            case BAO -> 2400L;
            case QING -> 900L;
            case ROU -> 2600L;
            case NING -> 3200L;
        };
    }
}
