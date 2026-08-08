package tw.zack.evilisland;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import tw.zack.evilisland.model.InheritanceSnapshot;
import tw.zack.evilisland.model.InheritanceType;
import tw.zack.evilisland.model.MissionContract;
import tw.zack.evilisland.persistence.DatabaseManager;
import tw.zack.evilisland.persistence.GrowthRepository;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;

public final class InheritanceService implements Listener {
    private final EvilIslandPlugin plugin;
    private final DatabaseManager database;
    private final GrowthRepository repository;
    private final PlayerProfileService profiles;
    private final Map<UUID, Map<InheritanceType, InheritanceSnapshot>> preloaded = new ConcurrentHashMap<>();
    private final Map<UUID, Map<InheritanceType, InheritanceSnapshot>> states = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private Predicate<Entity> enemyResolver = ignored -> false;
    private Consumer<Player> hubOpener = Player::closeInventory;

    public InheritanceService(EvilIslandPlugin plugin, DatabaseManager database, GrowthRepository repository,
                              PlayerProfileService profiles) {
        this.plugin = plugin;
        this.database = database;
        this.repository = repository;
        this.profiles = profiles;
    }

    public void setEnemyResolver(Predicate<Entity> enemyResolver) {
        this.enemyResolver = enemyResolver;
    }

