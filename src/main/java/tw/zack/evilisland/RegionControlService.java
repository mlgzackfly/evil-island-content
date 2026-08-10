package tw.zack.evilisland;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
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
import org.bukkit.plugin.Plugin;
import tw.zack.evilisland.model.CampaignMetric;
import tw.zack.evilisland.model.ExpeditionCampBlockSnapshot;
import tw.zack.evilisland.model.ExpeditionOutcome;
import tw.zack.evilisland.model.ExpeditionRules;
import tw.zack.evilisland.model.ExpeditionStoryChoice;
import tw.zack.evilisland.model.ExplorationSite;
import tw.zack.evilisland.model.LivingEventApproach;
import tw.zack.evilisland.model.LivingEventSnapshot;
import tw.zack.evilisland.model.LivingEventState;
import tw.zack.evilisland.model.LivingEventType;
import tw.zack.evilisland.model.MissionContract;
import tw.zack.evilisland.model.RegionControlRules;
import tw.zack.evilisland.model.RegionControlSnapshot;
import tw.zack.evilisland.model.RegionState;
import tw.zack.evilisland.model.WorldResource;
import tw.zack.evilisland.persistence.RegionControlRepository;
import tw.zack.evilisland.world.WorldAtlasService;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.lang.reflect.Method;
import java.util.logging.Level;

public final class RegionControlService implements Listener {
    private static final Set<Material> NATURAL_GROUND = Set.of(Material.GRASS_BLOCK, Material.DIRT,
            Material.COARSE_DIRT, Material.PODZOL, Material.STONE, Material.ANDESITE, Material.DIORITE,
            Material.GRANITE, Material.SAND, Material.RED_SAND, Material.GRAVEL, Material.MOSS_BLOCK,
            Material.DEEPSLATE, Material.TUFF, Material.CALCITE);
    private static final Set<Material> REPLACEABLE = Set.of(Material.AIR, Material.CAVE_AIR, Material.VOID_AIR,
            Material.SHORT_GRASS, Material.TALL_GRASS, Material.FERN, Material.LARGE_FERN, Material.SNOW,
            Material.DEAD_BUSH, Material.DANDELION, Material.POPPY, Material.BLUE_ORCHID, Material.ALLIUM,
            Material.AZURE_BLUET, Material.OXEYE_DAISY, Material.CORNFLOWER, Material.LILY_OF_THE_VALLEY);

    private final EvilIslandPlugin plugin;
    private final RegionControlRepository repository;
    private final CampaignService campaign;
    private final DevelopmentService development;
    private final PlayerProfileService profiles;
    private final DaoFieldService daoFields;
    private final WorldAtlasService atlas;
    private final NamespacedKey actorKey;
    private final NamespacedKey restKey;
    private final DynmapCampMarkers dynmap;
    private final Map<ExplorationSite, RegionControlSnapshot> regions = new EnumMap<>(ExplorationSite.class);
    private final Map<ExplorationSite, UUID> quartermasters = new EnumMap<>(ExplorationSite.class);
    private final Map<ExplorationSite, UUID> signs = new EnumMap<>(ExplorationSite.class);
    private Consumer<Player> missionBoardOpener = ignored -> { };
    private BiConsumer<Player, ExplorationSite> expeditionBoardOpener = (ignored, site) -> { };
    private BiConsumer<Player, ExplorationSite> campVisitListener = (ignored, site) -> { };
    private Function<ExplorationSite, ExpeditionStoryChoice> storyDirectionResolver = ignored -> null;

    public RegionControlService(EvilIslandPlugin plugin, RegionControlRepository repository,
                                CampaignService campaign, DevelopmentService development,
                                PlayerProfileService profiles, DaoFieldService daoFields, WorldAtlasService atlas) {
        this.plugin = plugin;
        this.repository = repository;
        this.campaign = campaign;
        this.development = development;
        this.profiles = profiles;
        this.daoFields = daoFields;
        this.atlas = atlas;
        this.actorKey = new NamespacedKey(plugin, "expedition_camp_actor");
        this.restKey = new NamespacedKey(plugin, "expedition_camp_rest");
        this.dynmap = new DynmapCampMarkers(plugin);
    }

    public void setMissionBoardOpener(Consumer<Player> opener) {
        missionBoardOpener = opener == null ? ignored -> { } : opener;
    }

    public void setExpeditionBoardOpener(BiConsumer<Player, ExplorationSite> opener) {
        expeditionBoardOpener = opener == null ? (ignored, site) -> { } : opener;
    }

    public void setCampVisitListener(BiConsumer<Player, ExplorationSite> listener) {
        campVisitListener = listener == null ? (ignored, site) -> { } : listener;
    }

