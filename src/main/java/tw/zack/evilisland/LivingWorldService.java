package tw.zack.evilisland;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import tw.zack.evilisland.model.CampaignSnapshot;
import tw.zack.evilisland.model.LivingEventApproach;
import tw.zack.evilisland.model.LivingEventArc;
import tw.zack.evilisland.model.LivingEventRules;
import tw.zack.evilisland.model.LivingEventSnapshot;
import tw.zack.evilisland.model.LivingEventState;
import tw.zack.evilisland.model.LivingEventType;
import tw.zack.evilisland.model.MissionContract;
import tw.zack.evilisland.model.WorldResource;
import tw.zack.evilisland.persistence.DatabaseManager;
import tw.zack.evilisland.persistence.LivingEventRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class LivingWorldService implements Listener {
    private final EvilIslandPlugin plugin;
    private final DatabaseManager database;
    private final LivingEventRepository repository;
    private final CampaignService campaign;
    private final DevelopmentService development;
    private final DaoFieldService daoFields;
    private final NamespacedKey actorKey;
    private final NamespacedKey noticeKey;
    private final List<LivingEventSnapshot> history = new ArrayList<>();
    private LivingEventSnapshot active;
    private UUID messengerId;
    private UUID billboardId;
    private Consumer<Player> missionBoardOpener = ignored -> { };
    private CrisisSceneService crisisScenes;
    private SupplyRouteService supplyRoutes;
    private ResidentIntelService residentIntel;
    private RegionControlService regionControl;

    public LivingWorldService(EvilIslandPlugin plugin, DatabaseManager database, LivingEventRepository repository,
                              CampaignService campaign, DevelopmentService development, DaoFieldService daoFields) {
        this.plugin = plugin;
        this.database = database;
        this.repository = repository;
        this.campaign = campaign;
        this.development = development;
        this.daoFields = daoFields;
        this.actorKey = new NamespacedKey(plugin, "living_world_actor");
        this.noticeKey = new NamespacedKey(plugin, "living_world_notice");
    }

    public void setMissionBoardOpener(Consumer<Player> opener) {
        missionBoardOpener = opener == null ? ignored -> { } : opener;
    }

    public void setCrisisSceneService(CrisisSceneService service) {
        crisisScenes = service;
    }

    public void setSupplyRouteService(SupplyRouteService service) {
        supplyRoutes = service;
    }

    public void setResidentIntelService(ResidentIntelService service) {
        residentIntel = service;
    }

    public void setRegionControlService(RegionControlService service) {
        regionControl = service;
    }

    public void load() {
        history.clear();
        history.addAll(repository.findRecent(retention()));
        active = repository.active().orElse(null);
        sync();
        refreshScene();
        plugin.getLogger().info("Living world loaded with " + history.size() + " crisis records ("
                + (active == null ? "none" : active.type().id()) + " active).");
    }

    public void tick() {
        sync();
        refreshMessengerLocation();
    }

    public LivingEventSnapshot activeEvent() {
        sync();
        return active;
    }

    LivingEventSnapshot activeEventWithoutSync() {
        return active;
    }

    public boolean resolveSupplyRoute(UUID eventId) {
        if (active == null || eventId == null || !active.id().equals(eventId)) return false;
        resolve(active, LivingEventApproach.LOGISTICS, 0);
        return true;
    }

    public List<LivingEventSnapshot> eventHistory() {
        return List.copyOf(history);
    }

    public List<MissionContract> missionBoard(List<MissionContract> base) {
        return LivingEventRules.missionBoard(base, activeEvent());
    }

    public int missionEnemyModifier(MissionContract contract) {
        LivingEventSnapshot current = activeEvent();
        int pressure = current == null ? 0 : regionPressure(current.type());
        int modifier = LivingEventRules.missionEnemyModifier(current, contract, pressure);
        if (modifier > 0 && residentIntel != null) modifier -= residentIntel.enemyReduction(current);
        return Math.min(Math.max(0, plugin.getConfig().getInt("living-world.crisis-enemy-cap", 2)),
                Math.max(0, modifier));
    }

    public void recordMission(MissionContract contract, int participants) {
        LivingEventSnapshot current = activeEvent();
        if (current == null || current.type().contract() != contract) return;
        resolve(current, LivingEventApproach.FIELD, participants);
    }

    public String summary() {
        LivingEventSnapshot current = activeEvent();
        if (current == null) return "目前沒有待處理危機";
        return current.type().display() + "・" + current.type().region().display()
                + "・剩餘 " + remainingDays(current) + " 日";
    }

    public void openBoard(Player player) {
        LivingEventSnapshot current = activeEvent();
        EventHolder holder = new EventHolder(Menu.BOARD, current == null ? null : current.id());
        Inventory inventory = create(holder, "新城動態通報");
        if (current == null) {
            inventory.setItem(13, item(Material.CLOCK, "目前沒有待處理危機", NamedTextColor.GREEN,
                    List.of("傳令人正在整理下一批區域消息。", historySummary())));
        } else {
            LivingEventType type = current.type();
            inventory.setItem(4, item(type.region().icon(), type.display(), NamedTextColor.YELLOW,
                    List.of(type.summary(), "區域：" + type.region().display() + "　脈絡：" + type.arc().display(),
                            "剩餘：" + remainingDays(current) + " 日　區域壓力：" + regionPressure(type) + "/3",
                            crisisScenes == null ? "現場尚未定位" : crisisScenes.sceneSummary(current.id()))));
            inventory.setItem(11, item(Material.IRON_SWORD, "現場應對", NamedTextColor.RED,
                    List.of("任務公告已加入「" + type.contract().display() + "」。",
                            "完成後由參與的一至兩名玩家共同結案。",
                            "危機任務會依區域壓力增加有限敵軍。")));
            inventory.setItem(15, item(Material.CHEST, "物資調度", NamedTextColor.GOLD,
                    List.of("由公共庫存處理，不必進入戰鬥。", "需要：" + costText(eventCost(type)),
                            "點擊後仍需二次確認。")));
            inventory.setItem(20, item(Material.WRITABLE_BOOK, "本輪事件記憶", NamedTextColor.AQUA,
                    List.of(historySummary(), arcSummary(current.cycle()))));
        }
        inventory.setItem(26, item(Material.ARROW, "返回發展總覽", NamedTextColor.GREEN, List.of()));
        player.openInventory(inventory);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMessengerInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || !event.getRightClicked().getPersistentDataContainer().has(actorKey, PersistentDataType.STRING)
                || !"messenger".equals(event.getRightClicked().getPersistentDataContainer()
                .get(actorKey, PersistentDataType.STRING))) return;
        event.setCancelled(true);
        openBoard(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof EventHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0
                || event.getRawSlot() >= top.getSize()) return;
        LivingEventSnapshot current = activeEvent();
        if (holder.menu == Menu.BOARD) {
            if (event.getRawSlot() == 11 && current != null) {
                missionBoardOpener.accept(player);
            } else if (event.getRawSlot() == 15 && current != null) {
                openLogisticsConfirmation(player, current);
            } else if (event.getRawSlot() == 26) {
                development.openHub(player);
            }
        } else if (holder.menu == Menu.CONFIRM) {
            if (event.getRawSlot() == 11) openBoard(player);
            else if (event.getRawSlot() == 15 && current != null && current.id().equals(holder.eventId)) {
                resolveLogistics(player, current);
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> notifyPlayer(event.getPlayer()), 30L);
    }

    public int runSelfTest() {
        LivingEventSnapshot current = activeEvent();
        flush();
        int checks = LivingEventType.values().length == 12 ? 1 : 0;
        if (LivingEventArc.values().length == 4) checks++;
        if (current != null && current.state() == LivingEventState.ACTIVE) checks++;
        if (current != null && missionBoard(campaign.board()).contains(current.type().contract())) checks++;
        if (java.util.Arrays.stream(LivingEventType.values()).allMatch(type -> type.logisticsCost().size() == 2
                && type.logisticsCost().values().stream().allMatch(amount -> amount > 0))) checks++;
        if (current != null && repository.active().map(event -> event.id().equals(current.id())).orElse(false)) checks++;
        Entity messenger = messengerId == null ? null : Bukkit.getEntity(messengerId);
        if (messenger != null && messenger.isValid() && "messenger".equals(messenger.getPersistentDataContainer()
                .get(actorKey, PersistentDataType.STRING))) checks++;
        Entity billboard = billboardId == null ? null : Bukkit.getEntity(billboardId);
        if (billboard instanceof TextDisplay && billboard.isValid() && "billboard".equals(
                billboard.getPersistentDataContainer().get(actorKey, PersistentDataType.STRING))) checks++;
        return checks;
    }

    public void flush() {
        List<LivingEventSnapshot> snapshots = List.copyOf(history);
        database.submit(() -> snapshots.forEach(repository::save)).join();
    }

    private void sync() {
        CampaignSnapshot state = campaign.state();
        long now = System.currentTimeMillis();
        if (active != null && active.state() == LivingEventState.ACTIVE
                && state.epochDay() >= active.expiresEpochDay()) {
            LivingEventSnapshot expired = active.expire(now);
            replace(expired);
            saveAsync(expired);
            campaign.adjustMetric(expired.type().metric(), -expiryPenalty());
            Bukkit.broadcast(EvilIslandPlugin.message("區域危機「" + expired.type().display()
                    + "」未及時處理，" + expired.type().metric().display() + "受到損失。", NamedTextColor.RED));
            if (crisisScenes != null) crisisScenes.finish(expired);
            if (supplyRoutes != null) supplyRoutes.close(expired);
            if (regionControl != null) regionControl.eventFinished(expired);
            active = null;
        }
        if (active != null) return;
        LivingEventSnapshot latest = history.stream().max(Comparator.comparingLong(
                LivingEventSnapshot::createdAt)).orElse(null);
        if (latest != null && state.epochDay() < latest.expiresEpochDay()) return;
        List<LivingEventType> recent = history.stream().sorted(Comparator.comparingLong(
                LivingEventSnapshot::createdAt).reversed()).limit(recentWindow()).map(LivingEventSnapshot::type).toList();
        LivingEventType type = LivingEventRules.select(state.cycle(), state.week(), state.day(), recent);
        active = new LivingEventSnapshot(UUID.randomUUID(), type, LivingEventState.ACTIVE, LivingEventApproach.NONE,
                state.cycle(), state.week(), state.day(), state.epochDay(), state.epochDay() + durationDays(),
                0, now, 0L, now);
        history.add(active);
        saveAsync(active);
        if (crisisScenes != null) crisisScenes.activate(active);
        if (supplyRoutes != null) supplyRoutes.open(active);
        if (regionControl != null) regionControl.eventOpened(active);
        database.submit(() -> repository.prune(retention()));
        Bukkit.broadcast(EvilIslandPlugin.message("新城收到區域通報：「" + type.display()
                + "」。可向傳令人或發展總覽查看。", NamedTextColor.YELLOW));
        refreshScene();
    }

    private void resolveLogistics(Player player, LivingEventSnapshot current) {
        Map<WorldResource, Integer> cost = eventCost(current.type());
        if (!development.spendResources(cost)) {
            player.sendMessage(EvilIslandPlugin.message("公共物資不足，需要「" + costText(cost) + "」。",
                    NamedTextColor.RED));
            openBoard(player);
            return;
        }
        resolve(current, LivingEventApproach.LOGISTICS, 0);
    }

    private void resolve(LivingEventSnapshot current, LivingEventApproach approach, int participants) {
        if (active == null || !active.id().equals(current.id()) || active.state() != LivingEventState.ACTIVE) return;
        LivingEventSnapshot resolved = active.resolve(approach, participants, System.currentTimeMillis());
        replace(resolved);
        saveAsync(resolved);
        campaign.adjustMetric(resolved.type().metric(), resolutionReward());
        if (crisisScenes != null) crisisScenes.finish(resolved);
        if (supplyRoutes != null) supplyRoutes.close(resolved);
        if (regionControl != null) regionControl.eventFinished(resolved);
        active = null;
        Bukkit.broadcast(EvilIslandPlugin.message("區域危機「" + resolved.type().display() + "」已透過「"
                + approach.display() + "」處理。", NamedTextColor.GREEN));
        refreshScene();
    }

    private void openLogisticsConfirmation(Player player, LivingEventSnapshot current) {
        EventHolder holder = new EventHolder(Menu.CONFIRM, current.id());
        Inventory inventory = create(holder, "確認危機物資調度");
        inventory.setItem(4, item(current.type().region().icon(), current.type().display(), NamedTextColor.YELLOW,
                List.of(current.type().summary(), "需要：" + costText(eventCost(current.type())))));
        inventory.setItem(11, item(Material.ARROW, "返回", NamedTextColor.GREEN, List.of("不消耗任何物資。")));
        inventory.setItem(15, item(Material.LIME_CONCRETE, "確認調度", NamedTextColor.GOLD,
                List.of("立即從全服公共庫存扣除物資。", "事件處理後不可重選方式。")));
        player.openInventory(inventory);
    }

    private void notifyPlayer(Player player) {
        LivingEventSnapshot current = activeEvent();
        if (current == null || !player.isOnline()) return;
        String noticed = player.getPersistentDataContainer().get(noticeKey, PersistentDataType.STRING);
        if (current.id().toString().equals(noticed)) return;
        player.getPersistentDataContainer().set(noticeKey, PersistentDataType.STRING, current.id().toString());
        player.sendMessage(EvilIslandPlugin.message("新城通報：「" + current.type().display() + "」仍待處理；剩餘 "
                + remainingDays(current) + " 日。", NamedTextColor.YELLOW));
    }

    private void refreshScene() {
        Location center = daoFields.cityCenter();
        if (center == null || center.getWorld() == null) return;
        Villager messenger = null;
        TextDisplay billboard = null;
        for (Entity entity : center.getWorld().getEntities()) {
            String role = entity.getPersistentDataContainer().get(actorKey, PersistentDataType.STRING);
            if ("messenger".equals(role) && entity instanceof Villager candidate) {
                if (messenger == null) messenger = candidate;
                else candidate.remove();
            } else if ("billboard".equals(role) && entity instanceof TextDisplay candidate) {
                if (billboard == null) billboard = candidate;
                else candidate.remove();
            }
        }
        Location position = messengerLocation(center);
        if (messenger == null) {
            messenger = center.getWorld().spawn(position, Villager.class);
            messenger.setAdult();
            messenger.setProfession(Villager.Profession.CARTOGRAPHER);
            messenger.setAI(false);
            messenger.setInvulnerable(true);
            messenger.setCollidable(false);
            messenger.setSilent(true);
            messenger.setPersistent(true);
            messenger.setRemoveWhenFarAway(false);
            messenger.getPersistentDataContainer().set(actorKey, PersistentDataType.STRING, "messenger");
        } else {
            messenger.teleport(position);
        }
        messenger.customName(Component.text(active == null ? "新城傳令人" : "新城傳令人・" + active.type().display(),
                active == null ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        messenger.setCustomNameVisible(true);
        messengerId = messenger.getUniqueId();
        Location textPosition = position.clone().add(0, 2.6, 0);
        if (billboard == null) {
            billboard = center.getWorld().spawn(textPosition, TextDisplay.class);
            billboard.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            billboard.setPersistent(true);
            billboard.getPersistentDataContainer().set(actorKey, PersistentDataType.STRING, "billboard");
        } else {
            billboard.teleport(textPosition);
        }
        billboard.text(Component.text(active == null ? "目前無緊急通報" : summary(),
                active == null ? NamedTextColor.GREEN : NamedTextColor.GOLD));
        billboardId = billboard.getUniqueId();
    }

    private void refreshMessengerLocation() {
        Location center = daoFields.cityCenter();
        Entity messenger = messengerId == null ? null : Bukkit.getEntity(messengerId);
        Entity billboard = billboardId == null ? null : Bukkit.getEntity(billboardId);
        if (center == null || messenger == null || billboard == null || !messenger.isValid() || !billboard.isValid()) {
            refreshScene();
            return;
        }
        Location target = messengerLocation(center);
        if (messenger.getLocation().distanceSquared(target) > 1.0) messenger.teleport(target);
        billboard.teleport(target.clone().add(0, 2.6, 0));
    }

    private Location messengerLocation(Location center) {
        World world = center.getWorld();
        int segment = world == null ? 0 : (int) (world.getTime() / 6000L) % 4;
        int[][] offsets = {{6, 5}, {-5, 6}, {-6, -4}, {5, -5}};
        Location position = center.clone().add(offsets[segment][0], 0, offsets[segment][1]);
        int y = world.getHighestBlockYAt(position.getBlockX(), position.getBlockZ()) + 1;
        return new Location(world, position.getBlockX() + 0.5, y, position.getBlockZ() + 0.5);
    }

    private int regionPressure(LivingEventType type) {
        return LivingEventRules.regionPressure(history, type.region(), campaign.state().cycle());
    }

    private int remainingDays(LivingEventSnapshot event) {
        return (int) Math.max(1L, event.expiresEpochDay() - campaign.state().epochDay());
    }

    private String historySummary() {
        long resolved = history.stream().filter(event -> event.cycle() == campaign.state().cycle()
                && event.state() == LivingEventState.RESOLVED).count();
        long expired = history.stream().filter(event -> event.cycle() == campaign.state().cycle()
                && event.state() == LivingEventState.EXPIRED).count();
        return "本輪已處理 " + resolved + " 件，惡化 " + expired + " 件";
    }

    private String arcSummary(int cycle) {
        return java.util.Arrays.stream(LivingEventArc.values()).map(arc -> arc.display() + " "
                + LivingEventRules.arcProgress(history, arc, cycle)).reduce((left, right) -> left + "　" + right)
                .orElse("");
    }

    private String costText(Map<WorldResource, Integer> cost) {
        return cost.entrySet().stream().map(entry -> entry.getKey().display() + " " + entry.getValue())
                .reduce((left, right) -> left + "、" + right).orElse("無");
    }

    private Map<WorldResource, Integer> eventCost(LivingEventType type) {
        double multiplier = Math.max(0.25, plugin.getConfig().getDouble(
                "living-world.logistics-cost-multiplier", 1.0));
        Map<WorldResource, Integer> base = type.logisticsCost();
        Map<WorldResource, Integer> result = new java.util.EnumMap<>(WorldResource.class);
        base.forEach((resource, amount) -> result.put(resource,
                Math.max(1, (int) Math.ceil(amount * multiplier))));
        return Map.copyOf(result);
    }

    private void replace(LivingEventSnapshot updated) {
        history.removeIf(event -> event.id().equals(updated.id()));
        history.add(updated);
    }

    private void saveAsync(LivingEventSnapshot snapshot) {
        database.submit(() -> repository.save(snapshot)).exceptionally(exception -> {
            plugin.getLogger().log(Level.SEVERE, "Cannot save living event " + snapshot.id(), exception);
            return null;
        });
    }

    private int durationDays() {
        return Math.max(2, plugin.getConfig().getInt("living-world.event-duration-days", 3));
    }

    private int recentWindow() {
        return Math.max(1, plugin.getConfig().getInt("living-world.recent-event-window", 6));
    }

    private int retention() {
        return Math.max(24, plugin.getConfig().getInt("living-world.history-retention", 96));
    }

    private int resolutionReward() {
        return Math.max(0, plugin.getConfig().getInt("living-world.resolution-metric-reward", 2));
    }

    private int expiryPenalty() {
        return Math.max(0, plugin.getConfig().getInt("living-world.expiry-metric-penalty", 4));
    }

    private Inventory create(EventHolder holder, String title) {
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text(title));
        holder.inventory = inventory;
        return inventory;
    }

    private ItemStack item(Material material, String name, NamedTextColor color, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, color));
        meta.lore(lore.stream().map(line -> Component.text(line, NamedTextColor.GRAY)).toList());
        stack.setItemMeta(meta);
        return stack;
    }

    private enum Menu { BOARD, CONFIRM }

    private static final class EventHolder implements InventoryHolder {
        private final Menu menu;
        private final UUID eventId;
        private Inventory inventory;

        private EventHolder(Menu menu, UUID eventId) {
            this.menu = menu;
            this.eventId = eventId;
        }

        @Override
        public Inventory getInventory() { return inventory; }
    }
}
