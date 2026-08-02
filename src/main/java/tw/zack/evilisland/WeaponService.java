package tw.zack.evilisland;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import tw.zack.evilisland.model.WeaponType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

public final class WeaponService implements Listener {
    private final EvilIslandPlugin plugin;
    private final PlayerProfileService profiles;
    private final GameItemService items;
    private final Map<UUID, Long> techniqueCooldowns = new HashMap<>();
    private final Map<UUID, Stance> stances = new HashMap<>();
    private Predicate<Entity> enemyResolver = entity -> false;

    public WeaponService(EvilIslandPlugin plugin, PlayerProfileService profiles, GameItemService items) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.items = items;
    }

    public boolean hasWeapon(Player player) {
        return items.hasOwnedWeapon(player.getInventory(), player.getUniqueId());
    }

    public void setEnemyResolver(Predicate<Entity> enemyResolver) {
        this.enemyResolver = enemyResolver;
    }

    public void openArmory(Player player) {
        ArmoryHolder holder = new ArmoryHolder();
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text("歲安軍團制式兵器"));
        holder.inventory = inventory;
        int[] slots = {10, 11, 12, 14, 15, 16};
        WeaponType[] types = WeaponType.values();
        for (int index = 0; index < types.length; index++) {
            WeaponType type = types[index];
            holder.options.put(slots[index], type);
            inventory.setItem(slots[index], preview(type));
        }
        player.openInventory(inventory);
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof ArmoryHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getRawSlot() < 0 || event.getRawSlot() >= top.getSize()) {
            return;
        }
        WeaponType selected = holder.options.get(event.getRawSlot());
        if (selected == null) {
            return;
        }
        if (hasWeapon(player)) {
            player.closeInventory();
            player.sendMessage(EvilIslandPlugin.message("你已領有一件登記兵器；更換兵器需等軍械庫系統開放。"));
            return;
        }
        player.getInventory().addItem(items.createWeapon(selected, player.getUniqueId()));
        player.closeInventory();
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 0.8f, 1.0f);
        player.sendMessage(EvilIslandPlugin.message("已領取" + selected.display() + "。再次右鍵撼山巡防員即可報到。", NamedTextColor.GREEN));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (items.isOwnedWeapon(event.getItemDrop().getItemStack(), event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Component.text("登記兵器不可丟棄", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onTechnique(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getPlayer().isSneaking()
                || !event.getAction().isRightClick() || event.getClickedBlock() != null) {
            return;
        }
        Player player = event.getPlayer();
        WeaponType type = items.weaponType(player.getInventory().getItemInMainHand());
        if (type == null || !items.isOwnedWeapon(player.getInventory().getItemInMainHand(), player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        if (!profiles.isEnlisted(player)) {
            player.sendMessage(EvilIslandPlugin.message("先完成炁息測定與存想定型。"));
            return;
        }
        long now = System.currentTimeMillis();
        long readyAt = techniqueCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (readyAt > now) {
            player.sendActionBar(Component.text("兵器招式尚需 " + ((readyAt - now + 999) / 1000) + " 秒恢復"));
            return;
        }
        techniqueCooldowns.put(player.getUniqueId(), now + cooldown(type));
        useTechnique(player, type);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStanceDefense(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Stance stance = stances.get(player.getUniqueId());
        if (stance == null || stance.expiresAt < System.currentTimeMillis()) {
            stances.remove(player.getUniqueId());
            return;
        }
        double multiplier = switch (stance.type) {
            case DUAL_BATONS -> 0.55;
            case SWORD -> 0.35;
            case SHIELD_BLADE -> 0.40;
            default -> 1.0;
        };
        event.setDamage(event.getDamage() * multiplier);
        if (stance.type == WeaponType.SWORD && event.getDamager() instanceof LivingEntity attacker) {
            Vector away = attacker.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
            attacker.setVelocity(away.multiply(0.55).setY(0.2));
            stances.remove(player.getUniqueId());
        }
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.7f, 1.1f);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onWeaponStrike(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !enemyResolver.test(event.getEntity())) {
            return;
        }
        WeaponType type = items.weaponType(player.getInventory().getItemInMainHand());
        if (type == null || !items.isOwnedWeapon(player.getInventory().getItemInMainHand(), player.getUniqueId())) {
            return;
        }
        event.setDamage(switch (type) {
            case SPEAR -> 6.5;
            case DUAL_BATONS -> 5.5;
            case SABER -> 8.0;
            case SWORD -> 6.5;
            case DAGGERS -> 4.5;
            case SHIELD_BLADE -> 5.0;
        });
    }

    public void clearRuntimeState() {
        techniqueCooldowns.clear();
        stances.clear();
    }

    private void useTechnique(Player player, WeaponType type) {
        switch (type) {
            case SPEAR -> strike(player, type, 5.5, 7.0, 0.85);
            case SABER -> strike(player, type, 3.6, 9.0, 0.45);
            case DAGGERS -> {
                Vector direction = player.getLocation().getDirection().normalize();
                player.setVelocity(direction.multiply(1.25).setY(Math.max(0.15, direction.getY())));
                player.setFallDistance(0);
                player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 18, 0.3, 0.2, 0.3, 0.02);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.6f, 1.7f);
            }
            case DUAL_BATONS -> stance(player, type, 3000L, "雙鐧架勢：正面承勁");
            case SWORD -> stance(player, type, 1800L, "截勢反擊：等待來攻");
            case SHIELD_BLADE -> {
                stance(player, type, 4200L, "沉身固守：穩住防線");
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 90, 0, true, false));
            }
        }
    }

    private void strike(Player player, WeaponType type, double range, double damage, double knockback) {
        LivingEntity target = findTarget(player, range);
        if (target == null) {
            player.sendActionBar(Component.text(type.technique() + "落空"));
            return;
        }
        target.damage(damage, player);
        Vector push = target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
        target.setVelocity(push.multiply(knockback).setY(0.18));
        target.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1, 0), 1);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.8f,
                type == WeaponType.SPEAR ? 1.35f : 0.8f);
    }

    private LivingEntity findTarget(Player player, double range) {
        Vector origin = player.getEyeLocation().toVector();
        Vector direction = player.getEyeLocation().getDirection().normalize();
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : player.getNearbyEntities(range, range, range)) {
            if (!(entity instanceof LivingEntity target) || target.equals(player) || !enemyResolver.test(target)) {
                continue;
            }
            Vector offset = target.getLocation().add(0, target.getHeight() * 0.5, 0).toVector().subtract(origin);
            double forward = offset.dot(direction);
            if (forward < 0 || forward > range) {
                continue;
            }
            double sideways = offset.clone().subtract(direction.clone().multiply(forward)).length();
            if (sideways <= 1.15 && forward < bestDistance && player.hasLineOfSight(target)) {
                best = target;
                bestDistance = forward;
            }
        }
        return best;
    }

    private void stance(Player player, WeaponType type, long duration, String message) {
        stances.put(player.getUniqueId(), new Stance(type, System.currentTimeMillis() + duration));
        player.sendActionBar(Component.text(message));
        player.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, player.getLocation().add(0, 1, 0),
                20, 0.55, 0.75, 0.55, 0.02);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.7f, 0.9f);
    }

    private ItemStack preview(WeaponType type) {
        ItemStack item = new ItemStack(type.material());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(type.display(), NamedTextColor.AQUA));
        meta.lore(List.of(
                Component.text(type.technique(), NamedTextColor.YELLOW),
                Component.text("兵器可以更換，不改變炁訣定型。", NamedTextColor.GRAY)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private long cooldown(WeaponType type) {
        return switch (type) {
            case SPEAR -> 1800L;
            case DUAL_BATONS -> 4200L;
            case SABER -> 2600L;
            case SWORD -> 3000L;
            case DAGGERS -> 1400L;
            case SHIELD_BLADE -> 5200L;
        };
    }

    private record Stance(WeaponType type, long expiresAt) {
    }

    private static final class ArmoryHolder implements InventoryHolder {
        private Inventory inventory;
        private final Map<Integer, WeaponType> options = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