    public void setStoryDirectionResolver(Function<ExplorationSite, ExpeditionStoryChoice> resolver) {
        storyDirectionResolver = resolver == null ? ignored -> null : resolver;
    }

    public void load() {
        regions.clear();
        regions.putAll(repository.loadAll());
        long now = System.currentTimeMillis();
        for (ExplorationSite site : ExplorationSite.values()) {
            RegionControlSnapshot region = regions.computeIfAbsent(site,
                    ignored -> RegionControlSnapshot.initial(site, now));
            repository.save(region);
            ensureCamp(region);
        }
        plugin.getLogger().info("Region control loaded for " + regions.size() + " expedition areas.");
    }

    public void reconcile(List<LivingEventSnapshot> history) {
        for (LivingEventSnapshot event : history) {
            applyEvent(event, LivingEventState.ACTIVE, LivingEventApproach.NONE, false);
            if (event.state() != LivingEventState.ACTIVE) {
                applyEvent(event, event.state(), event.approach(), false);
            }
        }
        refreshAll();
    }

    public void tick() {
        for (ExplorationSite site : ExplorationSite.values()) refreshActors(site);
    }

    public RegionControlSnapshot region(ExplorationSite site) {
        return regions.get(site);
    }

    public Location campLocation(ExplorationSite site) {
        RegionControlSnapshot region = regions.get(site);
        if (region == null || !region.placed()) return siteLocation(site);
        World world = Bukkit.getWorld(region.world());
        return world == null ? siteLocation(site) : new Location(world, region.x() + 0.5, region.y() + 1.0,
                region.z() + 0.5);
    }

    public boolean isProtected(Location location) {
        if (location == null || location.getWorld() == null) return false;
        double radius = Math.max(8.0, plugin.getConfig().getDouble("region-control.camp.protection-radius", 12.0));
        double squared = radius * radius;
        for (RegionControlSnapshot region : regions.values()) {
            if (!region.placed() || !region.world().equals(location.getWorld().getName())) continue;
            double dx = location.getX() - region.x();
            double dz = location.getZ() - region.z();
            if (dx * dx + dz * dz <= squared) return true;
        }
        return false;
    }

    public void eventOpened(LivingEventSnapshot event) {
        applyEvent(event, LivingEventState.ACTIVE, LivingEventApproach.NONE, true);
    }

    public void eventFinished(LivingEventSnapshot event) {
        applyEvent(event, event.state(), event.approach(), true);
    }

    public void recordVerifiedIntel(UUID eventId, ExplorationSite site) {
        apply("intel:" + eventId, site, "居民情報核實", 4, true);
    }

    public void recordSupplyRoute(UUID eventId, ExplorationSite site) {
        apply("supply-route:" + eventId, site, "前線補給接力", 3, true);
    }

    public void recordMission(MissionContract contract, int participants) {
        ExplorationSite site = missionRegion(contract);
        String effectId = "mission:" + campaign.state().cycle() + ":" + campaign.state().epochDay()
                + ":" + site.id();
        apply(effectId, site, "區域任務", RegionControlRules.missionDelta(participants), true);
    }

    public void recordExpedition(UUID expeditionId, ExpeditionOutcome outcome, int participants) {
        recordExpedition(expeditionId, ExplorationSite.EASTERN_ROUTE, outcome, participants);
    }

    public void recordExpedition(UUID expeditionId, ExplorationSite site, ExpeditionOutcome outcome,
                                 int participants) {
        apply("deep-expedition:" + expeditionId, site,
                site.display() + "深入遠征：" + outcome.display(),
                ExpeditionRules.regionDelta(outcome, participants), true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCampInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        ExplorationSite site = ExplorationSite.parse(event.getRightClicked().getPersistentDataContainer()
                .get(actorKey, PersistentDataType.STRING));
        if (site == null) return;
        event.setCancelled(true);
        campVisitListener.accept(event.getPlayer(), site);
        openCamp(event.getPlayer(), site);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof CampHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0
                || event.getRawSlot() >= top.getSize()) return;
        switch (event.getRawSlot()) {
            case 20 -> {
                expeditionBoardOpener.accept(player, holder.site);
            }
            case 11 -> rest(player, holder.site);
            case 13 -> resupply(player, holder.site);
            case 15 -> upgrade(player, holder.site);
            case 22 -> missionBoardOpener.accept(player);
            case 26 -> returnToCity(player, holder.site);
            default -> { }
        }
    }

