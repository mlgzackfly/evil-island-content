package tw.zack.evilisland;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import tw.zack.evilisland.model.CityProject;
import tw.zack.evilisland.model.CityRoute;
import tw.zack.evilisland.model.CityRouteRules;
import tw.zack.evilisland.model.DevelopmentRules;
import tw.zack.evilisland.model.EventChain;
import tw.zack.evilisland.model.ExplorationSite;
import tw.zack.evilisland.model.Faction;
import tw.zack.evilisland.model.MissionContract;
import tw.zack.evilisland.model.MissionType;
import tw.zack.evilisland.model.TechniquePath;
import tw.zack.evilisland.model.WeaponMasterySnapshot;
import tw.zack.evilisland.model.WeaponType;
import tw.zack.evilisland.model.WorldDevelopmentSnapshot;
import tw.zack.evilisland.model.WorldResource;
import tw.zack.evilisland.model.SpeciesType;
import tw.zack.evilisland.model.ProjectConditionRules;
import tw.zack.evilisland.model.ProjectConditionSnapshot;
import tw.zack.evilisland.persistence.DatabaseManager;
import tw.zack.evilisland.persistence.DevelopmentRepository;
import tw.zack.evilisland.world.WorldAtlasService;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class DevelopmentService implements Listener {
    private final EvilIslandPlugin plugin;
    private final DatabaseManager database;
    private final DevelopmentRepository repository;
    private final CampaignService campaign;
    private final WorldAtlasService atlas;
    private final DaoFieldService daoFields;
    private final GameItemService items;
    private final NamespacedKey visualKey;
    private final Map<UUID, Map<WeaponType, WeaponMasterySnapshot>> mastery = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastExplorationCheck = new HashMap<>();
    private final Map<CityProject, ProjectConditionSnapshot> conditions = new EnumMap<>(CityProject.class);
    private WorldDevelopmentSnapshot state;
    private CityRoute route;
    private int routeCycle;
    private SpeciesService species;
    private ConstructionService construction;
    private DiplomacyService diplomacy;
    private InheritanceService inheritance;
    private WeaponService weapons;
    private LivingWorldService livingWorld;

    public DevelopmentService(EvilIslandPlugin plugin, DatabaseManager database, DevelopmentRepository repository,
                              CampaignService campaign, WorldAtlasService atlas, DaoFieldService daoFields,
                              GameItemService items) {
        this.plugin = plugin;
        this.database = database;
        this.repository = repository;
        this.campaign = campaign;
        this.atlas = atlas;
        this.daoFields = daoFields;
        this.items = items;
        this.visualKey = new NamespacedKey(plugin, "development_visual");
    }

    public void load() {
        Optional<WorldDevelopmentSnapshot> stored = repository.loadWorld();
        state = stored.orElseGet(() -> WorldDevelopmentSnapshot.initial(
                campaign.state().cycle(), System.currentTimeMillis()));
        if (stored.isEmpty()) repository.saveWorld(state);
        conditions.putAll(repository.loadConditions());
        long now = System.currentTimeMillis();
        for (CityProject project : CityProject.values()) {
            int initial = state.project(project) > 0 ? ProjectConditionRules.MAX_CONDITION : 0;
            conditions.putIfAbsent(project, new ProjectConditionSnapshot(project, initial, now));
        }
        repository.saveConditions(conditions);
        settleIfNeeded();
        loadRoute();
        refreshVisuals();
        plugin.getLogger().info("Development cycle " + state.cycle() + " loaded with "
                + completedProjectLevels() + " project levels.");
    }

    public WorldDevelopmentSnapshot state() {
        settleIfNeeded();
        return state;
    }

    public void setSpeciesService(SpeciesService species) {
        this.species = species;
    }

    public void setConstructionService(ConstructionService construction) {
        this.construction = construction;
    }

    public void setDiplomacyService(DiplomacyService diplomacy) {
        this.diplomacy = diplomacy;
    }

    public void setInheritanceService(InheritanceService inheritance) {
        this.inheritance = inheritance;
    }

    public void setWeaponService(WeaponService weapons) {
        this.weapons = weapons;
    }

    public void setLivingWorldService(LivingWorldService livingWorld) {
        this.livingWorld = livingWorld;
    }

    public int projectLevel(CityProject project) {
        return state().project(project);
    }

    public int projectCondition(CityProject project) {
        ProjectConditionSnapshot condition = conditions.get(project);
        return condition == null ? (projectLevel(project) > 0 ? 100 : 0) : condition.condition();
    }

    public int functionalProjectLevel(CityProject project) {
        return ProjectConditionRules.functionalLevel(projectLevel(project), projectCondition(project),
                plugin.getConfig().getInt("development.maintenance.offline-below", 30),
                plugin.getConfig().getInt("development.maintenance.full-effect-at", 60));
    }

    public void damageAfterDefenseFailure(int breaches) {
        List<String> damaged = new ArrayList<>();
        ProjectConditionRules.defenseFailureDamage(breaches, campaign.state().week(),
                plugin.getConfig().getInt("development.maintenance.defense-damage.wall-base", 18),
                plugin.getConfig().getInt("development.maintenance.defense-damage.wall-per-breach", 4),
                plugin.getConfig().getInt("development.maintenance.defense-damage.secondary-early", 12),
                plugin.getConfig().getInt("development.maintenance.defense-damage.secondary-late", 15))
                .forEach((project, amount) -> {
                    if (projectLevel(project) <= 0) return;
                    int before = projectCondition(project);
                    int after = Math.max(0, before - amount);
                    if (after == before) return;
                    setCondition(project, after);
                    damaged.add(project.display() + " " + before + "%→" + after + "%");
                });
        if (damaged.isEmpty()) return;
        refreshVisuals();
        Bukkit.broadcast(EvilIslandPlugin.message("攻勢造成城市設施損傷：" + String.join("、", damaged)
                + "。可從發展總覽調度修復。", NamedTextColor.RED));
    }

    public CityRoute activeRoute() {
        settleIfNeeded();
        if (routeCycle != campaign.state().cycle()) loadRoute();
        return route;
    }

    public boolean spendResources(Map<WorldResource, Integer> cost) {
        if (!canAfford(cost)) return false;
        EnumMap<WorldResource, Integer> resources = copyResources();
        cost.forEach((resource, amount) -> resources.put(resource,
                resources.getOrDefault(resource, 0) - Math.max(0, amount)));
        state = with(resources, null, null, null, null, null, timestamp());
        saveAsync();
        return true;
    }

    public void addResource(WorldResource resource, int amount) {
        if (amount <= 0) return;
        EnumMap<WorldResource, Integer> resources = copyResources();
        resources.merge(resource, amount, Integer::sum);
        state = with(resources, null, null, null, null, null, timestamp());
        saveAsync();
    }

    public void adjustReputation(Faction faction, int amount) {
        if (amount == 0) return;
        EnumMap<Faction, Integer> factions = copyFactions();
        factions.put(faction, clampReputation(factions.getOrDefault(faction, 0) + amount));
        state = with(null, null, factions, null, null, null, timestamp());
        saveAsync();
    }

    public TechniquePath technique(Player player, WeaponType weapon) {
        return mastery(player.getUniqueId(), weapon).technique();
    }

    public int mastery(Player player, WeaponType weapon) {
        return mastery(player.getUniqueId(), weapon).mastery();
    }

    public int masteryTier(Player player, WeaponType weapon) {
        int value = mastery(player, weapon);
        if (value >= DevelopmentRules.techniqueRequirement(3)) return 3;
        if (value >= DevelopmentRules.techniqueRequirement(2)) return 2;
        if (value >= DevelopmentRules.techniqueRequirement(1)) return 1;
        return 0;
    }

    public int defenseEnemyPerEntranceModifier() {
        return DevelopmentRules.defenseEnemyPerEntranceModifier(campaign.state().week(),
                state().chainComplete(EventChain.DISPLACED_PEOPLE), state().chainComplete(EventChain.ENEMY_MUSTER))
                + CityRouteRules.defenseModifier(activeRoute());
    }

    public int bossEscortModifier() {
        return DevelopmentRules.bossEscortModifier(campaign.state().week(),
                state().chainProgress(EventChain.ENEMY_MUSTER), EventChain.ENEMY_MUSTER.stageCount());
    }

    public void recordMission(MissionContract contract, Set<UUID> members, boolean fullReward) {
        EventChain chain = activeChain();
        int chainBefore = state().chainProgress(chain);
        Map<WorldResource, Integer> yield = new EnumMap<>(DevelopmentRules.missionYield(
                contract.missionType(), contract.risk(), fullReward));
        if (fullReward && state().chainComplete(EventChain.SAFE_ROUTE)
                && (contract.missionType() == MissionType.ESCORT || contract.missionType() == MissionType.GATHER)) {
            yield.merge(WorldResource.PROVISIONS, 1, Integer::sum);
        }
        if (!yield.isEmpty()) {
            EnumMap<WorldResource, Integer> resources = copyResources();
            yield.forEach((resource, amount) -> resources.merge(resource, amount, Integer::sum));
            state = with(resources, null, null, null, advanceChain(contract.missionType()), null, timestamp());
            saveAsync();
        }
        boolean chainCompleted = chainBefore < chain.stageCount() && state().chainComplete(chain);
        adjustMissionReputation(contract.missionType(), fullReward, chainCompleted ? chain : null);
        for (UUID memberId : members) {
            Player player = Bukkit.getPlayer(memberId);
            WeaponType weapon = player == null ? null : ownedWeapon(player);
            if (weapon != null) addMastery(memberId, weapon,
                    DevelopmentRules.masteryGain(contract.risk(), fullReward));
        }
        if (diplomacy != null) diplomacy.recordMission(contract, members, fullReward);
        if (inheritance != null) inheritance.recordMission(contract, members, fullReward);
    }

    public void openHub(Player player) {
        HubHolder holder = new HubHolder(Menu.HUB, null);
        Inventory inventory = create(holder, "新城發展與遠征");
        CityRoute currentRoute = activeRoute();
        inventory.setItem(4, item(currentRoute == null ? Material.CARTOGRAPHY_TABLE : currentRoute.icon(),
                currentRoute == null ? "本輪城市路線" : currentRoute.display(), NamedTextColor.GOLD,
                List.of(currentRoute == null ? "前三日由玩家共同決定本輪發展方向。" : currentRoute.summary(),
                        currentRoute == null ? "點擊查看三條互斥路線。" : "本輪選定後不可更改。")));
        inventory.setItem(10, item(Material.STONECUTTER, "公共工程", NamedTextColor.AQUA,
                List.of("投入任務帶回的物資，改變新城設施與能力。", resourceSummary())));
        inventory.setItem(12, item(Material.RECOVERY_COMPASS, "區域探索", NamedTextColor.GREEN,
                List.of("前往地標調查補給點、危險路線與特殊素材。", discoverySummary())));
        EventChain active = activeChain();
        inventory.setItem(14, item(active.icon(), "連續事件：" + active.display(), NamedTextColor.YELLOW,
                List.of(chainSummary(active), "任務或物資方案都能推進事件。")));
        inventory.setItem(16, item(Material.WRITABLE_BOOK, "勢力交涉", NamedTextColor.GOLD,
                List.of("以有限物資建立互利關係，不必一律戰鬥。", factionSummary())));
        inventory.setItem(18, item(Material.BELL, "新城動態通報", NamedTextColor.YELLOW,
                List.of("處理持續數日的區域危機與分歧事件。",
                        livingWorld == null ? "傳令人尚未就位。" : livingWorld.summary())));
        inventory.setItem(20, item(Material.CHIPPED_ANVIL, "城市設施狀況", NamedTextColor.RED,
                List.of("守城失敗會損傷已建設施，效益可能下降。", conditionSummary())));
        inventory.setItem(22, item(Material.SMITHING_TABLE, "兵器研習", NamedTextColor.LIGHT_PURPLE,
                List.of("熟練只解鎖橫向運用，不改變炁訣定型。", masterySummary(player))));
        inventory.setItem(24, item(Material.ENCHANTED_BOOK, "傳承修習", NamedTextColor.AQUA,
                List.of("以任務與材料完成傳承，不改變四訣定型。",
                        inheritance == null ? "傳承紀錄尚未就緒" : inheritance.summary(player))));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        try {
            mastery.put(event.getUniqueId(), new ConcurrentHashMap<>(repository.loadMastery(event.getUniqueId())));
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Cannot load weapon mastery for " + event.getUniqueId(), exception);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    Component.text("兵器熟練資料載入失敗，請稍後再試。", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        mastery.computeIfAbsent(event.getPlayer().getUniqueId(), id -> new ConcurrentHashMap<>());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        mastery.remove(event.getPlayer().getUniqueId());
        lastExplorationCheck.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null || event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        if (now - lastExplorationCheck.getOrDefault(player.getUniqueId(), 0L) < 1500L) return;
        lastExplorationCheck.put(player.getUniqueId(), now);
        if (!atlas.isMainWorld(player.getWorld())) return;
        for (ExplorationSite site : ExplorationSite.values()) {
            Location target = siteLocation(site);
            double dx = target == null ? Double.MAX_VALUE : player.getLocation().getX() - target.getX();
            double dz = target == null ? Double.MAX_VALUE : player.getLocation().getZ() - target.getZ();
            if (target != null && target.getWorld().equals(player.getWorld()) && dx * dx + dz * dz <= 14.0 * 14.0
                    && state().discoveryCycle(site) < campaign.state().cycle()) {
                discover(player, site);
                break;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpeciesInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || species == null) return;
        SpeciesType type = species.type(event.getRightClicked());
        if (type != SpeciesType.MAO_ENVOY && type != SpeciesType.NAJIN_TRADER) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(EvilIslandPlugin.message(type.display()
                + "願意透過新城議事調度交換物資。", NamedTextColor.GREEN));
        if (diplomacy != null) diplomacy.openContract(event.getPlayer());
        else openFactions(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof HubHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0
                || event.getRawSlot() >= top.getSize()) return;
        int slot = event.getRawSlot();
        if (holder.menu == Menu.HUB) {
            if (slot == 4) openRoutes(player);
            else if (slot == 10) openProjects(player);
            else if (slot == 12) openExploration(player);
            else if (slot == 14) openEvent(player);
            else if (slot == 16) {
                if (diplomacy != null) diplomacy.openContract(player);
                else openFactions(player);
            }
            else if (slot == 22) openTechniques(player);
            else if (slot == 24 && inheritance != null) inheritance.openMenu(player);
            else if (slot == 20) openMaintenance(player);
            else if (slot == 18 && livingWorld != null) livingWorld.openBoard(player);
        } else if (holder.menu == Menu.PROJECTS) {
            if (slot == 26) openHub(player);
            else if (holder.value instanceof CityProject project) invest(player, project);
            else {
                CityProject project = projectAt(slot);
                if (project != null) {
                    holder.value = project;
                    openProjectConfirmation(player, project);
                }
            }
        } else if (holder.menu == Menu.PROJECT_CONFIRM) {
            if (slot == 11) openProjects(player);
            else if (slot == 15 && holder.value instanceof CityProject project) invest(player, project);
        } else if (holder.menu == Menu.MAINTENANCE) {
            if (slot == 26) openHub(player);
            else {
                CityProject project = projectAt(slot);
                if (project != null) openMaintenanceConfirmation(player, project);
            }
        } else if (holder.menu == Menu.MAINTENANCE_CONFIRM) {
            if (slot == 11) openMaintenance(player);
            else if (slot == 15 && holder.value instanceof CityProject project) repair(player, project);
        } else if (holder.menu == Menu.EXPLORATION) {
            if (slot == 26) openHub(player);
            else {
                ExplorationSite site = explorationAt(slot);
                if (site != null) deployToSite(player, site);
            }
        } else if (holder.menu == Menu.EVENT) {
            if (slot == 15) resolveEventWithResources(player);
            else if (slot == 26) openHub(player);
        } else if (holder.menu == Menu.FACTIONS) {
            if (diplomacy != null) diplomacy.openContract(player);
            else if (slot == 26) openHub(player);
        } else if (holder.menu == Menu.TECHNIQUES) {
            if (slot == 26) openHub(player);
            else if (slot == 22 && weapons != null) weapons.openArmory(player);
            else {
                TechniquePath path = slot == 11 ? TechniquePath.MOBILITY
                        : slot == 13 ? TechniquePath.CONTROL : slot == 15 ? TechniquePath.GUARD : null;
                if (path != null) selectTechnique(player, path);
            }
        } else if (holder.menu == Menu.ROUTES) {
            if (slot == 26) openHub(player);
            else {
                CityRoute selected = routeAt(slot);
                if (selected != null) openRouteConfirmation(player, selected);
            }
        } else if (holder.menu == Menu.ROUTE_CONFIRM) {
            if (slot == 11) openRoutes(player);
            else if (slot == 15 && holder.value instanceof CityRoute selected) chooseRoute(player, selected);
        }
    }

    private void openRoutes(Player player) {
        HubHolder holder = new HubHolder(Menu.ROUTES, null);
        Inventory inventory = create(holder, "本輪城市路線");
        CityRoute current = activeRoute();
        int[] slots = {11, 13, 15};
        CityRoute[] routes = CityRoute.values();
        for (int index = 0; index < routes.length; index++) {
            CityRoute candidate = routes[index];
            List<String> lore = new ArrayList<>();
            lore.add(candidate.summary());
            lore.add(routeBenefit(candidate));
            lore.add(current == candidate ? "本輪已採用。" : current == null ? "點擊進一步確認。" : "本輪不可改選。");
            inventory.setItem(slots[index], item(candidate.icon(), candidate.display(),
                    current == candidate ? NamedTextColor.GREEN : NamedTextColor.GOLD, lore));
        }
        back(inventory);
        player.openInventory(inventory);
    }

    private void openRouteConfirmation(Player player, CityRoute selected) {
        if (!CityRouteRules.canChoose(campaign.state().day(), activeRoute() != null)) {
            player.sendMessage(EvilIslandPlugin.message(activeRoute() == null
                    ? "本輪路線只能在前三日決定。" : "本輪城市路線已定，下一輪才能重新選擇。",
                    NamedTextColor.RED));
            openRoutes(player);
            return;
        }
        HubHolder holder = new HubHolder(Menu.ROUTE_CONFIRM, selected);
        Inventory inventory = create(holder, "確認本輪城市路線");
        inventory.setItem(13, item(selected.icon(), selected.display(), NamedTextColor.GOLD,
                List.of(selected.summary(), routeBenefit(selected), "選定後維持到本輪結算。")));
        inventory.setItem(11, item(Material.ARROW, "返回", NamedTextColor.GREEN, List.of()));
        inventory.setItem(15, item(Material.LIME_CONCRETE, "確認路線", NamedTextColor.YELLOW,
                List.of("所有在線玩家將收到公告。")));
        player.openInventory(inventory);
    }

    private void chooseRoute(Player player, CityRoute selected) {
        if (!CityRouteRules.canChoose(campaign.state().day(), activeRoute() != null)) {
            openRoutes(player);
            return;
        }
        int cycle = campaign.state().cycle();
        repository.saveRoute(cycle, selected, System.currentTimeMillis());
        routeCycle = cycle;
        route = repository.loadRoute(cycle).orElse(null);
        if (route == null) {
            player.sendMessage(EvilIslandPlugin.message("城市路線保存失敗，未變更本輪方向。", NamedTextColor.RED));
            return;
        }
        Bukkit.broadcast(EvilIslandPlugin.message(player.getName() + "在議事廳確立「" + route.display()
                + "」，本輪不可改選。", NamedTextColor.GOLD));
        if (diplomacy != null) diplomacy.refreshForRoute();
        openHub(player);
    }

    private void openProjects(Player player) {
        HubHolder holder = new HubHolder(Menu.PROJECTS, null);
        Inventory inventory = create(holder, "新城公共工程");
        int[] slots = {10, 12, 14, 16, 22};
        CityProject[] projects = CityProject.values();
        for (int index = 0; index < projects.length; index++) {
            CityProject project = projects[index];
            int level = state().project(project);
            List<String> lore = new ArrayList<>();
            lore.add(project.benefit());
            lore.add("階段：" + level + "/" + project.maximumLevel());
            if (level > 0) lore.add("狀況：" + projectCondition(project) + "%（" + conditionStatus(project)
                    + "，可用階段 " + functionalProjectLevel(project) + "）");
            if (construction != null && level > 0) lore.add(construction.status(project));
            lore.add(level >= project.maximumLevel() ? "工程已完成。" : "下一階段："
                    + costText(CityRouteRules.projectCost(project, level + 1, activeRoute())));
            inventory.setItem(slots[index], item(project.icon(), project.display(), NamedTextColor.AQUA, lore));
        }
        inventory.setItem(4, item(Material.CHEST, "公共庫存", NamedTextColor.GOLD, List.of(resourceSummary())));
        back(inventory);
        player.openInventory(inventory);
    }

    private void openProjectConfirmation(Player player, CityProject project) {
        HubHolder holder = new HubHolder(Menu.PROJECT_CONFIRM, project);
        Inventory inventory = create(holder, "確認投入公共工程");
        int next = state().project(project) + 1;
        inventory.setItem(13, item(project.icon(), project.display(), NamedTextColor.AQUA,
                List.of(project.benefit(), next > project.maximumLevel() ? "工程已完成。"
                        : "投入：" + costText(CityRouteRules.projectCost(project, next, activeRoute())))));
        inventory.setItem(11, item(Material.ARROW, "返回", NamedTextColor.GREEN, List.of()));
        inventory.setItem(15, item(Material.LIME_CONCRETE, "確認建造", NamedTextColor.YELLOW,
                List.of("公共物資將立即扣除。")));
        player.openInventory(inventory);
    }

    private void openMaintenance(Player player) {
        HubHolder holder = new HubHolder(Menu.MAINTENANCE, null);
        Inventory inventory = create(holder, "城市設施狀況與修復");
        int[] slots = {10, 12, 14, 16, 22};
        CityProject[] projects = CityProject.values();
        for (int index = 0; index < projects.length; index++) {
            CityProject project = projects[index];
            int built = projectLevel(project);
            int condition = projectCondition(project);
            int functional = functionalProjectLevel(project);
            List<String> lore = new ArrayList<>();
            lore.add("狀況：" + condition + "%（" + conditionStatus(project) + "）");
            lore.add("建設階段 " + built + "，目前可用階段 " + functional);
            lore.add(built <= 0 ? "尚無設施可修復。" : condition >= 100 ? "目前不需修復。"
                    : "修復需要：" + costText(repairCost(project)));
            inventory.setItem(slots[index], item(project.icon(), project.display(),
                    functional < built ? NamedTextColor.RED : NamedTextColor.GREEN, lore));
        }
        inventory.setItem(4, item(Material.CHEST, "公共庫存", NamedTextColor.GOLD, List.of(resourceSummary())));
        back(inventory);
        player.openInventory(inventory);
    }

    private void openMaintenanceConfirmation(Player player, CityProject project) {
        HubHolder holder = new HubHolder(Menu.MAINTENANCE_CONFIRM, project);
        Inventory inventory = create(holder, "確認修復城市設施");
        int built = projectLevel(project);
        int condition = projectCondition(project);
        inventory.setItem(13, item(project.icon(), project.display(), NamedTextColor.AQUA,
                List.of("目前狀況：" + condition + "%", built <= 0 ? "尚未建設。"
                        : condition >= 100 ? "設施狀況完整。" : "修復後："
                        + repairedCondition(condition) + "%",
                        built <= 0 || condition >= 100 ? "不會消耗物資。"
                                : "投入：" + costText(repairCost(project)))));
        inventory.setItem(11, item(Material.ARROW, "返回", NamedTextColor.GREEN, List.of()));
        inventory.setItem(15, item(Material.LIME_CONCRETE, "確認修復", NamedTextColor.YELLOW,
                List.of("公共物資將立即扣除。")));
        player.openInventory(inventory);
    }

    private void openExploration(Player player) {
        HubHolder holder = new HubHolder(Menu.EXPLORATION, null);
        Inventory inventory = create(holder, "區域探索情報");
        int[] slots = {10, 12, 14, 16, 22};
        ExplorationSite[] sites = ExplorationSite.values();
        for (int index = 0; index < sites.length; index++) {
            ExplorationSite site = sites[index];
            Location location = siteLocation(site);
            boolean current = state().discoveryCycle(site) == campaign.state().cycle();
            inventory.setItem(slots[index], item(site.icon(), site.display(), current
                    ? NamedTextColor.GREEN : NamedTextColor.YELLOW, List.of(
                    current ? "本輪已完成調查。" : "本輪尚待實地調查。",
                    location == null ? "位置情報不足。" : "座標 X " + location.getBlockX() + "、Z " + location.getBlockZ(),
                    "調查所得：" + site.reward().display(), state().discovered(site)
                            ? "輕疾站階段 2 後可點擊消耗 1 份糧秣部署。" : "首次必須實地抵達。")));
        }
        back(inventory);
        player.openInventory(inventory);
    }

    private void openEvent(Player player) {
        EventChain chain = activeChain();
        HubHolder holder = new HubHolder(Menu.EVENT, chain);
        Inventory inventory = create(holder, "連續事件：" + chain.display());
        inventory.setItem(11, item(chain.icon(), "現場處理", NamedTextColor.RED,
                List.of(chainSummary(chain), "完成指定類型任務即可推進。")));
        inventory.setItem(15, item(Material.CHEST, "調度物資處理", NamedTextColor.GOLD,
                List.of("消耗 4 份指定物資，作為本階段非戰鬥解法。", eventResource(chain).display())));
        back(inventory);
        player.openInventory(inventory);
    }

    private void openFactions(Player player) {
        HubHolder holder = new HubHolder(Menu.FACTIONS, null);
        Inventory inventory = create(holder, "勢力關係與交涉");
        int[] slots = {10, 12, 14, 16, 20, 22};
        Faction[] factions = Faction.values();
        for (int index = 0; index < factions.length; index++) {
            Faction faction = factions[index];
            int value = state().reputation(faction);
            List<String> lore = new ArrayList<>();
            lore.add("關係：" + faction.relation(value) + "（" + value + "）");
            if (faction == Faction.SUI_AN || faction == Faction.NEW_CITY) lore.add("透過任務結果逐步改變。 ");
            else lore.add("點擊調度 3 份" + factionResource(faction).display() + "進行交涉。 ");
            inventory.setItem(slots[index], item(faction.icon(), faction.display(), NamedTextColor.GOLD, lore));
        }
        back(inventory);
        player.openInventory(inventory);
    }

    private void openTechniques(Player player) {
        HubHolder holder = new HubHolder(Menu.TECHNIQUES, null);
        Inventory inventory = create(holder, "兵器橫向技法");
        WeaponType weapon = ownedWeapon(player);
        if (weapon == null) {
            inventory.setItem(13, item(Material.BARRIER, "未找到登記兵器", NamedTextColor.RED,
                    List.of("攜帶自己的歲安軍團兵器後再來。")));
        } else {
            WeaponMasterySnapshot value = mastery(player.getUniqueId(), weapon);
            inventory.setItem(4, item(weapon.material(), weapon.display(), NamedTextColor.AQUA,
                    List.of("熟練：" + value.mastery(), "目前運用：" + value.technique().display(),
                            "工程需求：軍械工坊階段 1")));
            inventory.setItem(11, techniqueItem(TechniquePath.MOBILITY));
            inventory.setItem(13, techniqueItem(TechniquePath.CONTROL));
            inventory.setItem(15, techniqueItem(TechniquePath.GUARD));
            inventory.setItem(22, item(Material.ANVIL, "軍械庫換裝", NamedTextColor.GOLD,
                    List.of("軍械工坊階段 2 後可更換登記兵器。", "耗損比例與各兵器熟練均會保留。")));
        }
        back(inventory);
        player.openInventory(inventory);
    }

    private ItemStack techniqueItem(TechniquePath path) {
        return item(path.icon(), path.display(), NamedTextColor.LIGHT_PURPLE,
                List.of(path.summary(), "需求：兵器熟練 " + DevelopmentRules.techniqueRequirement(1),
                        "可在城內重新選擇，不改變發散／內聚與四訣。"));
    }

    private void invest(Player player, CityProject project) {
        int current = state().project(project);
        if (current >= project.maximumLevel()) {
            player.sendMessage(EvilIslandPlugin.message("這項工程已完成。"));
            openProjects(player);
            return;
        }
        Map<WorldResource, Integer> cost = CityRouteRules.projectCost(project, current + 1, activeRoute());
        if (!canAfford(cost)) {
            player.sendMessage(EvilIslandPlugin.message("公共物資不足，需要「" + costText(cost) + "」。",
                    NamedTextColor.RED));
            openProjects(player);
            return;
        }
        EnumMap<WorldResource, Integer> resources = copyResources();
        cost.forEach((resource, amount) -> resources.put(resource, resources.getOrDefault(resource, 0) - amount));
        EnumMap<CityProject, Integer> projects = copyProjects();
        projects.put(project, current + 1);
        state = with(resources, projects, null, null, null, null, timestamp());
        setCondition(project, ProjectConditionRules.MAX_CONDITION);
        saveAsync();
        refreshVisuals();
        if (construction != null) construction.upgrade(project, current + 1);
        Bukkit.broadcast(EvilIslandPlugin.message(project.display() + "完成階段 " + (current + 1) + "。",
                NamedTextColor.GREEN));
        openProjects(player);
    }

    private void discover(Player player, ExplorationSite site) {
        EnumMap<ExplorationSite, Integer> discoveries = copyDiscoveries();
        discoveries.put(site, campaign.state().cycle());
        EnumMap<WorldResource, Integer> resources = copyResources();
        int routeBonus = site == ExplorationSite.EASTERN_ROUTE
                && state().chainComplete(EventChain.SAFE_ROUTE) ? 1 : 0;
        int amount = 2 + functionalProjectLevel(CityProject.SCOUT_POST) + routeBonus
                + (activeRoute() == CityRoute.EXPEDITION ? 1 : 0);
        resources.merge(site.reward(), amount, Integer::sum);
        resources.merge(WorldResource.SPECIAL, site.reward() == WorldResource.SPECIAL ? 0 : 1, Integer::sum);
        state = with(resources, null, null, discoveries, null, null, timestamp());
        saveAsync();
        player.sendMessage(EvilIslandPlugin.message("完成「" + site.display() + "」本輪調查，帶回 "
                + amount + " 份" + site.reward().display() + "。", NamedTextColor.GREEN));
        player.getWorld().strikeLightningEffect(player.getLocation());
        spawnSiteEcology(player, site);
    }

    private void spawnSiteEcology(Player player, ExplorationSite site) {
        if (species == null) return;
        Location center = player.getLocation();
        if (site == ExplorationSite.RONGXU_APPROACH) {
            spawnIfAbsent(center.clone().add(3, 0, 2), SpeciesType.MAO_ENVOY);
            return;
        }
        if (site == ExplorationSite.EASTERN_ROUTE) {
            spawnIfAbsent(center.clone().add(3, 0, 2), SpeciesType.NAJIN_TRADER);
            return;
        }
        if ((site == ExplorationSite.UDING_WALL || site == ExplorationSite.WESTERN_TRACE)
                && state().reputation(Faction.QUANRONG) >= 25) {
            player.sendMessage(EvilIslandPlugin.message("犬戎巡獵隊辨認出互利信物，讓出本段道路。",
                    NamedTextColor.GOLD));
            return;
        }
        if (site == ExplorationSite.UDING_WALL || site == ExplorationSite.WESTERN_TRACE) {
            int count = site == ExplorationSite.WESTERN_TRACE ? 3 : 2;
            for (int index = 0; index < count; index++) {
                spawnIfAbsent(center.clone().add(5 + index * 2, 0, index - 1), SpeciesType.QUANRONG_HUNTER);
            }
            if (campaign.state().week() >= 3) spawnIfAbsent(center.clone().add(9, 0, 3),
                    SpeciesType.QUANRONG_ALPHA);
            return;
        }
        if (site == ExplorationSite.DRAGON_COAST) {
            if (state().reputation(Faction.QIULONG) >= 25) {
                player.sendMessage(EvilIslandPlugin.message("虯龍關係提供了安全潮路，本次避開掠空群。",
                        NamedTextColor.GOLD));
                return;
            }
            int count = Math.max(1, 3 - functionalProjectLevel(CityProject.AIR_DEFENSE));
            for (int index = 0; index < count; index++) {
                spawnIfAbsent(center.clone().add(5 + index * 2, 0, index * 2), SpeciesType.YUJIANG_RAIDER);
            }
            if (functionalProjectLevel(CityProject.AIR_DEFENSE) < 2) {
                spawnIfAbsent(center.clone().add(9, 0, -3), SpeciesType.YUJIANG_WINDBREAKER);
            }
        }
    }

    private void spawnIfAbsent(Location location, SpeciesType type) {
        boolean exists = location.getWorld().getNearbyEntities(location, 32, 20, 32).stream()
                .anyMatch(entity -> species.type(entity) == type);
        if (!exists) species.spawnEcology(type, ground(location));
    }

    private void resolveEventWithResources(Player player) {
        EventChain chain = activeChain();
        int progress = state().chainProgress(chain);
        if (progress >= chain.stageCount()) {
            player.sendMessage(EvilIslandPlugin.message("本輪事件已處理完成。"));
            openEvent(player);
            return;
        }
        WorldResource resource = eventResource(chain);
        if (state().resource(resource) < 4) {
            player.sendMessage(EvilIslandPlugin.message("公共庫存缺少 4 份" + resource.display() + "。",
                    NamedTextColor.RED));
            return;
        }
        EnumMap<WorldResource, Integer> resources = copyResources();
        resources.put(resource, resources.get(resource) - 4);
        EnumMap<EventChain, Integer> chains = copyChains();
        chains.put(chain, progress + 1);
        boolean completedNow = progress + 1 == chain.stageCount();
        EnumMap<Faction, Integer> factions = copyFactions();
        Faction target = switch (chain) {
            case SAFE_ROUTE -> Faction.NEW_CITY;
            case DISPLACED_PEOPLE -> Faction.MAO;
            case ENEMY_MUSTER -> Faction.SUI_AN;
        };
        factions.put(target, clampReputation(factions.getOrDefault(target, 0) + 4));
        if (completedNow) applyChainReputation(factions, chain);
        state = with(resources, null, factions, null, chains, null, timestamp());
        saveAsync();
        player.sendMessage(EvilIslandPlugin.message("物資方案生效，事件推進至 " + (progress + 1) + "/"
                + chain.stageCount() + "。", NamedTextColor.GREEN));
        if (completedNow) Bukkit.broadcast(EvilIslandPlugin.message("連續事件「" + chain.display()
                + "」已透過調度完成。", NamedTextColor.GREEN));
        openEvent(player);
    }

    public void recordSpeciesDefeat(SpeciesType type) {
        if (type != SpeciesType.QUANRONG_HUNTER && type != SpeciesType.QUANRONG_ALPHA) return;
        EnumMap<Faction, Integer> factions = copyFactions();
        int loss = type.elite() ? 6 : 3;
        factions.put(Faction.QUANRONG,
                clampReputation(factions.getOrDefault(Faction.QUANRONG, 0) - loss));
        state = with(null, null, factions, null, null, null, timestamp());
        saveAsync();
    }

    private void deployToSite(Player player, ExplorationSite site) {
        if (!state().discovered(site)) {
            player.sendMessage(EvilIslandPlugin.message("首次調查必須依座標實地抵達。"));
            return;
        }
        int requirement = CityRouteRules.deploymentScoutRequirement(activeRoute());
        if (functionalProjectLevel(CityProject.SCOUT_POST) < requirement) {
            player.sendMessage(EvilIslandPlugin.message("輕疾站目前可用階段需達 " + requirement
                    + "，請先建設或修復。"));
            return;
        }
        if (state().resource(WorldResource.PROVISIONS) < 1) {
            player.sendMessage(EvilIslandPlugin.message("公共庫存缺少 1 份城防糧秣。", NamedTextColor.RED));
            return;
        }
        Location target = siteLocation(site);
        if (target == null) return;
        EnumMap<WorldResource, Integer> resources = copyResources();
        resources.put(WorldResource.PROVISIONS, resources.getOrDefault(WorldResource.PROVISIONS, 0) - 1);
        state = with(resources, null, null, null, null, null, timestamp());
        saveAsync();
        player.closeInventory();
        player.sendMessage(EvilIslandPlugin.message("輕疾正在安排前往「" + site.display() + "」的部署路線。"));
        target.getWorld().getChunkAtAsync(target).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            player.teleportAsync(ground(target));
            player.sendMessage(EvilIslandPlugin.message("已抵達「" + site.display() + "」補給點。",
                    NamedTextColor.GREEN));
        }));
    }

    private void negotiate(Player player, Faction faction) {
        if (faction == Faction.SUI_AN || faction == Faction.NEW_CITY) {
            player.sendMessage(EvilIslandPlugin.message("這項關係由任務與守城結果決定。"));
            return;
        }
        WorldResource resource = factionResource(faction);
        if (state().resource(resource) < 3) {
            player.sendMessage(EvilIslandPlugin.message("交涉需要公共庫存中的 3 份" + resource.display() + "。",
                    NamedTextColor.RED));
            return;
        }
        EnumMap<WorldResource, Integer> resources = copyResources();
        resources.put(resource, resources.get(resource) - 3);
        EnumMap<Faction, Integer> factions = copyFactions();
        factions.put(faction, clampReputation(factions.getOrDefault(faction, 0) + 8));
        state = with(resources, null, factions, null, null, null, timestamp());
        saveAsync();
        player.sendMessage(EvilIslandPlugin.message("與" + faction.display() + "的關係有所改善。",
                NamedTextColor.GREEN));
        openFactions(player);
    }

    private void selectTechnique(Player player, TechniquePath path) {
        WeaponType weapon = ownedWeapon(player);
        if (weapon == null) return;
        if (functionalProjectLevel(CityProject.WORKSHOP) < 1) {
            player.sendMessage(EvilIslandPlugin.message("軍械工坊目前可用階段不足，請先建設或修復。",
                    NamedTextColor.RED));
            return;
        }
        WeaponMasterySnapshot current = mastery(player.getUniqueId(), weapon);
        if (current.mastery() < DevelopmentRules.techniqueRequirement(1)) {
            player.sendMessage(EvilIslandPlugin.message("兵器熟練不足，需達 "
                    + DevelopmentRules.techniqueRequirement(1) + "。", NamedTextColor.RED));
            return;
        }
        WeaponMasterySnapshot updated = new WeaponMasterySnapshot(player.getUniqueId(), weapon,
                current.mastery(), path, System.currentTimeMillis());
        mastery.get(player.getUniqueId()).put(weapon, updated);
        saveMastery(updated);
        player.sendMessage(EvilIslandPlugin.message(weapon.display() + "已改用「" + path.display() + "」。",
                NamedTextColor.GREEN));
        openTechniques(player);
    }

    private Map<EventChain, Integer> advanceChain(MissionType type) {
        EventChain chain = activeChain();
        int progress = state.chainProgress(chain);
        if (chain.requiredType(progress) != type) return state.chains();
        EnumMap<EventChain, Integer> chains = copyChains();
        chains.put(chain, progress + 1);
        if (progress + 1 == chain.stageCount()) {
            Bukkit.broadcast(EvilIslandPlugin.message("連續事件「" + chain.display() + "」已完成。",
                    NamedTextColor.GREEN));
        }
        return chains;
    }

    private void adjustMissionReputation(MissionType type, boolean fullReward, EventChain completedChain) {
        if (!fullReward) return;
        EnumMap<Faction, Integer> factions = copyFactions();
        Faction target = switch (type) {
            case PATROL, DEFENSE -> Faction.SUI_AN;
            case GATHER, ESCORT -> Faction.NEW_CITY;
            case SCOUT -> Faction.NAJIN;
            case RESCUE -> Faction.MAO;
        };
        factions.put(target, clampReputation(factions.getOrDefault(target, 0) + 2));
        if (completedChain != null) applyChainReputation(factions, completedChain);
        state = with(null, null, factions, null, null, null, timestamp());
        saveAsync();
    }

    private void applyChainReputation(EnumMap<Faction, Integer> factions, EventChain chain) {
        Faction primary = switch (chain) {
            case SAFE_ROUTE -> Faction.NEW_CITY;
            case DISPLACED_PEOPLE -> Faction.MAO;
            case ENEMY_MUSTER -> Faction.SUI_AN;
        };
        factions.put(primary, clampReputation(factions.getOrDefault(primary, 0) + 8));
    }

    private void settleIfNeeded() {
        if (state == null) return;
        while (campaign.state().cycle() > state.cycle()) {
            long now = timestamp();
            int oldCycle = state.cycle();
            int completed = (int) java.util.Arrays.stream(EventChain.values()).filter(state::chainComplete).count();
            int discovered = (int) java.util.Arrays.stream(ExplorationSite.values())
                    .filter(site -> state.discoveryCycle(site) == oldCycle).count();
            String ending = DevelopmentRules.ending(state.projects(), state.reputation(), completed, discovered);
            String summary = "工程 " + completedProjectLevels() + "，事件 " + completed + "，探索 " + discovered;
            repository.recordCycle(oldCycle, ending, summary, now);
            EnumMap<WorldResource, Integer> resources = new EnumMap<>(WorldResource.class);
            state.resources().forEach((resource, amount) -> resources.put(resource,
                    DevelopmentRules.carryOverResource(amount)));
            EnumMap<Faction, Integer> factions = copyFactions();
            factions.replaceAll((faction, value) -> value * 9 / 10);
            state = new WorldDevelopmentSnapshot(oldCycle + 1, resources, state.projects(), factions,
                    state.discoveries(), Map.of(), ending, now);
            repository.saveWorld(state);
            route = null;
            routeCycle = oldCycle + 1;
            Bukkit.broadcast(EvilIslandPlugin.message("第 " + oldCycle + " 輪結算：「" + ending
                    + "」。工程與關係延續，事件位置已重組。", NamedTextColor.GOLD));
        }
    }

    public void tick() {
        settleIfNeeded();
        if (routeCycle != campaign.state().cycle()) loadRoute();
    }

    public void flush() {
        if (state != null) repository.saveWorld(state);
        repository.saveConditions(new EnumMap<>(conditions));
    }

    public int runSelfTest() {
        int checks = 0;
        if (CityProject.values().length == 5) checks++;
        if (Faction.values().length == 6) checks++;
        if (ExplorationSite.values().length == 5) checks++;
        if (EventChain.values().length == 3) checks++;
        if (TechniquePath.values().length == 4) checks++;
        if (DevelopmentRules.missionYield(MissionType.DEFENSE, 4, true)
                .getOrDefault(WorldResource.MASONRY, 0) > 0) checks++;
        if (DevelopmentRules.ending(Map.of(CityProject.WALLS, 3), Map.of(), 3, 5).equals("遠路重開")) checks++;
        if (state != null && state.cycle() >= 1) checks++;
        if (CityRoute.values().length == 3) checks++;
        if (ProjectConditionRules.functionalLevel(3, 59) == 2) checks++;
        if (ProjectConditionRules.functionalLevel(3, 29) == 0) checks++;
        if (ProjectConditionRules.repairedCondition(90) == 100) checks++;
        if (conditions.size() == CityProject.values().length) checks++;
        return checks;
    }

    public int runSceneSelfTest(Location location) {
        Location base = ground(location);
        BlockDisplay block = base.getWorld().spawn(base, BlockDisplay.class);
        block.setBlock(Bukkit.createBlockData(Material.STONE_BRICKS));
        block.getPersistentDataContainer().set(visualKey, PersistentDataType.STRING, CityProject.WALLS.id());
        TextDisplay label = base.getWorld().spawn(base.clone().add(0, 2.0, 0), TextDisplay.class);
        label.text(Component.text("工程場景自檢"));
        label.getPersistentDataContainer().set(visualKey, PersistentDataType.STRING, CityProject.WALLS.id());
        int checks = 0;
        if (block.getBlock().getMaterial() == Material.STONE_BRICKS) checks++;
        if (CityProject.WALLS.id().equals(block.getPersistentDataContainer()
                .get(visualKey, PersistentDataType.STRING))) checks++;
        if (label.text() != null) checks++;
        if (CityProject.WALLS.id().equals(label.getPersistentDataContainer()
                .get(visualKey, PersistentDataType.STRING))) checks++;
        block.remove();
        label.remove();
        return checks;
    }

    public void refreshVisuals() {
        Location center = daoFields.cityCenter();
        if (center == null || center.getWorld() == null) return;
        World world = center.getWorld();
        double radius = plugin.getConfig().getDouble("development.visual-radius", 58.0) * atlas.coordinateScale();
        for (Entity entity : new ArrayList<>(world.getNearbyEntities(center, radius, 80, radius))) {
            if (entity.getPersistentDataContainer().has(visualKey, PersistentDataType.STRING)) entity.remove();
        }
        for (CityProject project : CityProject.values()) spawnProjectVisual(center, project, state.project(project));
    }

    private void spawnProjectVisual(Location center, CityProject project, int level) {
        if (level <= 0) return;
        Location base = projectLocation(center, project);
        int condition = projectCondition(project);
        Material material = projectBlock(project, condition);
        BlockData blockData = Bukkit.createBlockData(material);
        for (int index = 0; index < level; index++) {
            Location location = ground(base.clone().add(index - (level - 1) / 2.0, index == 2 ? 1 : 0, 0));
            BlockDisplay display = location.getWorld().spawn(location, BlockDisplay.class);
            display.setBlock(blockData);
            display.setPersistent(true);
            display.getPersistentDataContainer().set(visualKey, PersistentDataType.STRING, project.id());
        }
        Location labelLocation = ground(base).add(0.5, 2.4, 0.5);
        TextDisplay label = labelLocation.getWorld().spawn(labelLocation, TextDisplay.class);
        label.text(Component.text(project.display() + "　階段 " + level + "　狀況 " + condition + "%"));
        label.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
        label.setPersistent(true);
        label.getPersistentDataContainer().set(visualKey, PersistentDataType.STRING, project.id());
    }

    private Location projectLocation(Location center, CityProject project) {
        double scale = atlas.coordinateScale();
        return switch (project) {
            case WALLS -> center.clone().add(20 * scale, 0, 20 * scale);
            case QI_MIRROR -> center.clone().add(-18 * scale, 0, 10 * scale);
            case WORKSHOP -> center.clone().add(10 * scale, 0, -18 * scale);
            case SCOUT_POST -> center.clone().add(-16 * scale, 0, -18 * scale);
            case AIR_DEFENSE -> center.clone().add(0, 0, 22 * scale);
        };
    }

    private Material projectBlock(CityProject project, int condition) {
        if (condition < plugin.getConfig().getInt("development.maintenance.full-effect-at", 60)) {
            return switch (project) {
                case WALLS -> Material.CRACKED_STONE_BRICKS;
                case QI_MIRROR -> Material.REDSTONE_LAMP;
                case WORKSHOP -> Material.OXIDIZED_COPPER;
                case SCOUT_POST -> Material.STRIPPED_OAK_LOG;
                case AIR_DEFENSE -> Material.CRACKED_DEEPSLATE_BRICKS;
            };
        }
        return switch (project) {
            case WALLS -> Material.STONE_BRICKS;
            case QI_MIRROR -> Material.SEA_LANTERN;
            case WORKSHOP -> Material.COPPER_BLOCK;
            case SCOUT_POST -> Material.OAK_LOG;
            case AIR_DEFENSE -> Material.DEEPSLATE_BRICKS;
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

    private EventChain activeChain() {
        return switch (campaign.state().week()) {
            case 1 -> EventChain.SAFE_ROUTE;
            case 2 -> EventChain.DISPLACED_PEOPLE;
            default -> EventChain.ENEMY_MUSTER;
        };
    }

    private WorldResource eventResource(EventChain chain) {
        return switch (chain) {
            case SAFE_ROUTE -> WorldResource.TIMBER;
            case DISPLACED_PEOPLE -> WorldResource.PROVISIONS;
            case ENEMY_MUSTER -> WorldResource.MASONRY;
        };
    }

    private WorldResource factionResource(Faction faction) {
        return switch (faction) {
            case QUANRONG -> WorldResource.PROVISIONS;
            case MAO -> WorldResource.TIMBER;
            case NAJIN -> WorldResource.COMPONENTS;
            case QIULONG -> WorldResource.SPECIAL;
            case SUI_AN -> WorldResource.MASONRY;
            case NEW_CITY -> WorldResource.PROVISIONS;
        };
    }

    private void loadRoute() {
        routeCycle = campaign.state().cycle();
        route = repository.loadRoute(routeCycle).orElse(null);
    }

    private String routeBenefit(CityRoute route) {
        return switch (route) {
            case FORTRESS -> "指定防禦工程減免 20%，每處入口少 1 名攻城敵人。";
            case EXPEDITION -> "指定遠征工程減免 20%，探索多 1 份主物資。";
            case QI_CIVIC -> "聚炁鏡工程減免 20%，城內炁恢復每次多 1 點。";
        };
    }

    private CityRoute routeAt(int slot) {
        return switch (slot) {
            case 11 -> CityRoute.FORTRESS;
            case 13 -> CityRoute.EXPEDITION;
            case 15 -> CityRoute.QI_CIVIC;
            default -> null;
        };
    }

    private CityProject projectAt(int slot) {
        return switch (slot) {
            case 10 -> CityProject.WALLS;
            case 12 -> CityProject.QI_MIRROR;
            case 14 -> CityProject.WORKSHOP;
            case 16 -> CityProject.SCOUT_POST;
            case 22 -> CityProject.AIR_DEFENSE;
            default -> null;
        };
    }

    private Faction factionAt(int slot) {
        return switch (slot) {
            case 10 -> Faction.SUI_AN;
            case 12 -> Faction.NEW_CITY;
            case 14 -> Faction.QUANRONG;
            case 16 -> Faction.MAO;
            case 20 -> Faction.NAJIN;
            case 22 -> Faction.QIULONG;
            default -> null;
        };
    }

    private ExplorationSite explorationAt(int slot) {
        return switch (slot) {
            case 10 -> ExplorationSite.EASTERN_ROUTE;
            case 12 -> ExplorationSite.UDING_WALL;
            case 14 -> ExplorationSite.RONGXU_APPROACH;
            case 16 -> ExplorationSite.WESTERN_TRACE;
            case 22 -> ExplorationSite.DRAGON_COAST;
            default -> null;
        };
    }

    private WeaponType ownedWeapon(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (items.isOwnedWeapon(stack, player.getUniqueId())) return items.weaponType(stack);
        }
        return null;
    }

    private WeaponMasterySnapshot mastery(UUID playerId, WeaponType weapon) {
        Map<WeaponType, WeaponMasterySnapshot> values = mastery.computeIfAbsent(playerId,
                id -> new ConcurrentHashMap<>(repository.loadMastery(id)));
        return values.computeIfAbsent(weapon, type -> new WeaponMasterySnapshot(playerId, type, 0,
                TechniquePath.UNTRAINED, System.currentTimeMillis()));
    }

    private void addMastery(UUID playerId, WeaponType weapon, int amount) {
        WeaponMasterySnapshot current = mastery(playerId, weapon);
        WeaponMasterySnapshot updated = new WeaponMasterySnapshot(playerId, weapon,
                current.mastery() + Math.max(0, amount), current.technique(), System.currentTimeMillis());
        mastery.get(playerId).put(weapon, updated);
        saveMastery(updated);
    }

    private void saveMastery(WeaponMasterySnapshot snapshot) {
        database.submit(() -> repository.saveMastery(snapshot)).exceptionally(exception -> {
            plugin.getLogger().warning("Cannot save weapon mastery: " + exception.getMessage());
            return null;
        });
    }

    private boolean canAfford(Map<WorldResource, Integer> cost) {
        return cost.entrySet().stream().allMatch(entry -> state().resource(entry.getKey()) >= entry.getValue());
    }

    private String costText(Map<WorldResource, Integer> cost) {
        return cost.entrySet().stream().map(entry -> entry.getKey().display() + " " + entry.getValue())
                .reduce((left, right) -> left + "、" + right).orElse("無");
    }

    private String resourceSummary() {
        return java.util.Arrays.stream(WorldResource.values())
                .map(resource -> resource.display() + " " + state().resource(resource))
                .reduce((left, right) -> left + "　" + right).orElse("");
    }

    private String discoverySummary() {
        long count = java.util.Arrays.stream(ExplorationSite.values())
                .filter(site -> state().discoveryCycle(site) == campaign.state().cycle()).count();
        return "本輪調查 " + count + "/" + ExplorationSite.values().length;
    }

    private String factionSummary() {
        return java.util.Arrays.stream(Faction.values())
                .map(faction -> faction.display() + "「" + faction.relation(state().reputation(faction)) + "」")
                .reduce((left, right) -> left + "、" + right).orElse("");
    }

    private String masterySummary(Player player) {
        WeaponType weapon = ownedWeapon(player);
        return weapon == null ? "未攜帶登記兵器" : weapon.display() + "熟練 " + mastery(player, weapon);
    }

    private String conditionSummary() {
        long damaged = java.util.Arrays.stream(CityProject.values())
                .filter(project -> projectLevel(project) > 0 && projectCondition(project) < 100).count();
        long offline = java.util.Arrays.stream(CityProject.values())
                .filter(project -> projectLevel(project) > 0 && functionalProjectLevel(project) == 0).count();
        return damaged == 0 ? "所有已建設施狀況完整" : "受損 " + damaged + " 項，停擺 " + offline + " 項";
    }

    private String conditionStatus(CityProject project) {
        return ProjectConditionRules.status(projectLevel(project), projectCondition(project),
                plugin.getConfig().getInt("development.maintenance.offline-below", 30),
                plugin.getConfig().getInt("development.maintenance.full-effect-at", 60));
    }

    private void repair(Player player, CityProject project) {
        int built = projectLevel(project);
        int condition = projectCondition(project);
        if (built <= 0 || condition >= ProjectConditionRules.MAX_CONDITION) {
            player.sendMessage(EvilIslandPlugin.message(built <= 0 ? "這項設施尚未建設。" : "這項設施不需修復。"));
            openMaintenance(player);
            return;
        }
        Map<WorldResource, Integer> cost = repairCost(project);
        if (!canAfford(cost)) {
            player.sendMessage(EvilIslandPlugin.message("公共物資不足，需要「" + costText(cost) + "」。",
                    NamedTextColor.RED));
            openMaintenance(player);
            return;
        }
        EnumMap<WorldResource, Integer> resources = copyResources();
        cost.forEach((resource, amount) -> resources.put(resource,
                resources.getOrDefault(resource, 0) - amount));
        state = with(resources, null, null, null, null, null, timestamp());
        int repaired = repairedCondition(condition);
        setCondition(project, repaired);
        saveAsync();
        refreshVisuals();
        Bukkit.broadcast(EvilIslandPlugin.message(player.getName() + "調度物資修復" + project.display()
                + "，狀況恢復至 " + repaired + "%。", NamedTextColor.GREEN));
        openMaintenance(player);
    }

    private void setCondition(CityProject project, int value) {
        ProjectConditionSnapshot current = conditions.get(project);
        long updatedAt = Math.max(System.currentTimeMillis(), current == null ? 0 : current.updatedAt() + 1);
        ProjectConditionSnapshot snapshot = new ProjectConditionSnapshot(project, value, updatedAt);
        conditions.put(project, snapshot);
        database.submit(() -> repository.saveCondition(snapshot)).exceptionally(exception -> {
            plugin.getLogger().warning("Cannot save project condition: " + exception.getMessage());
            return null;
        });
    }

    private int repairedCondition(int condition) {
        return ProjectConditionRules.repairedCondition(condition,
                plugin.getConfig().getInt("development.maintenance.repair-amount", 25));
    }

    private Map<WorldResource, Integer> repairCost(CityProject project) {
        Map<WorldResource, Integer> defaults = ProjectConditionRules.repairCost(project);
        EnumMap<WorldResource, Integer> result = new EnumMap<>(WorldResource.class);
        defaults.forEach((resource, amount) -> {
            int configured = plugin.getConfig().getInt("development.maintenance.costs." + project.id()
                    + "." + resource.id(), amount);
            if (configured > 0) result.put(resource, configured);
        });
        return Map.copyOf(result);
    }

    private String chainSummary(EventChain chain) {
        int progress = state().chainProgress(chain);
        MissionType required = chain.requiredType(progress);
        return progress >= chain.stageCount() ? "本輪已完成。" : "進度 " + progress + "/" + chain.stageCount()
                + "，目前需要「" + required.display() + "」任務。";
    }

    private int completedProjectLevels() {
        return state.projects().values().stream().mapToInt(Integer::intValue).sum();
    }

    private int clampReputation(int value) {
        return Math.max(-100, Math.min(100, value));
    }

    private EnumMap<WorldResource, Integer> copyResources() {
        EnumMap<WorldResource, Integer> copy = new EnumMap<>(WorldResource.class);
        copy.putAll(state.resources());
        return copy;
    }

    private EnumMap<CityProject, Integer> copyProjects() {
        EnumMap<CityProject, Integer> copy = new EnumMap<>(CityProject.class);
        copy.putAll(state.projects());
        return copy;
    }

    private EnumMap<Faction, Integer> copyFactions() {
        EnumMap<Faction, Integer> copy = new EnumMap<>(Faction.class);
        copy.putAll(state.reputation());
        return copy;
    }

    private EnumMap<ExplorationSite, Integer> copyDiscoveries() {
        EnumMap<ExplorationSite, Integer> copy = new EnumMap<>(ExplorationSite.class);
        copy.putAll(state.discoveries());
        return copy;
    }

    private EnumMap<EventChain, Integer> copyChains() {
        EnumMap<EventChain, Integer> copy = new EnumMap<>(EventChain.class);
        copy.putAll(state.chains());
        return copy;
    }

    private WorldDevelopmentSnapshot with(Map<WorldResource, Integer> resources, Map<CityProject, Integer> projects,
                                          Map<Faction, Integer> factions,
                                          Map<ExplorationSite, Integer> discoveries,
                                          Map<EventChain, Integer> chains, String ending, long now) {
        return new WorldDevelopmentSnapshot(state.cycle(), resources == null ? state.resources() : resources,
                projects == null ? state.projects() : projects, factions == null ? state.reputation() : factions,
                discoveries == null ? state.discoveries() : discoveries,
                chains == null ? state.chains() : chains, ending == null ? state.lastEnding() : ending, now);
    }

    private void saveAsync() {
        WorldDevelopmentSnapshot snapshot = state;
        database.submit(() -> repository.saveWorld(snapshot)).exceptionally(exception -> {
            plugin.getLogger().warning("Cannot save development state: " + exception.getMessage());
            return null;
        });
    }

    private long timestamp() {
        return Math.max(System.currentTimeMillis(), state == null ? 0L : state.updatedAt() + 1L);
    }

    private Inventory create(HubHolder holder, String title) {
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text(title));
        holder.inventory = inventory;
        return inventory;
    }

    private void back(Inventory inventory) {
        inventory.setItem(26, item(Material.ARROW, "返回發展總覽", NamedTextColor.GREEN, List.of()));
    }

    private ItemStack item(Material material, String name, NamedTextColor color, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, color));
        meta.lore(lore.stream().map(line -> Component.text(line, NamedTextColor.GRAY)).toList());
        stack.setItemMeta(meta);
        return stack;
    }

    private enum Menu {
        HUB, ROUTES, ROUTE_CONFIRM, PROJECTS, PROJECT_CONFIRM, MAINTENANCE, MAINTENANCE_CONFIRM,
        EXPLORATION, EVENT, FACTIONS, TECHNIQUES
    }

    private static final class HubHolder implements InventoryHolder {
        private final Menu menu;
        private Object value;
        private Inventory inventory;

        private HubHolder(Menu menu, Object value) {
            this.menu = menu;
            this.value = value;
        }

        @Override
        public Inventory getInventory() { return inventory; }
    }
}
