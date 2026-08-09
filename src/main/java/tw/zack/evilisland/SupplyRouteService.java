package tw.zack.evilisland;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import tw.zack.evilisland.model.LivingEventSnapshot;
import tw.zack.evilisland.model.LivingEventState;
import tw.zack.evilisland.model.SupplyRouteRules;
import tw.zack.evilisland.model.SupplyRouteSnapshot;
import tw.zack.evilisland.model.SupplyRouteState;
import tw.zack.evilisland.model.WorldResource;
import tw.zack.evilisland.persistence.DatabaseManager;
import tw.zack.evilisland.persistence.SupplyRouteRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SupplyRouteService implements Listener {
    private final EvilIslandPlugin plugin;
    private final DatabaseManager database;
    private final SupplyRouteRepository repository;
    private final DevelopmentService development;
    private final DaoFieldService daoFields;
    private final CrisisSceneService crisisScenes;
    private final LivingWorldService livingWorld;
    private final NamespacedKey actorKey;
    private SupplyRouteSnapshot route;
    private UUID dispatcherActor;
    private UUID fieldActor;
    private boolean actorsIndexed;
    private RegionControlService regionControl;

    public SupplyRouteService(EvilIslandPlugin plugin, DatabaseManager database, SupplyRouteRepository repository,
                              DevelopmentService development, DaoFieldService daoFields,
                              CrisisSceneService crisisScenes, LivingWorldService livingWorld) {
        this.plugin = plugin;
        this.database = database;
        this.repository = repository;
        this.development = development;
        this.daoFields = daoFields;
        this.crisisScenes = crisisScenes;
        this.livingWorld = livingWorld;
        this.actorKey = new NamespacedKey(plugin, "supply_route_actor");
    }

    public void load() {
        route = repository.active().orElse(null);
    }

    public void setRegionControlService(RegionControlService service) {
        regionControl = service;
    }

    public void reconcile(LivingEventSnapshot active) {
        if (route != null && (active == null || !route.eventId().equals(active.id()))) {
            route = route.close(SupplyRouteState.CANCELLED, System.currentTimeMillis());
            repository.save(route);
            route = null;
        }
        tick();
        refreshActors(active);
    }

    public void open(LivingEventSnapshot event) {
        refreshActors(event);
    }

    public void close(LivingEventSnapshot event) {
        if (route == null || event == null || !route.eventId().equals(event.id())) return;
        if (route.state().active()) {
            SupplyRouteState state = event.state() == LivingEventState.EXPIRED
                    ? SupplyRouteState.EXPIRED : SupplyRouteState.CANCELLED;
            route = route.close(state, System.currentTimeMillis());
            repository.save(route);
        }
        route = null;
        refreshActors(null);
    }

    public void tick() {
        LivingEventSnapshot active = livingWorld.activeEventWithoutSync();
        if (route != null && route.state() == SupplyRouteState.TRANSIT
                && System.currentTimeMillis() >= route.arrivesAt()) {
            route = route.arrive(System.currentTimeMillis());
            repository.save(route);
            Bukkit.broadcast(EvilIslandPlugin.message("補給車隊已抵達「" + (active == null ? "區域現場"
                    : active.type().region().display()) + "」，需要玩家前往接貨。", NamedTextColor.YELLOW));
        }
        refreshActors(active);
    }

    @EventHandler(ignoreCancelled = true)
    public void onActorInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        String role = event.getRightClicked().getPersistentDataContainer().get(actorKey, PersistentDataType.STRING);
        if (role == null) return;
        event.setCancelled(true);
        if (role.equals("dispatcher")) openBoard(event.getPlayer(), false);
        else if (role.equals("field")) receive(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof RouteHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0
                || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        if (event.getRawSlot() == 11) openBoard(player, false);
        if (event.getRawSlot() == 15 && holder.confirm) dispatch(player, holder.eventId);
        if (event.getRawSlot() == 13 && !holder.confirm && route == null) openBoard(player, true);
    }

    public int runSelfTest() {
        int checks = 0;
        Map<WorldResource, Integer> base = Map.of(WorldResource.TIMBER, 4, WorldResource.PROVISIONS, 3);
        Map<WorldResource, Integer> cost = SupplyRouteRules.discountedCost(base, 0.75);
        if (cost.get(WorldResource.TIMBER) == 3 && cost.get(WorldResource.PROVISIONS) == 3) checks++;
        long now = 1_000L;
        if (SupplyRouteRules.arrivalTime(now, 30) == now + 1_800_000L) checks++;
        if (route == null || repository.find(route.eventId()).map(saved -> saved.equals(route)).orElse(false)) checks++;
        Entity dispatcher = dispatcherActor == null ? null : Bukkit.getEntity(dispatcherActor);
        if (dispatcher != null && dispatcher.isValid()) checks++;
        if (route == null || route.state() != SupplyRouteState.ARRIVED
                || fieldActor != null && Bukkit.getEntity(fieldActor) != null) checks++;
        return checks;
    }

    public void flush() {
        SupplyRouteSnapshot snapshot = route;
        if (snapshot != null) database.submit(() -> repository.save(snapshot)).join();
    }

    private void openBoard(Player player, boolean confirm) {
        LivingEventSnapshot active = livingWorld.activeEvent();
        RouteHolder holder = new RouteHolder(confirm, active == null ? null : active.id());
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text(confirm ? "確認補給發車" : "區域補給路線"));
        holder.inventory = inventory;
        if (active == null) {
            inventory.setItem(13, item(Material.CLOCK, "目前沒有待處理危機", List.of("車隊留在新城整備。")));
        } else if (route == null) {
            Map<WorldResource, Integer> cost = routeCost(active);
            inventory.setItem(13, item(Material.CHEST_MINECART, "建立延遲補給路線",
                    List.of("目的地：" + active.type().region().display(), "需要：" + costText(cost),
                            "預計運送：" + durationMinutes() + " 分鐘", "以等待時間換取較低物資成本。")));
        } else if (route.state() == SupplyRouteState.TRANSIT) {
            inventory.setItem(13, item(Material.CLOCK, "補給運送中",
                    List.of("剩餘約 " + remainingMinutes() + " 分鐘", crisisScenes.sceneSummary(active.id()))));
        } else {
            inventory.setItem(13, item(Material.BARREL, "補給等待接貨",
                    List.of(crisisScenes.sceneSummary(active.id()), "前往現場與補給腳夫互動。")));
        }
        if (confirm && active != null && route == null) {
            inventory.setItem(11, item(Material.ARROW, "返回", List.of("不消耗物資。")));
            inventory.setItem(15, item(Material.LIME_CONCRETE, "確認發車",
                    List.of("扣除：" + costText(routeCost(active)), "車隊抵達前仍可能因危機逾期而失敗。")));
        }
        player.openInventory(inventory);
    }

    private void dispatch(Player player, UUID eventId) {
        LivingEventSnapshot active = livingWorld.activeEvent();
        if (active == null || eventId == null || !active.id().equals(eventId) || route != null) {
            openBoard(player, false);
            return;
        }
        Map<WorldResource, Integer> cost = routeCost(active);
        if (!development.spendResources(cost)) {
            player.sendMessage(EvilIslandPlugin.message("公共物資不足，需要「" + costText(cost) + "」。",
                    NamedTextColor.RED));
            openBoard(player, false);
            return;
        }
        long now = System.currentTimeMillis();
        route = new SupplyRouteSnapshot(active.id(), SupplyRouteState.TRANSIT, player.getUniqueId(), null,
                now, SupplyRouteRules.arrivalTime(now, durationMinutes()), now);
        repository.save(route);
        player.closeInventory();
        Bukkit.broadcast(EvilIslandPlugin.message(player.getName() + " 已從新城發出區域補給，預計 "
                + durationMinutes() + " 分鐘後抵達。", NamedTextColor.AQUA));
        refreshActors(active);
    }

    private void receive(Player player) {
        LivingEventSnapshot active = livingWorld.activeEvent();
        if (active == null || !SupplyRouteRules.canReceive(route, active.id(), System.currentTimeMillis())) {
            player.sendMessage(EvilIslandPlugin.message("目前沒有可接收的區域補給。", NamedTextColor.RED));
            return;
        }
        if (route.state() == SupplyRouteState.TRANSIT) route = route.arrive(System.currentTimeMillis());
        boolean relay = !route.dispatcher().equals(player.getUniqueId());
        route = route.receive(player.getUniqueId(), System.currentTimeMillis());
        repository.save(route);
        if (regionControl != null) regionControl.recordSupplyRoute(active.id(), active.type().region());
        livingWorld.resolveSupplyRoute(active.id());
        player.sendMessage(EvilIslandPlugin.message(relay ? "你完成了另一名玩家發起的補給接力。"
                : "你完成了從新城到現場的補給接續。", NamedTextColor.GREEN));
    }

    private void refreshActors(LivingEventSnapshot active) {
        indexActors(active);
        Location city = daoFields.cityCenter();
        if (city != null && city.getWorld() != null && active != null) {
            dispatcherActor = ensureActor("dispatcher", "補給調度員",
                    ground(city.clone().add(-7.5, 0, 4.5)), dispatcherActor);
        } else {
            remove(dispatcherActor);
            dispatcherActor = null;
        }
        Location field = active == null ? null : crisisScenes.sceneLocation(active.id());
        if (field != null && route != null && route.state() == SupplyRouteState.ARRIVED) {
            fieldActor = ensureActor("field", "區域補給腳夫", field.clone().add(1.5, 0, 0), fieldActor);
        } else {
            remove(fieldActor);
            fieldActor = null;
        }
    }

    private void indexActors(LivingEventSnapshot active) {
        if (actorsIndexed || daoFields.cityCenter() == null || daoFields.cityCenter().getWorld() == null) return;
        actorsIndexed = true;
        boolean needField = active != null && route != null && route.state() == SupplyRouteState.ARRIVED;
        for (Entity entity : daoFields.cityCenter().getWorld().getEntities()) {
            String role = entity.getPersistentDataContainer().get(actorKey, PersistentDataType.STRING);
            if (role == null) continue;
            if (role.equals("dispatcher") && active != null && dispatcherActor == null) {
                dispatcherActor = entity.getUniqueId();
            } else if (role.equals("field") && needField && fieldActor == null) {
                fieldActor = entity.getUniqueId();
            } else {
                entity.remove();
            }
        }
    }

    private Location ground(Location location) {
        int y = location.getWorld().getHighestBlockYAt(location.getBlockX(), location.getBlockZ()) + 1;
        return new Location(location.getWorld(), location.getBlockX() + 0.5, y, location.getBlockZ() + 0.5);
    }

    private UUID ensureActor(String role, String name, Location location, UUID known) {
        Entity existing = known == null ? null : Bukkit.getEntity(known);
        if (existing instanceof Villager villager && villager.isValid()) {
            villager.teleport(location);
            return known;
        }
        for (Entity entity : location.getWorld().getEntities()) {
            if (role.equals(entity.getPersistentDataContainer().get(actorKey, PersistentDataType.STRING))) {
                entity.teleport(location);
                return entity.getUniqueId();
            }
        }
        Villager villager = location.getWorld().spawn(location, Villager.class, actor -> {
            actor.customName(Component.text(name, NamedTextColor.GOLD));
            actor.setCustomNameVisible(true);
            actor.setAI(false);
            actor.setInvulnerable(true);
            actor.setPersistent(true);
            actor.setRemoveWhenFarAway(false);
            actor.getPersistentDataContainer().set(actorKey, PersistentDataType.STRING, role);
        });
        return villager.getUniqueId();
    }

    private void remove(UUID id) {
        Entity entity = id == null ? null : Bukkit.getEntity(id);
        if (entity != null) entity.remove();
    }

    private Map<WorldResource, Integer> routeCost(LivingEventSnapshot event) {
        return SupplyRouteRules.discountedCost(event.type().logisticsCost(),
                plugin.getConfig().getDouble("living-world.supply-route.cost-multiplier", 0.75));
    }

    private long durationMinutes() {
        return Math.max(1L, plugin.getConfig().getLong("living-world.supply-route.duration-minutes", 30L));
    }

    private long remainingMinutes() {
        if (route == null) return 0;
        return Math.max(1L, (route.arrivesAt() - System.currentTimeMillis() + 59_999L) / 60_000L);
    }

    private String costText(Map<WorldResource, Integer> cost) {
        List<String> values = new ArrayList<>();
        cost.forEach((resource, amount) -> values.add(resource.display() + "×" + amount));
        return String.join("、", values);
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.YELLOW));
        meta.lore(lore.stream().map(line -> Component.text(line, NamedTextColor.GRAY)).toList());
        stack.setItemMeta(meta);
        return stack;
    }

    private static final class RouteHolder implements InventoryHolder {
        private final boolean confirm;
        private final UUID eventId;
        private Inventory inventory;

        private RouteHolder(boolean confirm, UUID eventId) {
            this.confirm = confirm;
            this.eventId = eventId;
        }

        @Override public Inventory getInventory() { return inventory; }
    }
}
