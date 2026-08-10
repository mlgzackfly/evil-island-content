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
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import tw.zack.evilisland.model.WeaponType;
import tw.zack.evilisland.model.TechniquePath;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.BiFunction;
import java.util.function.ToIntBiFunction;
import java.util.function.IntSupplier;
import java.util.function.Consumer;

public final class WeaponService implements Listener {
    private final EvilIslandPlugin plugin;
    private final PlayerProfileService profiles;
    private final GameItemService items;
    private final Map<UUID, Long> techniqueCooldowns = new HashMap<>();
    private final Map<UUID, Stance> stances = new HashMap<>();
    private final Map<UUID, Long> maintenanceNotices = new HashMap<>();
    private Predicate<Entity> enemyResolver = entity -> false;
    private BiFunction<Player, WeaponType, TechniquePath> techniqueResolver = (player, weapon) -> TechniquePath.UNTRAINED;
    private ToIntBiFunction<Player, WeaponType> masteryTierResolver = (player, weapon) -> 0;
    private IntSupplier workshopLevelResolver = () -> 0;
    private Predicate<Block> maintenanceStationResolver = block -> false;
    private Consumer<Player> weaponClaimListener = ignored -> { };

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

    public void setTechniqueResolver(BiFunction<Player, WeaponType, TechniquePath> techniqueResolver,
                                     ToIntBiFunction<Player, WeaponType> masteryTierResolver) {
        this.techniqueResolver = techniqueResolver;
        this.masteryTierResolver = masteryTierResolver;
    }

    public void setWorkshopLevelResolver(IntSupplier workshopLevelResolver) {
        this.workshopLevelResolver = workshopLevelResolver;
    }

    public void setMaintenanceStationResolver(Predicate<Block> maintenanceStationResolver) {
        this.maintenanceStationResolver = maintenanceStationResolver;
    }

