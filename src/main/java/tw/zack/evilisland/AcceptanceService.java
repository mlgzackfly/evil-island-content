package tw.zack.evilisland;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.Chunk;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import tw.zack.evilisland.model.AcceptanceBlockSnapshot;
import tw.zack.evilisland.model.AcceptanceRunSnapshot;
import tw.zack.evilisland.model.AcceptanceState;
import tw.zack.evilisland.model.CityProject;
import tw.zack.evilisland.model.CityRoute;
import tw.zack.evilisland.model.CityRouteRules;
import tw.zack.evilisland.model.ConstructionPreviewBlock;
import tw.zack.evilisland.model.ConstructionPreviewPlan;
import tw.zack.evilisland.model.Faction;
import tw.zack.evilisland.model.FactionContract;
import tw.zack.evilisland.model.WorldResource;
import tw.zack.evilisland.model.ProjectConditionRules;
import tw.zack.evilisland.model.LivingEventApproach;
import tw.zack.evilisland.model.LivingEventArc;
import tw.zack.evilisland.model.LivingEventRules;
import tw.zack.evilisland.model.LivingEventSnapshot;
import tw.zack.evilisland.model.LivingEventState;
import tw.zack.evilisland.model.LivingEventType;
import tw.zack.evilisland.model.MissionContract;
import tw.zack.evilisland.model.SupplyRouteRules;
import tw.zack.evilisland.model.ResidentIntelRules;
import tw.zack.evilisland.model.ResidentRole;
import tw.zack.evilisland.model.BossVariant;
import tw.zack.evilisland.model.ExplorationSite;
import tw.zack.evilisland.model.RegionControlRules;
import tw.zack.evilisland.model.RegionState;
import tw.zack.evilisland.model.ExpeditionOperation;
import tw.zack.evilisland.model.ExpeditionOutcome;
import tw.zack.evilisland.model.ExpeditionPhase;
import tw.zack.evilisland.model.ExpeditionRoute;
import tw.zack.evilisland.model.ExpeditionRules;
import tw.zack.evilisland.model.ExpeditionDirector;
import tw.zack.evilisland.model.ExpeditionKit;
import tw.zack.evilisland.model.ExpeditionRouteEvent;
import tw.zack.evilisland.expedition.ExpeditionScenarioRegistry;
import tw.zack.evilisland.model.SpeciesType;
import tw.zack.evilisland.model.ExpeditionStoryChapter;
import tw.zack.evilisland.model.ExpeditionStoryChoice;
import tw.zack.evilisland.model.ExpeditionStoryProgressSnapshot;
import tw.zack.evilisland.model.ExpeditionStoryRules;
import tw.zack.evilisland.persistence.AcceptanceRepository;
import tw.zack.evilisland.world.WorldAtlasService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class AcceptanceService {
    private record Placement(Block block, BlockData data) { }

    private final EvilIslandPlugin plugin;
    private final AcceptanceRepository repository;
    private final ConstructionService construction;
    private final DiplomacyService diplomacy;
    private final DaoFieldService daoFields;
    private final WorldAtlasService atlas;
    private final CrisisSceneService crisisScenes;
    private final NamespacedKey entityKey;
    private final ArrayDeque<Placement> queue = new ArrayDeque<>();
    private final List<ConstructionPreviewPlan> plans = new ArrayList<>();
    private BukkitTask placementTask;
    private BukkitTask restoreTask;
    private AcceptanceRunSnapshot current;
    private CheckBook checks;
    private CommandSender requester;

    public AcceptanceService(EvilIslandPlugin plugin, AcceptanceRepository repository,
                             ConstructionService construction, DiplomacyService diplomacy,
                             DaoFieldService daoFields, WorldAtlasService atlas,
                             CrisisSceneService crisisScenes) {
        this.plugin = plugin;
        this.repository = repository;
        this.construction = construction;
        this.diplomacy = diplomacy;
        this.daoFields = daoFields;
        this.atlas = atlas;
        this.crisisScenes = crisisScenes;
        this.entityKey = new NamespacedKey(plugin, "acceptance_entity");
    }

    public void load() {
        Optional<AcceptanceRunSnapshot> active = repository.activeRun();
        if (active.isPresent()) {
            current = active.get();
            Bukkit.getScheduler().runTaskLater(plugin, () -> restoreInternal("伺服器啟動時自動復原"), 40L);
            return;
        }
        repository.latestRun().ifPresent(run -> Bukkit.getScheduler().runTaskLater(plugin, () -> {
            holdPreviewChunks(run);
            removeOrphanEnvoys(run);
            releasePreviewChunks(run);
        }, 40L));
    }

    public void run(CommandSender sender) {
        if ((current != null && current.state().active()) || placementTask != null) {
            sender.sendMessage(EvilIslandPlugin.message("已有驗收預覽進行中，請先查詢狀態或復原。",
                    NamedTextColor.RED));
            return;
        }
        Location city = daoFields.cityCenter();
        if (city == null || city.getWorld() == null) {
            sender.sendMessage(EvilIslandPlugin.message("新城座標尚未完成設定，無法建立驗收預覽。",
                    NamedTextColor.RED));
            return;
        }
        clearTasks();
        plans.clear();
        queue.clear();
        checks = new CheckBook();
        requester = sender;
        long now = System.currentTimeMillis();
        UUID runId = UUID.randomUUID();
        Location previewCenter = ground(city.clone().add(0, 0, 205.0 * atlas.coordinateScale()));
        current = new AcceptanceRunSnapshot(runId, AcceptanceState.PREPARING, city.getWorld().getName(),
                previewCenter.getBlockX(), previewCenter.getBlockY(), previewCenter.getBlockZ(),
                0, 0, "正在準備驗收預覽", now, now);
        repository.saveRun(current);

        try {
            validateRoutes();
            validateProjectConditions();
            validateLivingEvents();
            validateExpeditions();
            Set<String> reserved = new HashSet<>();
            for (CityProject project : CityProject.values()) {
                validateBlueprint(project);
                Optional<ConstructionPreviewPlan> plan = construction.planAcceptance(project, 3, reserved);
                checks.check(project.display() + "取得安全預覽地塊", plan.isPresent());
                if (plan.isEmpty()) continue;
                plans.add(plan.get());
                reserve(plan.get(), reserved);
            }
            if (plans.size() != CityProject.values().length) {
                failBeforePlacement("至少一項工程找不到安全預覽地塊，未放置任何方塊。");
                return;
            }
            List<AcceptanceBlockSnapshot> snapshots = snapshotBlocks(runId, plans);
            checks.check("所有預覽方塊位置互不重疊", uniquePositions(snapshots));
            checks.check("方塊快照已在施工前建立", !snapshots.isEmpty());
            repository.saveBlocks(snapshots);
            holdPreviewChunks(current);
            removeOrphanEnvoys(current);
            spawnEnvoys(previewCenter, runId);
            for (AcceptanceBlockSnapshot snapshot : snapshots) {
                World world = Bukkit.getWorld(snapshot.world());
                if (world != null) queue.addLast(new Placement(world.getBlockAt(snapshot.x(), snapshot.y(), snapshot.z()),
                        Bukkit.createBlockData(snapshot.placedData())));
            }
            sender.sendMessage(EvilIslandPlugin.message("自動驗收已建立快照，開始分批放置 " + queue.size()
                    + " 個預覽方塊。", NamedTextColor.AQUA));
            startPlacement();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Cannot run automated acceptance", exception);
            failAndRestore("驗收發生例外：" + exception.getClass().getSimpleName());
        }
    }

    public void status(CommandSender sender) {
        AcceptanceRunSnapshot run = current;
        if (run == null) run = repository.latestRun().orElse(null);
        if (run == null) {
            sender.sendMessage(EvilIslandPlugin.message("尚未執行自動化驗收。"));
            return;
        }
        sender.sendMessage(EvilIslandPlugin.message("驗收狀態：" + stateDisplay(run.state()) + "　"
                + run.checksPassed() + "/" + run.checksTotal(), run.state() == AcceptanceState.PREVIEW
                ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("預覽中心：" + run.world() + " X " + run.centerX() + " Y "
                + run.centerY() + " Z " + run.centerZ(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("報告：" + reportDirectory().resolve("latest-report.md"),
                NamedTextColor.GRAY));
    }

    public void restore(CommandSender sender) {
        requester = sender;
        if (current == null || !current.state().active()) {
            Optional<AcceptanceRunSnapshot> active = repository.activeRun();
            if (active.isEmpty()) {
                sender.sendMessage(EvilIslandPlugin.message("目前沒有需要復原的驗收預覽。"));
                return;
            }
            current = active.get();
        }
        restoreInternal("管理員手動復原");
    }

    public void shutdown() {
        if (current != null && current.state().active()) restoreInternal("伺服器關閉前自動復原");
        clearTasks();
    }

    public int runSelfTest() {
        int result = 0;
        if (AcceptanceState.PREVIEW.active()) result++;
        if (!AcceptanceState.RESTORED.active()) result++;
        if (construction.blueprintSize(CityProject.WALLS, 3)
                > construction.blueprintSize(CityProject.WALLS, 1)) result++;
        if (FactionContract.forWeek(1, CityRoute.FORTRESS) == FactionContract.QUANRONG_HUNT) result++;
        if (ProjectConditionRules.functionalLevel(3, 59) == 2) result++;
        if (ProjectConditionRules.repairedCondition(90) == 100) result++;
        if (LivingEventType.values().length == 12 && LivingEventArc.values().length == 4) result++;
        if (LivingEventRules.missionBoard(List.of(MissionContract.EAST_CLEARANCE,
                MissionContract.TIMBER_REQUISITION, MissionContract.NORTH_RIDGE_OBSERVATION),
                livingSnapshot(LivingEventType.WIND_RAID, LivingEventState.ACTIVE)).contains(
                LivingEventType.WIND_RAID.contract())) result++;
        if (java.util.Arrays.stream(LivingEventType.values())
                .allMatch(type -> crisisScenes.blueprintSize(type) >= 18)) result++;
        if (java.util.Arrays.stream(LivingEventType.values()).map(crisisScenes::blueprintSignature)
                .distinct().count() == LivingEventType.values().length) result++;
        if (java.util.Arrays.stream(LivingEventType.values()).allMatch(crisisScenes::outcomesDiffer)) result++;
        if (SupplyRouteRules.discountedCost(Map.of(WorldResource.TIMBER, 4), 0.75)
                .get(WorldResource.TIMBER) == 3) result++;
        if (SupplyRouteRules.arrivalTime(1_000L, 30) == 1_801_000L) result++;
        if (java.util.Arrays.stream(LivingEventType.values()).allMatch(type -> java.util.Arrays.stream(
                ResidentRole.values()).filter(role -> ResidentIntelRules.truthful(type, role)).count() == 4)) result++;
        if (ResidentRole.values().length == 6) result++;
        if (BossVariant.SIEGE_BREAKER.slamRadiusMultiplier() > 1.0) result++;
        if (BossVariant.SUPPLY_RAIDER.commandRadiusMultiplier() > 1.0) result++;
        if (BossVariant.HUNTED_COMMANDER.chargeCooldownMultiplier() < 1.0) result++;
        if (ExplorationSite.values().length == 5) result++;
        if (RegionControlRules.stateAfter(RegionState.TENSE, 20) == RegionState.LOST) result++;
        if (RegionControlRules.stateAfter(RegionState.LOST, 40) == RegionState.RECOVERING) result++;
        if (RegionControlRules.stateAfter(RegionState.RECOVERING, 70) == RegionState.STABLE) result++;
        if (ExpeditionRoute.values().length == 3 && ExpeditionOperation.values().length == 12) result++;
        if (ExpeditionRules.requiredClues(ExpeditionRoute.RIVERBED) == 3) result++;
        if (ExpeditionRules.syncWindowMillis(ExpeditionOperation.SUPPLY_NODE_SABOTAGE,
                ExpeditionRoute.RIDGE) == 20_000L) result++;
        if (ExpeditionRules.enemyCount(ExpeditionOperation.BLOCKADE_INFILTRATION,
                ExpeditionRoute.RIDGE, 2, 1) == 8) result++;
        if (ExpeditionRules.withdrawalOutcome(ExpeditionPhase.OBJECTIVE, 2, 0)
                == ExpeditionOutcome.PARTIAL) result++;
        if (ExpeditionRules.regionDelta(ExpeditionOutcome.COMPLETE, 2) == 9) result++;
        int twoKits = ExpeditionKit.MEDICAL.mask() | ExpeditionKit.SCOUTING.mask();
        int threeKits = twoKits | ExpeditionKit.PROVISIONS.mask();
        if (ExpeditionDirector.validLoadout(twoKits, 2)) result++;
        if (ExpeditionDirector.validLoadout(threeKits, 1)
                && !ExpeditionDirector.validLoadout(threeKits, 2)) result++;
        if (ExpeditionDirector.event(91L, 0) != ExpeditionDirector.event(91L, 1)) result++;
        if (ExpeditionDirector.resolve(ExpeditionRouteEvent.ENEMY_PATROL, false).alertDelta() == 1) result++;
        if (ExpeditionDirector.resolve(ExpeditionRouteEvent.WOUNDED_SCOUT, true).scoreDelta() == 2) result++;
        if (java.util.Arrays.stream(ExpeditionOperation.values())
                .filter(operation -> operation.site() == ExplorationSite.EASTERN_ROUTE)
                .map(ExpeditionDirector::preferredKit).distinct().count() == 4) result++;
        if (java.util.Arrays.stream(ExplorationSite.values()).allMatch(site ->
                java.util.Arrays.stream(ExpeditionOperation.values()).anyMatch(operation -> operation.site() == site))) {
            result++;
        }
        var scenarios = ExpeditionScenarioRegistry.standard();
        if (java.util.Arrays.stream(ExplorationSite.values()).map(site -> scenarios.forSite(site).boardTitle())
                .distinct().count() == ExplorationSite.values().length) result++;
        if (scenarios.forSite(ExplorationSite.UDING_WALL).requiredClues(ExpeditionOperation.CLIFF_RELAY,
                ExpeditionRoute.OLD_ROAD, null) == 3
                && scenarios.forSite(ExplorationSite.WESTERN_TRACE).requiredClues(
                ExpeditionOperation.RUIN_MAPPING, ExpeditionRoute.OLD_ROAD, null) == 2) result++;
        if (!scenarios.forSite(ExplorationSite.RONGXU_APPROACH).combatRequired()) result++;
        if (scenarios.forSite(ExplorationSite.UDING_WALL).enemy(0) == SpeciesType.QUANRONG_HUNTER
                && scenarios.forSite(ExplorationSite.DRAGON_COAST).enemy(0) == SpeciesType.YUJIANG_RAIDER) {
            result++;
        }
        if (scenarios.forSite(ExplorationSite.DRAGON_COAST).timedExtraction(
                ExpeditionOperation.TIDE_OBSERVATION)) result++;
        if (ExpeditionStoryChapter.values().length == 15) result++;
        if (java.util.Arrays.stream(ExpeditionStoryChapter.values()).map(ExpeditionStoryChapter::title)
                .distinct().count() == 15) result++;
        if (java.util.Arrays.stream(ExpeditionStoryChapter.values()).allMatch(chapter ->
                java.util.stream.IntStream.range(0, 3).mapToObj(chapter::discovery).distinct().count() == 3)) result++;
        if (java.util.Arrays.stream(ExpeditionStoryChapter.values()).allMatch(chapter ->
                !chapter.result(ExpeditionStoryChoice.SECURE)
                        .equals(chapter.result(ExpeditionStoryChoice.CONNECT)))) result++;
        ExpeditionStoryProgressSnapshot storyProgress = ExpeditionStoryProgressSnapshot.initial(
                ExplorationSite.EASTERN_ROUTE);
        storyProgress = ExpeditionStoryRules.advance(storyProgress, ExpeditionStoryChoice.SECURE, 1, 1, 10L);
        if (storyProgress.chapter() == 2 && !ExpeditionStoryRules.canAdvance(storyProgress, 2, 1, 1)
                && ExpeditionStoryRules.canAdvance(storyProgress, 2, 1, 2)) result++;
        return result;
    }

    private void validateRoutes() {
        checks.check("城市路線共三條", CityRoute.values().length == 3);
        for (CityRoute route : CityRoute.values()) {
            Set<FactionContract> contracts = EnumSet.noneOf(FactionContract.class);
            for (int week = 1; week <= 4; week++) contracts.add(FactionContract.forWeek(week, route));
            checks.check(route.display() + "四週契約不重複", contracts.size() == 4);
            boolean discounted = route == CityRoute.FORTRESS
                    ? discounted(CityProject.WALLS, route) && discounted(CityProject.AIR_DEFENSE, route)
                    : route == CityRoute.EXPEDITION
                    ? discounted(CityProject.SCOUT_POST, route) && discounted(CityProject.WORKSHOP, route)
                    : discounted(CityProject.QI_MIRROR, route);
            checks.check(route.display() + "指定工程成本降低", discounted);
        }
        checks.check("固城路線降低守城入口壓力", CityRouteRules.defenseModifier(CityRoute.FORTRESS) == -1);
        checks.check("遠征路線降低輕疾部署門檻",
                CityRouteRules.deploymentScoutRequirement(CityRoute.EXPEDITION) == 1);
        checks.check("聚炁民生提高城內炁息恢復", CityRouteRules.cityQiBonus(CityRoute.QI_CIVIC) == 1);
    }

    private boolean discounted(CityProject project, CityRoute route) {
        Map<WorldResource, Integer> base = project.costForLevel(2);
        Map<WorldResource, Integer> cost = CityRouteRules.projectCost(project, 2, route);
        return base.entrySet().stream().anyMatch(entry -> cost.getOrDefault(entry.getKey(), entry.getValue())
                < entry.getValue());
    }

    private void validateProjectConditions() {
        checks.check("完整設施維持全部建設效益", ProjectConditionRules.functionalLevel(3, 100) == 3);
        checks.check("中度受損設施只下降一階效益", ProjectConditionRules.functionalLevel(3, 59) == 2);
        checks.check("嚴重受損設施停止提供效益", ProjectConditionRules.functionalLevel(3, 29) == 0);
        checks.check("修復不會超過完整狀況", ProjectConditionRules.repairedCondition(90) == 100);
        checks.check("守城失敗同時損傷外牆與當週設施",
                ProjectConditionRules.defenseFailureDamage(3, 3).size() == 2
                        && ProjectConditionRules.defenseFailureDamage(3, 3).containsKey(CityProject.WALLS)
                        && ProjectConditionRules.defenseFailureDamage(3, 3).containsKey(CityProject.AIR_DEFENSE));
    }

    private void validateLivingEvents() {
        checks.check("動態危機包含十二種事件與四條脈絡",
                LivingEventType.values().length == 12 && LivingEventArc.values().length == 4);
        LivingEventType selected = LivingEventRules.select(2, 3, 4, List.of());
        checks.check("事件導演只選擇當週可用危機", selected.availableInWeek(3));
        LivingEventType different = LivingEventRules.select(2, 3, 4, List.of(selected));
        checks.check("事件導演避開近期同一危機", different != selected);
        LivingEventSnapshot active = livingSnapshot(LivingEventType.WIND_RAID, LivingEventState.ACTIVE);
        List<MissionContract> board = LivingEventRules.missionBoard(List.of(MissionContract.EAST_CLEARANCE,
                MissionContract.TIMBER_REQUISITION, MissionContract.NORTH_RIDGE_OBSERVATION), active);
        checks.check("危機任務會插入三項任務公告", board.size() == 3
                && board.contains(LivingEventType.WIND_RAID.contract()));
        List<LivingEventSnapshot> history = List.of(
                livingSnapshot(LivingEventType.TIDAL_WARNING, LivingEventState.EXPIRED),
                livingSnapshot(LivingEventType.WIND_RAID, LivingEventState.EXPIRED));
        checks.check("未處理事件會累積有限區域壓力",
                LivingEventRules.regionPressure(history, LivingEventType.WIND_RAID.region(), 2) == 2);
        checks.check("危機壓力只強化指定任務",
                LivingEventRules.missionEnemyModifier(active, active.type().contract(), 2) == 2
                        && LivingEventRules.missionEnemyModifier(active, MissionContract.EAST_CLEARANCE, 2) == 0);
        checks.check("十二種危機都有可辨識的實體現場", java.util.Arrays.stream(LivingEventType.values())
                .allMatch(type -> crisisScenes.blueprintSize(type) >= 18));
        checks.check("十二種危機現場藍圖互不相同", java.util.Arrays.stream(LivingEventType.values())
                .map(crisisScenes::blueprintSignature).distinct().count() == LivingEventType.values().length);
        checks.check("危機成功與逾期會留下不同痕跡", java.util.Arrays.stream(LivingEventType.values())
                .allMatch(crisisScenes::outcomesDiffer));
        checks.check("延遲補給成本低於即時調度",
                SupplyRouteRules.discountedCost(Map.of(WorldResource.TIMBER, 4), 0.75)
                        .get(WorldResource.TIMBER) == 3);
        checks.check("補給路線至少經過一分鐘才抵達",
                SupplyRouteRules.arrivalTime(1_000L, 0) == 61_000L);
        checks.check("每件危機固定有四名可信與兩名矛盾來源",
                java.util.Arrays.stream(LivingEventType.values()).allMatch(type -> java.util.Arrays.stream(
                        ResidentRole.values()).filter(role -> ResidentIntelRules.truthful(type, role)).count() == 4));
        checks.check("矛盾消息不會指向真正危機區域",
                java.util.Arrays.stream(LivingEventType.values()).allMatch(type -> java.util.Arrays.stream(
                        ResidentRole.values()).filter(role -> !ResidentIntelRules.truthful(type, role)).allMatch(
                        role -> ResidentIntelRules.claimedRegion(type, role) != type.region())));
        checks.check("破陣刑天擴大震地範圍", BossVariant.SIEGE_BREAKER.slamRadiusMultiplier() > 1.0);
        checks.check("劫糧刑天擴大統軍範圍", BossVariant.SUPPLY_RAIDER.commandRadiusMultiplier() > 1.0);
        checks.check("負創刑天縮短衝鋒冷卻", BossVariant.HUNTED_COMMANDER.chargeCooldownMultiplier() < 1.0);
        checks.check("五個探索區域都有獨立控制狀態", ExplorationSite.values().length == 5);
        checks.check("低穩定度會使區域失守",
                RegionControlRules.stateAfter(RegionState.TENSE, 20) == RegionState.LOST);
        checks.check("失守區域必須經過收復階段",
                RegionControlRules.stateAfter(RegionState.LOST, 40) == RegionState.RECOVERING);
        checks.check("收復區域達標後才恢復安定",
                RegionControlRules.stateAfter(RegionState.RECOVERING, 70) == RegionState.STABLE);
    }

    private void validateExpeditions() {
        checks.check("五區深入遠征共用三路線骨架與十二種行動",
                ExpeditionRoute.values().length == 3 && ExpeditionOperation.values().length == 12);
        checks.check("乾涸河道需要較完整的現場情報",
                ExpeditionRules.requiredClues(ExpeditionRoute.RIVERBED)
                        > ExpeditionRules.requiredClues(ExpeditionRoute.OLD_ROAD));
        checks.check("稜線視野延長同步目標時限",
                ExpeditionRules.syncWindowMillis(ExpeditionOperation.SUPPLY_NODE_SABOTAGE,
                        ExpeditionRoute.RIDGE)
                        > ExpeditionRules.syncWindowMillis(ExpeditionOperation.SUPPLY_NODE_SABOTAGE,
                        ExpeditionRoute.OLD_ROAD));
        checks.check("雙人及提高警戒會增加敵襲壓力",
                ExpeditionRules.enemyCount(ExpeditionOperation.BLOCKADE_INFILTRATION,
                        ExpeditionRoute.RIDGE, 2, 1)
                        > ExpeditionRules.enemyCount(ExpeditionOperation.BLOCKADE_INFILTRATION,
                        ExpeditionRoute.RIDGE, 1, 0));
        checks.check("取得情報後撤離仍保留部分成果",
                ExpeditionRules.withdrawalOutcome(ExpeditionPhase.INVESTIGATING, 2, 0)
                        == ExpeditionOutcome.PARTIAL);
        checks.check("完整雙人遠征只略增區域成果，不增加永久戰力",
                ExpeditionRules.regionDelta(ExpeditionOutcome.COMPLETE, 2)
                        == ExpeditionRules.regionDelta(ExpeditionOutcome.COMPLETE, 1) + 1);
        int twoKits = ExpeditionKit.MEDICAL.mask() | ExpeditionKit.SCOUTING.mask();
        int threeKits = twoKits | ExpeditionKit.PROVISIONS.mask();
        checks.check("雙人遠征只能攜帶兩項整備", ExpeditionDirector.validLoadout(twoKits, 2)
                && !ExpeditionDirector.validLoadout(threeKits, 2));
        checks.check("單人可多帶一項整備補足操作壓力", ExpeditionDirector.validLoadout(threeKits, 1));
        checks.check("同場兩段途中狀況不重複", ExpeditionDirector.event(91L, 0)
                != ExpeditionDirector.event(91L, 1));
        checks.check("缺乏偵察器材處理巡邏會提高警戒",
                ExpeditionDirector.resolve(ExpeditionRouteEvent.ENEMY_PATROL, false).alertDelta() == 1);
        checks.check("醫療包處理傷員會保留額外成果",
                ExpeditionDirector.resolve(ExpeditionRouteEvent.WOUNDED_SCOUT, true).scoreDelta() == 2);
        checks.check("東境四種行動各有不同建議整備", java.util.Arrays.stream(ExpeditionOperation.values())
                .filter(operation -> operation.site() == ExplorationSite.EASTERN_ROUTE)
                .map(ExpeditionDirector::preferredKit).distinct().count() == 4);
        checks.check("五個區域都有專屬遠征行動", java.util.Arrays.stream(ExplorationSite.values())
                .allMatch(site -> java.util.Arrays.stream(ExpeditionOperation.values())
                        .anyMatch(operation -> operation.site() == site)));
        var scenarios = ExpeditionScenarioRegistry.standard();
        checks.check("五座營地使用不同遠征公告", java.util.Arrays.stream(ExplorationSite.values())
                .map(site -> scenarios.forSite(site).boardTitle()).distinct().count() == ExplorationSite.values().length);
        checks.check("宇定要求完整觀測而西方只能帶回兩份證據",
                scenarios.forSite(ExplorationSite.UDING_WALL).requiredClues(ExpeditionOperation.CLIFF_RELAY,
                        ExpeditionRoute.OLD_ROAD, null) == 3
                        && scenarios.forSite(ExplorationSite.WESTERN_TRACE).requiredClues(
                        ExpeditionOperation.RUIN_MAPPING, ExpeditionRoute.OLD_ROAD, null) == 2);
        checks.check("絨須邊界行動不生成清剿階段敵軍",
                !scenarios.forSite(ExplorationSite.RONGXU_APPROACH).combatRequired());
        checks.check("宇定犬戎與龍宮禺彊使用不同敵軍生態",
                scenarios.forSite(ExplorationSite.UDING_WALL).enemy(0) == SpeciesType.QUANRONG_HUNTER
                        && scenarios.forSite(ExplorationSite.DRAGON_COAST).enemy(0)
                        == SpeciesType.YUJIANG_RAIDER);
        checks.check("龍宮海岸完成目標後仍有潮路撤離時限",
                scenarios.forSite(ExplorationSite.DRAGON_COAST).timedExtraction(
                        ExpeditionOperation.TIDE_OBSERVATION));
        checks.check("五區各有三章遠征故事", ExpeditionStoryChapter.values().length == 15
                && java.util.Arrays.stream(ExplorationSite.values()).allMatch(site ->
                java.util.stream.IntStream.rangeClosed(1, 3).allMatch(chapter ->
                        ExpeditionStoryChapter.forSite(site, chapter).site() == site)));
        checks.check("十五章使用不同章名", java.util.Arrays.stream(ExpeditionStoryChapter.values())
                .map(ExpeditionStoryChapter::title).distinct().count() == 15);
        checks.check("每章三項現場發現不重複", java.util.Arrays.stream(ExpeditionStoryChapter.values())
                .allMatch(chapter -> java.util.stream.IntStream.range(0, 3).mapToObj(chapter::discovery)
                        .distinct().count() == 3));
        checks.check("每章兩種回營主張產生不同敘事結果",
                java.util.Arrays.stream(ExpeditionStoryChapter.values()).allMatch(chapter ->
                        !chapter.result(ExpeditionStoryChoice.SECURE)
                                .equals(chapter.result(ExpeditionStoryChoice.CONNECT))));
        ExpeditionStoryProgressSnapshot progress = ExpeditionStoryRules.advance(
                ExpeditionStoryProgressSnapshot.initial(ExplorationSite.EASTERN_ROUTE),
                ExpeditionStoryChoice.SECURE, 1, 1, 10L);
        checks.check("同區每週最多推進一章故事", progress.chapter() == 2
                && !ExpeditionStoryRules.canAdvance(progress, 2, 1, 1)
                && ExpeditionStoryRules.canAdvance(progress, 2, 1, 2));
    }

    private LivingEventSnapshot livingSnapshot(LivingEventType type, LivingEventState state) {
        long resolved = state == LivingEventState.ACTIVE ? 0L : 20L;
        return new LivingEventSnapshot(UUID.nameUUIDFromBytes((type.id() + state.id()).getBytes(StandardCharsets.UTF_8)),
                type, state, state == LivingEventState.RESOLVED
                ? LivingEventApproach.FIELD : LivingEventApproach.NONE,
                2, 3, 4, 10L, 13L, state == LivingEventState.RESOLVED ? 1 : 0,
                10L, resolved, 20L);
    }

    private void validateBlueprint(CityProject project) {
        int first = construction.blueprintSize(project, 1);
        int second = construction.blueprintSize(project, 2);
        int third = construction.blueprintSize(project, 3);
        checks.check(project.display() + "三級藍圖逐階擴張", first > 0 && second > first && third > second);
    }

    private void reserve(ConstructionPreviewPlan plan, Set<String> reserved) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                reserved.add(ConstructionService.columnKey(plan.centerX() + dx, plan.centerZ() + dz));
            }
        }
    }

    private List<AcceptanceBlockSnapshot> snapshotBlocks(UUID runId, List<ConstructionPreviewPlan> values) {
        List<AcceptanceBlockSnapshot> snapshots = new ArrayList<>();
        for (ConstructionPreviewPlan plan : values) {
            World world = Bukkit.getWorld(plan.world());
            if (world == null) continue;
            for (ConstructionPreviewBlock block : plan.blocks()) {
                Block target = world.getBlockAt(block.x(), block.y(), block.z());
                snapshots.add(new AcceptanceBlockSnapshot(runId, world.getName(), block.x(), block.y(), block.z(),
                        target.getBlockData().getAsString(), block.placedData()));
            }
        }
        return snapshots;
    }

    private boolean uniquePositions(List<AcceptanceBlockSnapshot> snapshots) {
        Set<String> positions = new HashSet<>();
        for (AcceptanceBlockSnapshot block : snapshots) {
            if (!positions.add(block.world() + ":" + block.x() + ":" + block.y() + ":" + block.z())) return false;
        }
        return true;
    }

    private void spawnEnvoys(Location center, UUID runId) {
        Faction[] factions = {Faction.QUANRONG, Faction.MAO, Faction.NAJIN, Faction.QIULONG};
        for (int index = 0; index < factions.length; index++) {
            Location position = center.clone().add((index - 1.5) * 5.0, 0, 0);
            Mob envoy = diplomacy.spawnAcceptanceEnvoy(factions[index], position);
            envoy.getPersistentDataContainer().set(entityKey, PersistentDataType.STRING, runId.toString());
        }
    }

    private void startPlacement() {
        int limit = Math.max(20, plugin.getConfig().getInt("acceptance.blocks-per-tick", 120));
        placementTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int count = 0;
            while (count++ < limit && !queue.isEmpty()) {
                Placement placement = queue.removeFirst();
                placement.block().setBlockData(placement.data(), false);
            }
            if (!queue.isEmpty()) return;
            placementTask.cancel();
            placementTask = null;
            finishPreview();
        }, 1L, 1L);
    }

    private void finishPreview() {
        List<AcceptanceBlockSnapshot> snapshots = repository.loadBlocks(current.id());
        boolean blocksMatch = snapshots.stream().allMatch(snapshot -> {
            World world = Bukkit.getWorld(snapshot.world());
            return world != null && world.getBlockAt(snapshot.x(), snapshot.y(), snapshot.z())
                    .getBlockData().getAsString().equals(snapshot.placedData());
        });
        checks.check("預覽方塊與快照一致", blocksMatch);
        for (Faction faction : List.of(Faction.QUANRONG, Faction.MAO, Faction.NAJIN, Faction.QIULONG)) {
            checks.check(faction.display() + "驗收使者已生成", acceptanceEntities(current.id()).stream()
                    .anyMatch(entity -> entity.getType() == envoyType(faction)));
        }
        long now = System.currentTimeMillis();
        boolean passed = checks.passed == checks.total;
        AcceptanceState state = passed ? AcceptanceState.PREVIEW : AcceptanceState.PREPARING;
        current = new AcceptanceRunSnapshot(current.id(), state, current.world(), current.centerX(), current.centerY(),
                current.centerZ(), checks.passed, checks.total,
                passed ? "所有自動檢查通過，等待瀏覽器檢視。" : "自動檢查未全部通過，正在復原。",
                current.startedAt(), now);
        repository.saveRun(current);
        writeReport(current, "預覽已建立", 0, snapshots.size());
        CommandSender sender = requester == null ? Bukkit.getConsoleSender() : requester;
        sender.sendMessage(EvilIslandPlugin.message("自動驗收完成：" + checks.passed + "/" + checks.total,
                state == AcceptanceState.PREVIEW ? NamedTextColor.GREEN : NamedTextColor.RED));
        sender.sendMessage(Component.text(dynmapUrl(current), NamedTextColor.AQUA));
        sender.sendMessage(Component.text("報告：" + reportDirectory().resolve("latest-report.md"), NamedTextColor.GRAY));
        if (!passed) {
            restoreInternal("驗收未全數通過，自動復原");
            return;
        }
        long ticks = Math.max(200L, plugin.getConfig().getLong("acceptance.preview-ticks", 12000L));
        restoreTask = Bukkit.getScheduler().runTaskLater(plugin, () -> restoreInternal("預覽逾時自動復原"), ticks);
    }

    private org.bukkit.entity.EntityType envoyType(Faction faction) {
        return switch (faction) {
            case QUANRONG -> org.bukkit.entity.EntityType.PILLAGER;
            case MAO -> org.bukkit.entity.EntityType.VILLAGER;
            case NAJIN -> org.bukkit.entity.EntityType.WANDERING_TRADER;
            case QIULONG -> org.bukkit.entity.EntityType.DROWNED;
            default -> org.bukkit.entity.EntityType.VILLAGER;
        };
    }

    private List<Entity> acceptanceEntities(UUID runId) {
        String marker = runId.toString();
        List<Entity> result = new ArrayList<>();
        if (current == null) return result;
        holdPreviewChunks(current);
        World world = Bukkit.getWorld(current.world());
        if (world == null) return result;
        Location center = new Location(world, current.centerX() + 0.5, current.centerY(), current.centerZ() + 0.5);
        for (Entity entity : world.getNearbyEntities(center, 48, 32, 48)) {
            if (marker.equals(entity.getPersistentDataContainer().get(entityKey, PersistentDataType.STRING))) {
                result.add(entity);
            }
        }
        return result;
    }

    private void failBeforePlacement(String reason) {
        long now = System.currentTimeMillis();
        current = new AcceptanceRunSnapshot(current.id(), AcceptanceState.FAILED, current.world(), current.centerX(),
                current.centerY(), current.centerZ(), checks.passed, checks.total, reason, current.startedAt(), now);
        repository.saveRun(current);
        repository.pruneCompletedRuns(plugin.getConfig().getInt("acceptance.retained-runs", 5));
        writeReport(current, reason, 0, 0);
        requester.sendMessage(EvilIslandPlugin.message(reason, NamedTextColor.RED));
    }

    private void failAndRestore(String reason) {
        if (current == null) return;
        current = new AcceptanceRunSnapshot(current.id(), AcceptanceState.PREPARING, current.world(),
                current.centerX(), current.centerY(), current.centerZ(), checks == null ? 0 : checks.passed,
                checks == null ? 0 : checks.total, reason, current.startedAt(), System.currentTimeMillis());
        repository.saveRun(current);
        restoreInternal(reason);
    }

    private void restoreInternal(String reason) {
        if (current == null) return;
        clearTasks();
        queue.clear();
        List<AcceptanceBlockSnapshot> blocks = repository.loadBlocks(current.id());
        int restored = 0;
        int conflicts = 0;
        for (AcceptanceBlockSnapshot snapshot : blocks) {
            World world = Bukkit.getWorld(snapshot.world());
            if (world == null) {
                conflicts++;
                continue;
            }
            Block block = world.getBlockAt(snapshot.x(), snapshot.y(), snapshot.z());
            String currentData = block.getBlockData().getAsString();
            if (currentData.equals(snapshot.placedData())) {
                block.setBlockData(Bukkit.createBlockData(snapshot.originalData()), false);
                restored++;
            } else if (currentData.equals(snapshot.originalData())) {
                restored++;
            } else {
                conflicts++;
            }
        }
        List<Entity> previewEntities = acceptanceEntities(current.id());
        int entities = previewEntities.size();
        previewEntities.forEach(Entity::remove);
        releasePreviewChunks(current);
        AcceptanceState state = conflicts == 0 ? AcceptanceState.RESTORED : AcceptanceState.FAILED;
        current = new AcceptanceRunSnapshot(current.id(), state, current.world(), current.centerX(), current.centerY(),
                current.centerZ(), current.checksPassed(), current.checksTotal(), reason + "；復原方塊 " + restored
                + "，衝突 " + conflicts + "，移除使者 " + entities + "。", current.startedAt(),
                System.currentTimeMillis());
        repository.saveRun(current);
        repository.pruneCompletedRuns(plugin.getConfig().getInt("acceptance.retained-runs", 5));
        writeReport(current, reason, restored, blocks.size());
        CommandSender sender = requester == null ? Bukkit.getConsoleSender() : requester;
        sender.sendMessage(EvilIslandPlugin.message("驗收預覽已復原：方塊 " + restored + "/" + blocks.size()
                + "，衝突 " + conflicts + "，使者 " + entities + "。",
                conflicts == 0 ? NamedTextColor.GREEN : NamedTextColor.RED));
    }

    private void clearTasks() {
        if (placementTask != null) placementTask.cancel();
        if (restoreTask != null) restoreTask.cancel();
        placementTask = null;
        restoreTask = null;
    }

    private void holdPreviewChunks(AcceptanceRunSnapshot run) {
        World world = Bukkit.getWorld(run.world());
        if (world == null) return;
        int centerChunkX = run.centerX() >> 4;
        int centerChunkZ = run.centerZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Chunk chunk = world.getChunkAt(centerChunkX + dx, centerChunkZ + dz);
                chunk.load(true);
                if (plugin.isEnabled()) chunk.addPluginChunkTicket(plugin);
            }
        }
    }

    private void releasePreviewChunks(AcceptanceRunSnapshot run) {
        if (!plugin.isEnabled()) return;
        World world = Bukkit.getWorld(run.world());
        if (world == null) return;
        int centerChunkX = run.centerX() >> 4;
        int centerChunkZ = run.centerZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.getChunkAt(centerChunkX + dx, centerChunkZ + dz).removePluginChunkTicket(plugin);
            }
        }
    }

    private void removeOrphanEnvoys(AcceptanceRunSnapshot run) {
        World world = Bukkit.getWorld(run.world());
        if (world == null) return;
        Location center = new Location(world, run.centerX() + 0.5, run.centerY(), run.centerZ() + 0.5);
        for (Entity entity : new ArrayList<>(world.getNearbyEntities(center, 48, 32, 48))) {
            if (entity.getPersistentDataContainer().has(entityKey, PersistentDataType.STRING)) entity.remove();
        }
    }

    private void writeReport(AcceptanceRunSnapshot run, String phase, int restored, int snapshotTotal) {
        try {
            Files.createDirectories(reportDirectory());
            StringBuilder report = new StringBuilder();
            report.append("# 噩盡島插件自動化驗收報告\n\n")
                    .append("- 執行識別：`").append(run.id()).append("`\n")
                    .append("- 狀態：").append(stateDisplay(run.state())).append("\n")
                    .append("- 階段：").append(phase).append("\n")
                    .append("- 自動檢查：").append(run.checksPassed()).append("/").append(run.checksTotal()).append("\n")
                    .append("- 預覽中心：`").append(run.world()).append(" ").append(run.centerX()).append(" ")
                    .append(run.centerY()).append(" ").append(run.centerZ()).append("`\n")
                    .append("- Dynmap：").append(dynmapUrl(run)).append("\n")
                    .append("- 摘要：").append(run.summary()).append("\n\n");
            if (checks != null) {
                report.append("## 檢查明細\n\n");
                checks.results.forEach((label, passed) -> report.append("- [")
                        .append(passed ? "x" : " ").append("] ").append(label).append("\n"));
                report.append("\n");
            }
            if (!plans.isEmpty()) {
                report.append("## 工程預覽座標\n\n")
                        .append("| 工程 | 階段 | 中心座標 | 方塊數 |\n")
                        .append("|---|---:|---|---:|\n");
                for (ConstructionPreviewPlan plan : plans) report.append("| ").append(plan.project().display())
                        .append(" | ").append(plan.level()).append(" | `").append(plan.centerX()).append(" ")
                        .append(plan.centerY()).append(" ").append(plan.centerZ()).append("` | ")
                        .append(plan.blocks().size()).append(" |\n");
                report.append("\n");
            }
            if (snapshotTotal > 0) report.append("## 復原\n\n- 已復原方塊：").append(restored)
                    .append("/").append(snapshotTotal).append("\n");
            String value = report.toString();
            Files.writeString(reportDirectory().resolve(run.id() + ".md"), value, StandardCharsets.UTF_8);
            Files.writeString(reportDirectory().resolve("latest-report.md"), value, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Cannot write acceptance report", exception);
        }
    }

    private String dynmapUrl(AcceptanceRunSnapshot run) {
        String base = plugin.getConfig().getString("acceptance.dynmap-url", "http://127.0.0.1:8123/");
        String map = plugin.getConfig().getString("acceptance.dynmap-map", "surface");
        return base + "?worldname=" + run.world() + "&mapname=" + map + "&zoom=5&x=" + run.centerX()
                + "&y=" + run.centerY() + "&z=" + run.centerZ();
    }

    private Path reportDirectory() {
        return plugin.getDataFolder().toPath().resolve("acceptance");
    }

    private Location ground(Location location) {
        World world = location.getWorld();
        int y = world.getHighestBlockYAt(location.getBlockX(), location.getBlockZ()) + 1;
        return new Location(world, location.getBlockX() + 0.5, y, location.getBlockZ() + 0.5);
    }

    private String stateDisplay(AcceptanceState state) {
        return switch (state) {
            case PREPARING -> "準備中";
            case PREVIEW -> "等待瀏覽器檢視";
            case RESTORED -> "已復原";
            case FAILED -> "失敗或有復原衝突";
        };
    }

    private static final class CheckBook {
        private final Map<String, Boolean> results = new LinkedHashMap<>();
        private int passed;
        private int total;

        private void check(String label, boolean value) {
            results.put(label, value);
            total++;
            if (value) passed++;
        }
    }
}