    public void setHubOpener(Consumer<Player> hubOpener) {
        this.hubOpener = hubOpener;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        try {
            preloaded.put(event.getUniqueId(), repository.loadInheritances(event.getUniqueId()));
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Cannot load inheritance state", exception);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    Component.text("傳承資料載入失敗，請稍後再試。", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        Map<InheritanceType, InheritanceSnapshot> loaded = preloaded.remove(playerId);
        states.put(playerId, normalize(playerId, loaded == null ? repository.loadInheritances(playerId) : loaded));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        Map<InheritanceType, InheritanceSnapshot> state = states.remove(playerId);
        cooldowns.remove(playerId);
        if (state != null) saveAsync(playerId, state);
    }

    public void openMenu(Player player) {
        InheritanceHolder holder = new InheritanceHolder();
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text("傳承修習與調息"));
        holder.inventory = inventory;
        int[] slots = {10, 12, 14, 16};
        InheritanceType[] types = InheritanceType.values();
        for (int index = 0; index < types.length; index++) {
            holder.options.put(slots[index], types[index]);
            inventory.setItem(slots[index], item(player, types[index]));
        }
        inventory.setItem(22, menuItem(Material.ARROW, "返回新城發展", NamedTextColor.GREEN, List.of()));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof InheritanceHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0
                || event.getRawSlot() >= top.getSize()) return;
        if (event.getRawSlot() == 22) {
            hubOpener.accept(player);
            return;
        }
        InheritanceType type = holder.options.get(event.getRawSlot());
        if (type != null) advance(player, type);
    }

    public void recordMission(MissionContract contract, Set<UUID> members, boolean fullReward) {
        if (!fullReward) return;
        for (UUID playerId : members) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) continue;
            Map<InheritanceType, InheritanceSnapshot> values = state(player);
            for (InheritanceType type : InheritanceType.values()) {
                InheritanceSnapshot current = values.get(type);
                if (!current.completed() && current.progress() == 1
                        && type.missionType() == contract.missionType()) {
                    InheritanceSnapshot updated = new InheritanceSnapshot(playerId, type, 2,
                            false, false, System.currentTimeMillis());
                    values.put(type, updated);
                    saveAsync(playerId, values);
                    player.sendMessage(EvilIslandPlugin.message(type.display()
                            + "的任務考驗已完成，返回新城繳交修習材料。", NamedTextColor.GOLD));
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onAbility(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking() || !profiles.isEnlisted(player)) return;
        InheritanceType type = attuned(player);
        if (type == null) return;
        event.setCancelled(true);
        long now = System.currentTimeMillis();
        long remaining = cooldowns.getOrDefault(player.getUniqueId(), 0L) - now;
        if (remaining > 0) {
            player.sendActionBar(Component.text("傳承調息尚需 " + Math.max(1, (remaining + 999) / 1000) + " 秒",
                    NamedTextColor.GRAY));
            return;
        }
        int qi = plugin.getConfig().getInt("inheritance.qi-cost", 20);
        if (!profiles.spendQi(player, qi)) {
            player.sendMessage(EvilIslandPlugin.message("炁息不足，傳承運用需要 " + qi + " 點。",
                    NamedTextColor.RED));
            return;
        }
        if (activate(player, type)) {
            cooldowns.put(player.getUniqueId(), now
                    + plugin.getConfig().getLong("inheritance.cooldown-ms", 20000L));
        } else {
            profiles.addQi(player, qi);
        }
    }

    public InheritanceType attuned(Player player) {
        return state(player).values().stream().filter(InheritanceSnapshot::completed)
                .filter(InheritanceSnapshot::attuned).map(InheritanceSnapshot::inheritance)
                .findFirst().orElse(null);
    }

    public String summary(Player player) {
        Map<InheritanceType, InheritanceSnapshot> values = state(player);
        long completed = values.values().stream().filter(InheritanceSnapshot::completed).count();
        InheritanceType attuned = attuned(player);
        return "完成 " + completed + "/" + InheritanceType.values().length + "　調息："
                + (attuned == null ? "未選擇" : attuned.display());
    }

    public int runSelfTest() {
        int checks = 0;
        if (InheritanceType.values().length == 4) checks++;
        if (java.util.Arrays.stream(InheritanceType.values()).map(InheritanceType::missionType).distinct().count() == 4) checks++;
        if (java.util.Arrays.stream(InheritanceType.values()).allMatch(type -> type.materialAmount() > 0)) checks++;
        if (InheritanceType.parse("magic") == InheritanceType.MAGIC) checks++;
        if (normalize(UUID.randomUUID(), Map.of()).size() == 4) checks++;
        return checks;
    }

    public void flush() {
        List<CompletableFuture<Void>> saves = new ArrayList<>();
        states.forEach((playerId, values) -> saves.add(saveAsync(playerId, values)));
        CompletableFuture.allOf(saves.toArray(CompletableFuture[]::new)).join();
    }

    public void reset(Player player) {
        UUID playerId = player.getUniqueId();
        states.remove(playerId);
        preloaded.remove(playerId);
        cooldowns.remove(playerId);
    }

    private void advance(Player player, InheritanceType type) {
        if (!profiles.isEnlisted(player)) {
            player.sendMessage(EvilIslandPlugin.message("先完成軍團報到與炁訣定型。"));
            return;
        }
        Map<InheritanceType, InheritanceSnapshot> values = state(player);
        InheritanceSnapshot current = values.get(type);
        long now = System.currentTimeMillis();
        if (current.completed()) {
            for (InheritanceType candidate : InheritanceType.values()) {
                InheritanceSnapshot value = values.get(candidate);
                values.put(candidate, new InheritanceSnapshot(player.getUniqueId(), candidate,
                        value.progress(), value.completed(), candidate == type, now));
            }
            saveAsync(player.getUniqueId(), values);
            player.sendMessage(EvilIslandPlugin.message("目前調息改為「" + type.display() + "」。",
                    NamedTextColor.GREEN));
            openMenu(player);
            return;
        }
        if (current.progress() == 0) {
            boolean otherActive = values.values().stream().anyMatch(value -> !value.completed()
                    && value.progress() > 0 && value.inheritance() != type);
            if (otherActive) {
                player.sendMessage(EvilIslandPlugin.message("一次只能進行一條未完成傳承，請先完成目前修習。"));
                return;
            }
            values.put(type, new InheritanceSnapshot(player.getUniqueId(), type, 1,
                    false, false, now));
            saveAsync(player.getUniqueId(), values);
            player.sendMessage(EvilIslandPlugin.message("開始修習「" + type.display() + "」，需完成一份「"
                    + type.missionType().display() + "」任務。", NamedTextColor.YELLOW));
            openMenu(player);
            return;
        }
        if (current.progress() == 1) {
            player.sendMessage(EvilIslandPlugin.message("尚需完成一份「" + type.missionType().display() + "」任務。"));
            return;
        }
        if (count(player, type.material()) < type.materialAmount()) {
            player.sendMessage(EvilIslandPlugin.message("修習材料不足，需要 " + type.materialAmount() + " 個"
                    + materialName(type.material()) + "。", NamedTextColor.RED));
            return;
        }
        remove(player, type.material(), type.materialAmount());
        boolean first = values.values().stream().noneMatch(InheritanceSnapshot::completed);
        values.put(type, new InheritanceSnapshot(player.getUniqueId(), type, 2,
                true, first, now));
        saveAsync(player.getUniqueId(), values);
        player.sendMessage(EvilIslandPlugin.message(type.display() + "已完成。"
                + (first ? "並設為目前調息。" : "可再次點擊切換調息。"), NamedTextColor.GOLD));
        openMenu(player);
    }

    private boolean activate(Player player, InheritanceType type) {
        boolean activated = switch (type) {
            case MAGIC -> {
                yield magicStep(player);
            }
            case LIGHT_SPIRIT -> {
                lightPulse(player);
                yield true;
            }
            case MOUNTAIN_SLEEP -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 140, 1,
                        true, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 140, 0,
                        true, false, true));
                player.getWorld().spawnParticle(Particle.BLOCK_CRACK, player.getLocation(), 24,
                        0.8, 0.3, 0.8, 0.0, Material.MOSS_BLOCK.createBlockData());
                yield true;
            }
            case BINDING -> bindNearest(player);
        };
        if (activated) {
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.2f);
        }
        return activated;
    }

    private boolean magicStep(Player player) {
        Location origin = player.getLocation();
        org.bukkit.util.Vector direction = origin.getDirection().setY(0);
        if (direction.lengthSquared() < 0.01) {
            player.sendMessage(EvilIslandPlugin.message("需面向可移形的方向，炁息未消耗。"));
            return false;
        }
        direction.normalize();
        Location destination = origin.clone();
        for (int distance = 1; distance <= 6; distance++) {
            Location candidate = origin.clone().add(direction.clone().multiply(distance));
            if (!candidate.getBlock().isPassable() || !candidate.clone().add(0, 1, 0).getBlock().isPassable()) break;
            destination = candidate;
        }
        if (destination.distanceSquared(origin) < 0.25) {
            player.sendMessage(EvilIslandPlugin.message("前方沒有可移形的空間，炁息未消耗。"));
            return false;
        }
        player.getWorld().spawnParticle(Particle.PORTAL, origin.add(0, 1, 0), 28, 0.45, 0.7, 0.45, 0.2);
        player.teleport(destination);
        return true;
    }

    private void lightPulse(Player player) {
        double radius = plugin.getConfig().getDouble("inheritance.light-radius", 8.0);
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof Player ally && profiles.isEnlisted(ally)) healAndRestore(ally);
        }
        healAndRestore(player);
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0),
                36, 1.5, 0.8, 1.5, 0.04);
    }

    private void healAndRestore(Player player) {
        AttributeInstance max = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        player.setHealth(Math.min(max == null ? 20.0 : max.getValue(), player.getHealth() + 3.0));
        profiles.addQi(player, 8);
    }

    private boolean bindNearest(Player player) {
        LivingEntity target = null;
        double nearest = 12.0 * 12.0;
        for (Entity entity : player.getNearbyEntities(12, 8, 12)) {
            if (!(entity instanceof LivingEntity living) || !enemyResolver.test(entity)) continue;
            double distance = living.getLocation().distanceSquared(player.getLocation());
            if (distance < nearest) {
                nearest = distance;
                target = living;
            }
        }
        if (target == null) {
            player.sendMessage(EvilIslandPlugin.message("附近沒有可縛制的敵對妖族，炁息未消耗。"));
            return false;
        }
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 100, 3, true, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 1, true, true, true));
        target.getWorld().spawnParticle(Particle.CRIT_MAGIC, target.getLocation().add(0, 1, 0),
                24, 0.5, 0.8, 0.5, 0.02);
        return true;
    }

    private Map<InheritanceType, InheritanceSnapshot> state(Player player) {
        return states.computeIfAbsent(player.getUniqueId(), id -> normalize(id, repository.loadInheritances(id)));
    }

    private Map<InheritanceType, InheritanceSnapshot> normalize(UUID playerId,
                                                                 Map<InheritanceType, InheritanceSnapshot> loaded) {
        Map<InheritanceType, InheritanceSnapshot> result = new EnumMap<>(InheritanceType.class);
        result.putAll(loaded);
        long now = System.currentTimeMillis();
        for (InheritanceType type : InheritanceType.values()) {
            result.putIfAbsent(type, new InheritanceSnapshot(playerId, type, 0,
                    false, false, now));
        }
        return result;
    }

    private CompletableFuture<Void> saveAsync(UUID playerId,
                                               Map<InheritanceType, InheritanceSnapshot> values) {
        Map<InheritanceType, InheritanceSnapshot> copy = new EnumMap<>(values);
        return database.submit(() -> repository.replaceInheritances(playerId, copy)).exceptionally(exception -> {
            plugin.getLogger().log(Level.SEVERE, "Cannot save inheritance state", exception);
            return null;
        });
    }

    private ItemStack item(Player player, InheritanceType type) {
        InheritanceSnapshot state = state(player).get(type);
        List<String> lore = new ArrayList<>();
        lore.add(type.ability());
        if (state.completed()) lore.add(state.attuned() ? "目前調息；潛行換手可運用。" : "已完成；點擊切換調息。");
        else if (state.progress() == 0) lore.add("點擊開始；考驗為「" + type.missionType().display() + "」。");
        else if (state.progress() == 1) lore.add("進行中：完成一份「" + type.missionType().display() + "」任務。");
        else lore.add("繳交：" + type.materialAmount() + " 個" + materialName(type.material()) + "。");
        return menuItem(type.icon(), type.display(), state.attuned() ? NamedTextColor.GREEN
                : state.completed() ? NamedTextColor.AQUA : NamedTextColor.GOLD, lore);
    }

    private ItemStack menuItem(Material material, String name, NamedTextColor color, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color));
        meta.lore(lore.stream().map(line -> Component.text(line, NamedTextColor.GRAY)).toList());
        item.setItemMeta(meta);
        return item;
    }

    private int count(Player player, Material material) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && stack.getType() == material) total += stack.getAmount();
        }
        return total;
    }

    private void remove(Player player, Material material, int amount) {
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
    }

    private String materialName(Material material) {
        return switch (material) {
            case AMETHYST_SHARD -> "紫水晶碎片";
            case GLOWSTONE_DUST -> "螢石粉";
            case MOSS_BLOCK -> "苔蘚方塊";
            case STRING -> "線";
            default -> material.name().toLowerCase(java.util.Locale.ROOT);
        };
    }

    private static final class InheritanceHolder implements InventoryHolder {
        private Inventory inventory;
        private final Map<Integer, InheritanceType> options = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