    public int runSelfTest() {
        int checks = 0;
        if (regions.size() == ExplorationSite.values().length) checks++;
        if (RegionControlRules.stateAfter(RegionState.TENSE, 20) == RegionState.LOST
                && RegionControlRules.stateAfter(RegionState.LOST, 45) == RegionState.RECOVERING
                && RegionControlRules.stateAfter(RegionState.RECOVERING, 70) == RegionState.STABLE) checks++;
        if (RegionControlRules.eventDelta(LivingEventState.EXPIRED, LivingEventApproach.NONE) < 0
                && RegionControlRules.eventDelta(LivingEventState.RESOLVED, LivingEventApproach.FIELD)
                > RegionControlRules.eventDelta(LivingEventState.RESOLVED, LivingEventApproach.LOGISTICS)) checks++;
        if (regions.values().stream().allMatch(region -> region.campLevel() >= 1
                && region.supplies() <= RegionControlRules.MAX_SUPPLIES)) checks++;
        if (regions.values().stream().allMatch(region -> !region.placed()
                || !repository.loadBlocks(region.site()).isEmpty())) checks++;
        if (quartermasters.size() == ExplorationSite.values().length
                && signs.size() == ExplorationSite.values().length
                && new HashSet<>(quartermasters.values()).size() == ExplorationSite.values().length
                && new HashSet<>(signs.values()).size() == ExplorationSite.values().length) {
            checks++;
        }
        return checks;
    }

    public void flush() {
        for (RegionControlSnapshot region : regions.values()) repository.save(region);
    }

    private void applyEvent(LivingEventSnapshot event, LivingEventState state, LivingEventApproach approach,
                            boolean announce) {
        String phase = state == LivingEventState.ACTIVE ? "open" : "result";
        int delta = RegionControlRules.eventDelta(state, approach);
        String source = state == LivingEventState.ACTIVE ? "區域危機出現"
                : state == LivingEventState.EXPIRED ? "區域危機惡化" : approach.display() + "結案";
        apply("event:" + event.id() + ":" + phase, event.type().region(), source, delta, announce);
    }

    private synchronized void apply(String effectId, ExplorationSite site, String source, int delta,
                                    boolean announce) {
        if (delta == 0) return;
        RegionControlSnapshot previous = regions.get(site);
        if (previous == null) return;
        RegionControlSnapshot changed = previous.adjust(delta, System.currentTimeMillis());
        if (!repository.applyEffect(effectId, source, delta, changed)) return;
        regions.put(site, changed);
        transitionCamp(previous, changed);
        if (announce && previous.state() != changed.state()) {
            Bukkit.broadcast(EvilIslandPlugin.message(site.display() + "由「" + previous.state().display()
                    + "」轉為「" + changed.state().display() + "」。", changed.state() == RegionState.LOST
                    ? NamedTextColor.RED : NamedTextColor.YELLOW));
        }
        refreshActors(site);
    }