    public void setWeaponClaimListener(Consumer<Player> listener) {
        weaponClaimListener = listener == null ? ignored -> { } : listener;
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
        ItemStack current = items.ownedWeapon(player.getInventory(), player.getUniqueId());
        WeaponType currentType = items.weaponType(current);
        if (currentType != null) {
            if (currentType == selected) {
                player.sendActionBar(Component.text("目前已登記使用" + selected.display(), NamedTextColor.GRAY));
                return;
            }
            int requiredLevel = plugin.getConfig().getInt("weapons.refit-workshop-level", 2);
            if (workshopLevelResolver.getAsInt() < requiredLevel) {
                player.closeInventory();
                player.sendMessage(EvilIslandPlugin.message("軍械工坊目前可用階段需達 " + requiredLevel
                        + " 才能更換登記兵器；受損也可能使設施降效。", NamedTextColor.RED));
                return;
            }
            int cost = plugin.getConfig().getInt("weapons.refit-iron-cost", 4);
            if (!removeMaterial(player, Material.IRON_INGOT, cost)) {
                player.closeInventory();
                player.sendMessage(EvilIslandPlugin.message("重新配裝需要 " + cost + " 個鐵錠。",
                        NamedTextColor.RED));
                return;
            }
            double wear = current.getType().getMaxDurability() <= 1 ? 0.0
                    : (double) items.weaponDamage(current) / (current.getType().getMaxDurability() - 1);
            items.removeOwnedWeapons(player.getInventory(), player.getUniqueId());
            ItemStack replacement = items.createWeapon(selected, player.getUniqueId());
            items.setWeaponDamage(replacement, (int) Math.round(
                    wear * Math.max(0, replacement.getType().getMaxDurability() - 1)));
            player.getInventory().addItem(replacement);
            player.closeInventory();
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 0.8f, 1.0f);
            player.sendMessage(EvilIslandPlugin.message("已改用" + selected.display()
                    + "；原有耗損比例已轉入新兵器，各兵器熟練分別保留。", NamedTextColor.GREEN));
            return;
        }
        player.getInventory().addItem(items.createWeapon(selected, player.getUniqueId()));
        player.closeInventory();
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 0.8f, 1.0f);
        player.sendMessage(EvilIslandPlugin.message("已領取" + selected.display() + "。再次右鍵撼山巡防員即可報到。", NamedTextColor.GREEN));
        weaponClaimListener.accept(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (items.isOwnedWeapon(event.getItemDrop().getItemStack(), event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Component.text("登記兵器不可丟棄", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        for (ItemStack stack : player.getInventory().getContents()) {
            items.normalizeWeapon(stack, player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMaintenance(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick()
                || !event.getPlayer().isSneaking() || event.getClickedBlock() == null
                || !maintenanceStationResolver.test(event.getClickedBlock())) return;
        Player player = event.getPlayer();
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (!items.normalizeWeapon(weapon, player.getUniqueId())) return;
        event.setCancelled(true);
        int level = workshopLevelResolver.getAsInt();
        if (level <= 0) {
            player.sendMessage(EvilIslandPlugin.message("軍械工坊目前無法運作，請先建設或修復設施。",
                    NamedTextColor.RED));
            return;
        }
        int damage = items.weaponDamage(weapon);
        if (damage <= 0) {
            player.sendActionBar(Component.text("兵器狀況良好，暫不需要整備", NamedTextColor.GRAY));
            return;
        }
        int repairPerIngot = plugin.getConfig().getInt("weapons.repair-base-per-ingot", 100)
                + level * plugin.getConfig().getInt("weapons.repair-level-bonus", 60);
        int cost = Math.max(1, (damage + repairPerIngot - 1) / repairPerIngot);
        if (!removeMaterial(player, Material.IRON_INGOT, cost)) {
            player.sendMessage(EvilIslandPlugin.message("整備需要 " + cost + " 個鐵錠；工坊等級越高越省材料。",
                    NamedTextColor.RED));
            return;
        }
        items.setWeaponDamage(weapon, 0);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.15f);
        player.sendMessage(EvilIslandPlugin.message("兵器整備完成，消耗 " + cost + " 個鐵錠。",
                NamedTextColor.GREEN));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWeaponWear(PlayerItemDamageEvent event) {
        Player player = event.getPlayer();
        ItemStack weapon = event.getItem();
        if (!items.normalizeWeapon(weapon, player.getUniqueId())) return;
        int maxDamage = Math.max(0, weapon.getType().getMaxDurability() - items.weaponDamage(weapon) - 1);
        if (maxDamage == 0) event.setCancelled(true);
        else if (event.getDamage() > maxDamage) event.setDamage(maxDamage);
        if (maxDamage <= Math.max(1, weapon.getType().getMaxDurability() / 8)) {
            long now = System.currentTimeMillis();
            if (now >= maintenanceNotices.getOrDefault(player.getUniqueId(), 0L)) {
                maintenanceNotices.put(player.getUniqueId(), now + 15000L);
                player.sendActionBar(Component.text("兵器已嚴重耗損，返回軍械工坊整備", NamedTextColor.RED));
            }
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
        applyHorizontalTechnique(player, type);
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
        maintenanceNotices.clear();
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

    private void applyHorizontalTechnique(Player player, WeaponType weapon) {
        TechniquePath path = techniqueResolver.apply(player, weapon);
        int tier = Math.max(1, masteryTierResolver.applyAsInt(player, weapon));
        switch (path) {
            case MOBILITY -> player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                    30 + tier * 10, 0, true, false, true));
            case CONTROL -> {
                double radius = 3.5 + tier * 0.5;
                for (Entity entity : player.getNearbyEntities(radius, 3.0, radius)) {
                    if (entity instanceof LivingEntity target && enemyResolver.test(target)) {
                        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW,
                                24 + tier * 8, 0, true, false, true));
                    }
                }
            }
            case GUARD -> player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE,
                    25 + tier * 8, 0, true, false, true));
            case UNTRAINED -> { }
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
                Component.text("工坊階段 2 可付費更換登記兵器。", NamedTextColor.GRAY),
                Component.text("熟練與炁訣定型不會因此重置。", NamedTextColor.DARK_GRAY)
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

    private boolean removeMaterial(Player player, Material material, int amount) {
        if (!player.getInventory().containsAtLeast(new ItemStack(material), amount)) return false;
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || stack.getType() != material) continue;
            int taken = Math.min(remaining, stack.getAmount());
            remaining -= taken;
            if (taken == stack.getAmount()) player.getInventory().setItem(slot, null);
            else {
                stack.setAmount(stack.getAmount() - taken);
                player.getInventory().setItem(slot, stack);
            }
        }
        return true;
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