    private void openCamp(Player player, ExplorationSite site) {
        RegionControlSnapshot region = regions.get(site);
        if (region == null) return;
        CampHolder holder = new CampHolder(site);
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text(site.display() + "遠征營地"));
        holder.inventory = inventory;
        ExpeditionStoryChoice direction = storyDirectionResolver.apply(site);
        inventory.setItem(4, item(site.icon(), site.display(), color(region.state()), List.of(
                "區域狀態：" + region.state().display() + "　穩定度：" + region.stability() + "/100",
                "營地階段：" + region.campLevel() + "/2　補給：" + region.supplies() + "/"
                        + RegionControlRules.campCapacity(region.campLevel()),
                direction == null ? "區域方向：尚未形成" : "區域方向：" + direction.display())));
        inventory.setItem(11, item(Material.HONEY_BOTTLE, "前線整備", NamedTextColor.GREEN,
                List.of("消耗 1 份營地補給，恢復生命、飽食與少量炁息。",
                        direction == ExpeditionStoryChoice.SECURE ? "內側警戒線：額外恢復生命。"
                                : direction == ExpeditionStoryChoice.CONNECT ? "外側回訊線：額外恢復炁息。" : "",
                        region.state() == RegionState.LOST ? "區域失守，整備服務已暫停。" : "每名玩家十五分鐘可使用一次。")));
        inventory.setItem(13, item(Material.BARREL, "補充營地", NamedTextColor.GOLD,
                List.of("公共庫存：城防糧秣 2、工事木料 1。", "增加 3 份營地補給，不超過上限。")));
        inventory.setItem(15, item(Material.LANTERN, region.campLevel() >= 2 ? "營地已完成擴建" : "擴建營地",
                NamedTextColor.AQUA, region.campLevel() >= 2 ? List.of("目前已提供完整前線設施。")
                        : List.of("公共庫存：工事木料 3、牆材 2。", "擴建只增加後勤容量，不提升永久戰力。")));
        inventory.setItem(22, item(Material.WRITABLE_BOOK,
                region.state() == RegionState.LOST ? "收復任務公告" : "區域任務公告", NamedTextColor.YELLOW,
                List.of("一人出勤會依任務配置 NPC 支援，兩人可分工行動。",
                        "每日完成同區任務可有限改善區域穩定度。")));
        inventory.setItem(20, item(Material.RECOVERY_COMPASS,
                tw.zack.evilisland.expedition.ExpeditionScenarioRegistry.standard()
                        .forSite(site).boardTitle(), NamedTextColor.RED,
                List.of("一至兩人進行區域遠征；各區有不同目標、威脅與撤離條件。",
                        "單人由無跡接受現場命令，雙人必須分頭同步執行目標。")));
        inventory.setItem(26, item(Material.COMPASS, "返回新城", NamedTextColor.GREEN,
                List.of(region.state() == RegionState.LOST ? "區域失守，撤離路線仍保持開放。" : "由營地後勤安排返城。")));
        player.openInventory(inventory);
    }

    private void rest(Player player, ExplorationSite site) {
        RegionControlSnapshot region = regions.get(site);
        if (!RegionControlRules.campOperational(region)) {
            player.sendMessage(EvilIslandPlugin.message(region != null && region.state() == RegionState.LOST
                    ? "區域失守，營地只能提供撤離與收復任務。" : "營地補給不足。", NamedTextColor.RED));
            openCamp(player, site);
            return;
        }
        long now = System.currentTimeMillis();
        Long last = player.getPersistentDataContainer().get(restKey, PersistentDataType.LONG);
        long cooldown = Math.max(60_000L, plugin.getConfig().getLong("region-control.camp.rest-cooldown-ms", 900_000L));
        if (last != null && now - last < cooldown) {
            player.sendMessage(EvilIslandPlugin.message("你剛完成整備，需等待 "
                    + Math.max(1L, (cooldown - (now - last) + 59_999L) / 60_000L) + " 分鐘。"));
            return;
        }
        updateCamp(region.withCamp(region.campLevel(), region.supplies() - 1, now));
        double maximum = player.getAttribute(Attribute.GENERIC_MAX_HEALTH) == null ? 20.0
                : player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        ExpeditionStoryChoice direction = storyDirectionResolver.apply(site);
        player.setHealth(Math.min(maximum, player.getHealth()
                + (direction == ExpeditionStoryChoice.SECURE ? 12.0 : 8.0)));
        player.setFoodLevel(Math.min(20, player.getFoodLevel() + 6));
        if (profiles.isEnlisted(player)) profiles.addQi(player,
                direction == ExpeditionStoryChoice.CONNECT ? 18 : 12);
        player.getPersistentDataContainer().set(restKey, PersistentDataType.LONG, now);
        player.sendMessage(EvilIslandPlugin.message("前線整備完成，營地補給剩餘 "
                + regions.get(site).supplies() + " 份。", NamedTextColor.GREEN));
        openCamp(player, site);
    }

    private void resupply(Player player, ExplorationSite site) {
        RegionControlSnapshot region = regions.get(site);
        if (region == null || region.state() == RegionState.LOST) {
            player.sendMessage(EvilIslandPlugin.message("失守區域無法接收一般補給，必須先完成收復行動。",
                    NamedTextColor.RED));
            return;
        }
        int capacity = RegionControlRules.campCapacity(region.campLevel());
        if (region.supplies() >= capacity) {
            player.sendMessage(EvilIslandPlugin.message("營地補給已達上限。"));
            return;
        }
        Map<WorldResource, Integer> cost = Map.of(WorldResource.PROVISIONS, 2, WorldResource.TIMBER, 1);
        if (!development.spendResources(cost)) {
            player.sendMessage(EvilIslandPlugin.message("公共庫存缺少城防糧秣 2、工事木料 1。",
                    NamedTextColor.RED));
            return;
        }
        updateCamp(region.withCamp(region.campLevel(), Math.min(capacity, region.supplies() + 3),
                System.currentTimeMillis()));
        player.sendMessage(EvilIslandPlugin.message("補給已入營，現有 " + regions.get(site).supplies() + " 份。",
                NamedTextColor.GREEN));
        openCamp(player, site);
    }

    private void upgrade(Player player, ExplorationSite site) {
        RegionControlSnapshot region = regions.get(site);
        if (region == null || region.campLevel() >= 2) return;
        if (region.state() == RegionState.LOST) {
            player.sendMessage(EvilIslandPlugin.message("失守期間無法擴建營地。", NamedTextColor.RED));
            return;
        }
        Map<WorldResource, Integer> cost = Map.of(WorldResource.TIMBER, 3, WorldResource.MASONRY, 2);
        if (!development.spendResources(cost)) {
            player.sendMessage(EvilIslandPlugin.message("公共庫存缺少工事木料 3、牆材 2。", NamedTextColor.RED));
            return;
        }
        RegionControlSnapshot changed = region.withCamp(2, Math.max(region.supplies(), 4),
                System.currentTimeMillis());
        updateCamp(changed);
        transitionCamp(region, changed);
        Bukkit.broadcast(EvilIslandPlugin.message(site.display() + "遠征營地已完成擴建。", NamedTextColor.GREEN));
        openCamp(player, site);
    }

    private void returnToCity(Player player, ExplorationSite site) {
        Location city = daoFields.cityCenter();
        if (city == null || city.getWorld() == null) return;
        player.closeInventory();
        player.teleportAsync(ground(city));
        player.sendMessage(EvilIslandPlugin.message("營地已安排撤回新城的路線。", NamedTextColor.GREEN));
    }

    private void updateCamp(RegionControlSnapshot changed) {
        regions.put(changed.site(), changed);
        repository.save(changed);
        refreshActors(changed.site());
    }

    private void ensureCamp(RegionControlSnapshot region) {
        Location target = siteLocation(region.site());
        if (target == null || target.getWorld() == null) return;
        target.getWorld().getChunkAtAsync(target).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
            RegionControlSnapshot current = regions.get(region.site());
            if (current == null) return;
            if (!current.placed() || repository.loadBlocks(current.site()).isEmpty()) placeCamp(current, target);
            else verifyCamp(current);
        }));
    }

    private void placeCamp(RegionControlSnapshot region, Location target) {
        List<CampBlock> base = blueprint();
        Location anchor = findSafeAnchor(region.site(), target, base);
        if (anchor == null || anchor.getWorld() == null) {
            plugin.getLogger().warning("No safe plot found for expedition camp " + region.site().id());
            return;
        }
        List<CampBlock> plan = withFoundations(anchor, base);
        RegionControlSnapshot placed = region.withAnchor(anchor.getWorld().getName(), anchor.getBlockX(),
                anchor.getBlockY(), anchor.getBlockZ(), System.currentTimeMillis());
        List<ExpeditionCampBlockSnapshot> blocks = new ArrayList<>();
        for (CampBlock planned : plan) {
            Block block = anchor.getWorld().getBlockAt(placed.x() + planned.dx, placed.y() + planned.dy,
                    placed.z() + planned.dz);
            String desired = data(placed.state() == RegionState.LOST ? planned.lost
                    : placed.campLevel() >= 2 ? planned.levelTwo : planned.levelOne);
            blocks.add(new ExpeditionCampBlockSnapshot(placed.site(), placed.world(), block.getX(), block.getY(),
                    block.getZ(), block.getBlockData().getAsString(), data(planned.levelOne), data(planned.levelTwo),
                    data(planned.lost), desired));
        }
        repository.save(placed);
        repository.saveBlocks(blocks);
        regions.put(placed.site(), placed);
        for (ExpeditionCampBlockSnapshot block : blocks) {
            anchor.getWorld().getBlockAt(block.x(), block.y(), block.z())
                    .setBlockData(Bukkit.createBlockData(block.placedData()), false);
        }
        refreshActors(placed.site());
        plugin.getLogger().info("Placed " + placed.site().id() + " expedition camp with " + blocks.size()
                + " owned blocks at " + placed.x() + "," + placed.y() + "," + placed.z() + ".");
    }

    private void verifyCamp(RegionControlSnapshot region) {
        World world = Bukkit.getWorld(region.world());
        if (world == null) return;
        world.getChunkAt(region.x() >> 4, region.z() >> 4).load();
        boolean conflict = false;
        List<ExpeditionCampBlockSnapshot> updated = new ArrayList<>();
        for (ExpeditionCampBlockSnapshot snapshot : repository.loadBlocks(region.site())) {
            Block block = world.getBlockAt(snapshot.x(), snapshot.y(), snapshot.z());
            String current = block.getBlockData().getAsString();
            String desired = snapshot.desired(region);
            if (current.equals(snapshot.placedData()) || current.equals(snapshot.originalData())) {
                if (!current.equals(desired)) block.setBlockData(Bukkit.createBlockData(desired), false);
                updated.add(snapshot.withPlacedData(desired));
            } else if (current.equals(desired)) {
                updated.add(snapshot.withPlacedData(desired));
            } else {
                conflict = true;
                updated.add(snapshot);
            }
        }
        repository.saveBlocks(updated);
        if (conflict) plugin.getLogger().warning("Expedition camp " + region.site().id()
                + " has externally modified blocks; conflicting positions were preserved.");
        refreshActors(region.site());
    }

    private void transitionCamp(RegionControlSnapshot previous, RegionControlSnapshot changed) {
        if (!changed.placed() || (previous.state() == changed.state()
                && previous.campLevel() == changed.campLevel())) return;
        verifyCamp(changed);
    }

    private void refreshAll() {
        for (ExplorationSite site : ExplorationSite.values()) {
            RegionControlSnapshot region = regions.get(site);
            if (region != null && region.placed()) verifyCamp(region);
        }
    }

    private void refreshActors(ExplorationSite site) {
        RegionControlSnapshot region = regions.get(site);
        Location camp = campLocation(site);
        if (region == null || !region.placed() || camp == null || camp.getWorld() == null) return;
        if (!camp.getWorld().isChunkLoaded(region.x() >> 4, region.z() >> 4)) return;
        Villager keeper = null;
        TextDisplay sign = null;
        for (Entity entity : camp.getWorld().getNearbyEntities(camp, 16, 8, 16)) {
            ExplorationSite marked = ExplorationSite.parse(entity.getPersistentDataContainer()
                    .get(actorKey, PersistentDataType.STRING));
            if (marked != site) continue;
            if (entity instanceof Villager candidate) {
                if (keeper == null) keeper = candidate;
                else candidate.remove();
            } else if (entity instanceof TextDisplay candidate) {
                if (sign == null) sign = candidate;
                else candidate.remove();
            }
        }
        Location keeperPosition = camp.clone().add(1.5, 0, -1.5);
        if (keeper == null) keeper = camp.getWorld().spawn(keeperPosition, Villager.class);
        else keeper.teleport(keeperPosition);
        keeper.setAdult();
        keeper.setProfession(Villager.Profession.LEATHERWORKER);
        keeper.setAI(false);
        keeper.setInvulnerable(true);
        keeper.setCollidable(false);
        keeper.setSilent(true);
        keeper.setPersistent(true);
        keeper.setRemoveWhenFarAway(false);
        keeper.getPersistentDataContainer().set(actorKey, PersistentDataType.STRING, site.id());
        keeper.customName(Component.text(site.display() + "營地管事", color(region.state())));
        keeper.setCustomNameVisible(true);
        quartermasters.put(site, keeper.getUniqueId());
        Location signPosition = camp.clone().add(0, 3.2, 0);
        if (sign == null) sign = camp.getWorld().spawn(signPosition, TextDisplay.class);
        else sign.teleport(signPosition);
        sign.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
        sign.setPersistent(true);
        sign.getPersistentDataContainer().set(actorKey, PersistentDataType.STRING, site.id());
        sign.text(Component.text(region.state().display() + "　" + region.stability() + "/100\n營地補給 "
                + region.supplies() + "/" + RegionControlRules.campCapacity(region.campLevel()),
                color(region.state())));
        signs.put(site, sign.getUniqueId());
        dynmap.upsert(region);
    }

    private Location findSafeAnchor(ExplorationSite site, Location target, List<CampBlock> plan) {
        int step = Math.max(10, plugin.getConfig().getInt("region-control.camp.search-step", 14));
        int rings = Math.max(1, plugin.getConfig().getInt("region-control.camp.search-rings", 8));
        for (int ring = 0; ring <= rings; ring++) {
            for (int x = -ring; x <= ring; x++) {
                for (int z = -ring; z <= ring; z++) {
                    if (ring > 0 && Math.abs(x) != ring && Math.abs(z) != ring) continue;
                    int anchorX = target.getBlockX() + x * step;
                    int anchorZ = target.getBlockZ() + z * step;
                    Integer ground = safePlot(site, target.getWorld(), anchorX, anchorZ, plan);
                    if (ground != null) return new Location(target.getWorld(), anchorX, ground + 1, anchorZ);
                }
            }
        }
        return null;
    }

    private Integer safePlot(ExplorationSite site, World world, int anchorX, int anchorZ, List<CampBlock> plan) {
        Set<String> columns = new HashSet<>();
        for (CampBlock block : plan) columns.add(block.dx + ":" + block.dz);
        int minimum = world.getMaxHeight();
        int maximum = world.getMinHeight();
        for (String column : columns) {
            String[] split = column.split(":");
            int x = anchorX + Integer.parseInt(split[0]);
            int z = anchorZ + Integer.parseInt(split[1]);
            int ground = world.getHighestBlockYAt(x, z);
            Material groundMaterial = world.getBlockAt(x, ground, z).getType();
            boolean waterPlatform = site == ExplorationSite.DRAGON_COAST && groundMaterial == Material.WATER;
            if (!waterPlatform && !NATURAL_GROUND.contains(groundMaterial)) return null;
            minimum = Math.min(minimum, ground);
            maximum = Math.max(maximum, ground);
        }
        int maxSlope = Math.max(0, plugin.getConfig().getInt("region-control.camp.max-slope", 3));
        if (maximum - minimum > maxSlope) return null;
        for (CampBlock planned : plan) {
            Material current = world.getBlockAt(anchorX + planned.dx, maximum + 1 + planned.dy,
                    anchorZ + planned.dz).getType();
            if (!REPLACEABLE.contains(current)) return null;
        }
        return maximum;
    }

    private List<CampBlock> withFoundations(Location anchor, List<CampBlock> base) {
        List<CampBlock> result = new ArrayList<>(base);
        for (CampBlock block : base) {
            if (block.dy != 0 || block.levelOne == Material.AIR) continue;
            int ground = anchor.getWorld().getHighestBlockYAt(anchor.getBlockX() + block.dx,
                    anchor.getBlockZ() + block.dz);
            for (int y = ground + 1; y < anchor.getBlockY(); y++) {
                result.add(new CampBlock(block.dx, y - anchor.getBlockY(), block.dz,
                        Material.STRIPPED_SPRUCE_LOG, Material.STONE_BRICKS, Material.COBBLESTONE));
            }
        }
        return List.copyOf(result);
    }

    private List<CampBlock> blueprint() {
        Map<String, CampBlock> result = new LinkedHashMap<>();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) put(result, new CampBlock(x, 0, z, Material.SPRUCE_PLANKS,
                    Material.DARK_OAK_PLANKS, Material.COARSE_DIRT));
        }
        for (int x : new int[]{-2, 2}) for (int z : new int[]{-2, 2}) {
            put(result, new CampBlock(x, 1, z, Material.STRIPPED_SPRUCE_LOG,
                    Material.STRIPPED_DARK_OAK_LOG, Material.COBBLESTONE));
            put(result, new CampBlock(x, 2, z, Material.STRIPPED_SPRUCE_LOG,
                    Material.STRIPPED_DARK_OAK_LOG, Material.AIR));
        }
        for (int x = -1; x <= 1; x++) {
            put(result, new CampBlock(x, 2, 2, Material.WHITE_WOOL, Material.LIGHT_GRAY_WOOL, Material.AIR));
            put(result, new CampBlock(x, 2, -2, Material.WHITE_WOOL, Material.LIGHT_GRAY_WOOL, Material.AIR));
        }
        put(result, new CampBlock(0, 1, 0, Material.CAMPFIRE, Material.CAMPFIRE, Material.SOUL_CAMPFIRE));
        put(result, new CampBlock(-1, 1, -1, Material.BARREL, Material.BARREL, Material.AIR));
        put(result, new CampBlock(1, 1, -1, Material.CRAFTING_TABLE, Material.SMITHING_TABLE, Material.AIR));
        put(result, new CampBlock(0, 3, 0, Material.LANTERN, Material.SOUL_LANTERN, Material.AIR));
        put(result, new CampBlock(2, 1, 0, Material.OAK_FENCE, Material.DARK_OAK_FENCE, Material.AIR));
        put(result, new CampBlock(-2, 1, 0, Material.OAK_FENCE, Material.DARK_OAK_FENCE, Material.AIR));
        for (int x = -3; x <= 3; x++) {
            put(result, new CampBlock(x, 0, -3, Material.AIR, Material.COBBLESTONE, Material.GRAVEL));
            put(result, new CampBlock(x, 0, 3, Material.AIR, Material.COBBLESTONE, Material.GRAVEL));
        }
        put(result, new CampBlock(3, 1, 0, Material.AIR, Material.BELL, Material.AIR));
        put(result, new CampBlock(-3, 1, 0, Material.AIR, Material.CHEST, Material.AIR));
        return List.copyOf(result.values());
    }

    private void put(Map<String, CampBlock> values, CampBlock block) {
        values.put(block.dx + ":" + block.dy + ":" + block.dz, block);
    }

    private ExplorationSite missionRegion(MissionContract contract) {
        for (LivingEventType type : LivingEventType.values()) {
            if (type.contract() == contract) return type.region();
        }
        CampaignMetric metric = contract.metric();
        return switch (metric) {
            case DEFENSE -> ExplorationSite.UDING_WALL;
            case SUPPLY -> ExplorationSite.EASTERN_ROUTE;
            case INTELLIGENCE -> ExplorationSite.WESTERN_TRACE;
            case MORALE -> ExplorationSite.RONGXU_APPROACH;
        };
    }

    private Location siteLocation(ExplorationSite site) {
        Location landmark = atlas.landmarkLocation(site.landmark());
        if (landmark == null || landmark.getWorld() == null) return null;
        return landmark.clone().add(site.offsetX() * atlas.coordinateScale(), 0,
                site.offsetZ() * atlas.coordinateScale());
    }

    private Location ground(Location location) {
        World world = location.getWorld();
        int y = world.getHighestBlockYAt(location.getBlockX(), location.getBlockZ()) + 1;
        return new Location(world, location.getBlockX() + 0.5, y, location.getBlockZ() + 0.5);
    }

    private NamedTextColor color(RegionState state) {
        return switch (state) {
            case STABLE -> NamedTextColor.GREEN;
            case TENSE -> NamedTextColor.YELLOW;
            case LOST -> NamedTextColor.RED;
            case RECOVERING -> NamedTextColor.AQUA;
        };
    }

    private String data(Material material) {
        return material.createBlockData().getAsString();
    }

    private ItemStack item(Material material, String name, NamedTextColor color, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, color));
        meta.lore(lore.stream().map(line -> Component.text(line, NamedTextColor.GRAY)).toList());
        stack.setItemMeta(meta);
        return stack;
    }

    private record CampBlock(int dx, int dy, int dz, Material levelOne, Material levelTwo, Material lost) { }

    private static final class CampHolder implements InventoryHolder {
        private final ExplorationSite site;
        private Inventory inventory;

        private CampHolder(ExplorationSite site) { this.site = site; }

        @Override
        public Inventory getInventory() { return inventory; }
    }

    private static final class DynmapCampMarkers {
        private final EvilIslandPlugin plugin;
        private boolean warned;

        private DynmapCampMarkers(EvilIslandPlugin plugin) {
            this.plugin = plugin;
        }

        private void upsert(RegionControlSnapshot region) {
            if (!region.placed()) return;
            Plugin dynmap = Bukkit.getPluginManager().getPlugin("dynmap");
            if (dynmap == null || !dynmap.isEnabled()) return;
            try {
                Object api = invoke(dynmap, "getMarkerAPI");
                Object set = invoke(api, "getMarkerSet", "evil_island_camps");
                if (set == null) {
                    set = invoke(api, "createMarkerSet", "evil_island_camps", "噩盡島遠征營地", null, true);
                }
                String id = "camp-" + region.site().id();
                Object marker = invoke(set, "findMarker", id);
                String label = region.site().display() + "營地・" + region.state().display();
                if (marker == null) {
                    Object icon = invoke(api, "getMarkerIcon", "house");
                    if (icon == null) icon = invoke(api, "getMarkerIcon", "default");
                    marker = invoke(set, "createMarker", id, label, region.world(), region.x() + 0.5,
                            (double) region.y(), region.z() + 0.5, icon, true);
                } else {
                    invoke(marker, "setLocation", region.world(), region.x() + 0.5,
                            (double) region.y(), region.z() + 0.5);
                    invoke(marker, "setLabel", label);
                }
                if (marker != null) invoke(marker, "setDescription", "區域：" + region.site().display()
                        + "<br>狀態：" + region.state().display() + "<br>穩定度：" + region.stability()
                        + "/100<br>營地補給：" + region.supplies() + "/"
                        + RegionControlRules.campCapacity(region.campLevel()));
            } catch (ReflectiveOperationException | RuntimeException exception) {
                if (warned) return;
                warned = true;
                plugin.getLogger().log(Level.WARNING, "Dynmap expedition camp markers are unavailable", exception);
            }
        }

        private static Object invoke(Object target, String name, Object... args) throws ReflectiveOperationException {
            for (Method method : target.getClass().getMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
                Class<?>[] types = method.getParameterTypes();
                boolean compatible = true;
                for (int index = 0; index < args.length; index++) {
                    if (args[index] == null) continue;
                    if (!wrap(types[index]).isAssignableFrom(args[index].getClass())) {
                        compatible = false;
                        break;
                    }
                }
                if (!compatible) continue;
                if (!method.canAccess(target)) method.setAccessible(true);
                return method.invoke(target, args);
            }
            throw new NoSuchMethodException(target.getClass().getName() + "#" + name);
        }

        private static Class<?> wrap(Class<?> type) {
            if (!type.isPrimitive()) return type;
            if (type == boolean.class) return Boolean.class;
            if (type == int.class) return Integer.class;
            if (type == long.class) return Long.class;
            if (type == double.class) return Double.class;
            if (type == float.class) return Float.class;
            if (type == short.class) return Short.class;
            if (type == byte.class) return Byte.class;
            if (type == char.class) return Character.class;
            return type;
        }
    }
}
