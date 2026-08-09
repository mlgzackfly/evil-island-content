package tw.zack.evilisland.persistence;

import tw.zack.evilisland.model.Formula;
import tw.zack.evilisland.model.CampaignSnapshot;
import tw.zack.evilisland.model.FormulaPath;
import tw.zack.evilisland.model.ObjectiveStage;
import tw.zack.evilisland.model.PlayerProfileSnapshot;
import tw.zack.evilisland.model.QiTendency;
import tw.zack.evilisland.model.WorldEventSnapshot;
import tw.zack.evilisland.model.WorldEventState;
import tw.zack.evilisland.model.NpcRole;
import tw.zack.evilisland.model.NpcRosterSnapshot;
import tw.zack.evilisland.model.CampaignStrategy;
import tw.zack.evilisland.model.CityProject;
import tw.zack.evilisland.model.CityRoute;
import tw.zack.evilisland.model.ConstructionBlockSnapshot;
import tw.zack.evilisland.model.ConstructionPlot;
import tw.zack.evilisland.model.ContractResolution;
import tw.zack.evilisland.model.EventChain;
import tw.zack.evilisland.model.ExplorationSite;
import tw.zack.evilisland.model.Faction;
import tw.zack.evilisland.model.FactionContract;
import tw.zack.evilisland.model.FactionContractSnapshot;
import tw.zack.evilisland.model.FactionContractState;
import tw.zack.evilisland.model.MissionType;
import tw.zack.evilisland.model.PlayerActivitySnapshot;
import tw.zack.evilisland.model.AcceptanceBlockSnapshot;
import tw.zack.evilisland.model.AcceptanceRunSnapshot;
import tw.zack.evilisland.model.AcceptanceState;
import tw.zack.evilisland.model.TechniquePath;
import tw.zack.evilisland.model.WeaponMasterySnapshot;
import tw.zack.evilisland.model.WeaponType;
import tw.zack.evilisland.model.WorldDevelopmentSnapshot;
import tw.zack.evilisland.model.WorldResource;
import tw.zack.evilisland.model.EssenceSourceSnapshot;
import tw.zack.evilisland.model.InheritanceSnapshot;
import tw.zack.evilisland.model.InheritanceType;
import tw.zack.evilisland.model.PlayerGrowthSnapshot;
import tw.zack.evilisland.model.ProjectConditionSnapshot;
import tw.zack.evilisland.model.LivingEventApproach;
import tw.zack.evilisland.model.LivingEventSnapshot;
import tw.zack.evilisland.model.LivingEventState;
import tw.zack.evilisland.model.LivingEventType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.Map;
import java.util.logging.Logger;

public final class DatabaseIntegrationTest {
    private DatabaseIntegrationTest() {
    }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("evil-island-db-test-");
        Logger logger = Logger.getLogger("DatabaseIntegrationTest");
        UUID playerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        try {
            DatabaseManager database = new DatabaseManager(directory, 3, logger);
            database.initialize();
            assert database.schemaVersion() == 13;

            NpcRosterRepository roster = new NpcRosterRepository(database);
            NpcRosterSnapshot wuji = new NpcRosterSnapshot(NpcRole.WUJI, 42, 12345L, 100L);
            roster.save(wuji);
            assert roster.findAll().get(NpcRole.WUJI).equals(wuji);

            CampaignRepository campaigns = new CampaignRepository(database);
            CampaignSnapshot campaign = new CampaignSnapshot(2, 3, 4, 61, 48, 72, 55,
                    22000L, true, "deep_field_scout", true, CampaignStrategy.RECON,
                    1, 0, 2, 90L);
            campaigns.save(campaign);
            assert campaigns.find().orElseThrow().equals(campaign);

            PlayerProfileRepository profiles = new PlayerProfileRepository(database);
            FormulaPath path = FormulaPath.mixed(Formula.BAO, Formula.QING, 70);
            PlayerProfileSnapshot profile = new PlayerProfileSnapshot(playerId, "測試玩家",
                    QiTendency.OUTWARD, path, 91, 7, 2, ObjectiveStage.DEFEAT_XINGTIAN, 11, 100L);
            profiles.save(profile);
            PlayerProfileSnapshot loaded = profiles.find(playerId).orElseThrow();
            assert loaded.name().equals("測試玩家");
            assert loaded.tendency() == QiTendency.OUTWARD;
            assert loaded.formulaPath().equals(path);
            assert loaded.qi() == 91;
            assert loaded.essence() == 7;
            assert loaded.transformations() == 2;
            assert loaded.objective() == ObjectiveStage.DEFEAT_XINGTIAN;
            assert loaded.zaochiKills() == 11;

            GrowthRepository growth = new GrowthRepository(database);
            PlayerGrowthSnapshot growthState = new PlayerGrowthSnapshot(playerId, 2, 105L);
            growth.saveGrowth(growthState);
            assert growth.loadGrowth(playerId).orElseThrow().equals(growthState);
            var sources = java.util.List.of(
                    new EssenceSourceSnapshot(playerId, "zaochi", 3, 3, 106L),
                    new EssenceSourceSnapshot(playerId, "xingtian", 4, 12, 106L));
            growth.replaceSources(playerId, sources);
            assert java.util.Set.copyOf(growth.loadSources(playerId)).equals(java.util.Set.copyOf(sources));
            Map<InheritanceType, InheritanceSnapshot> inheritances = new java.util.EnumMap<>(InheritanceType.class);
            for (InheritanceType type : InheritanceType.values()) {
                inheritances.put(type, new InheritanceSnapshot(playerId, type,
                        type == InheritanceType.MAGIC ? 2 : 0,
                        type == InheritanceType.MAGIC, type == InheritanceType.MAGIC, 107L));
            }
            growth.replaceInheritances(playerId, inheritances);
            assert growth.loadInheritances(playerId).equals(inheritances);

            DevelopmentRepository development = new DevelopmentRepository(database);
            WorldDevelopmentSnapshot world = new WorldDevelopmentSnapshot(2,
                    Map.of(WorldResource.TIMBER, 9), Map.of(CityProject.WORKSHOP, 1),
                    Map.of(Faction.MAO, 25), Map.of(ExplorationSite.RONGXU_APPROACH, 2),
                    Map.of(EventChain.SAFE_ROUTE, 3), "遠路重開", 110L);
            development.saveWorld(world);
            assert development.loadWorld().orElseThrow().equals(world);
            WorldDevelopmentSnapshot newerWorld = new WorldDevelopmentSnapshot(2,
                    Map.of(WorldResource.TIMBER, 12), world.projects(), world.reputation(), world.discoveries(),
                    world.chains(), "遠路重開", 210L);
            development.saveWorld(newerWorld);
            development.saveWorld(world);
            assert development.loadWorld().orElseThrow().equals(newerWorld);
            WeaponMasterySnapshot mastery = new WeaponMasterySnapshot(playerId, WeaponType.SPEAR, 18,
                    TechniquePath.CONTROL, 120L);
            development.saveMastery(mastery);
            assert development.loadMastery(playerId).get(WeaponType.SPEAR).equals(mastery);
            development.recordCycle(1, "新城固守", "測試輪次", 130L);
            development.saveRoute(2, CityRoute.EXPEDITION, 140L);
            assert development.loadRoute(2).orElseThrow() == CityRoute.EXPEDITION;
            development.saveRoute(2, CityRoute.FORTRESS, 150L);
            assert development.loadRoute(2).orElseThrow() == CityRoute.EXPEDITION;
            Map<CityProject, ProjectConditionSnapshot> conditions = new java.util.EnumMap<>(CityProject.class);
            conditions.put(CityProject.WALLS, new ProjectConditionSnapshot(CityProject.WALLS, 64, 151L));
            conditions.put(CityProject.WORKSHOP, new ProjectConditionSnapshot(CityProject.WORKSHOP, 38, 152L));
            development.saveConditions(conditions);
            assert development.loadConditions().equals(conditions);
            development.saveCondition(new ProjectConditionSnapshot(CityProject.WALLS, 90, 160L));
            development.saveCondition(new ProjectConditionSnapshot(CityProject.WALLS, 10, 140L));
            assert development.loadConditions().get(CityProject.WALLS).condition() == 90;
            conditions.put(CityProject.WALLS, new ProjectConditionSnapshot(CityProject.WALLS, 90, 160L));

            ConstructionRepository construction = new ConstructionRepository(database);
            ConstructionPlot plot = new ConstructionPlot(CityProject.WALLS, "test", 10, 70, 20,
                    0, 1, "complete");
            construction.savePlot(plot);
            assert construction.loadPlots().get(CityProject.WALLS).equals(plot);
            ConstructionBlockSnapshot block = new ConstructionBlockSnapshot(CityProject.WALLS, "test",
                    10, 70, 20, "minecraft:air", "minecraft:stone_bricks");
            construction.saveBlocks(java.util.List.of(block));
            assert construction.loadBlocks(CityProject.WALLS).equals(java.util.List.of(block));

            DiplomacyRepository diplomacy = new DiplomacyRepository(database);
            FactionContractSnapshot contract = new FactionContractSnapshot(2, FactionContract.MAO_SETTLEMENT,
                    2, ContractResolution.COOPERATE, FactionContractState.RESOLVED, 160L);
            diplomacy.saveContract(contract);
            assert diplomacy.loadContract(2, Faction.MAO).orElseThrow().equals(contract);
            assert diplomacy.addCredit(playerId, Faction.MAO, 22, 3, 170L) == 1;
            assert diplomacy.stock(Faction.MAO, 22, 4) == 4;
            assert diplomacy.purchase(playerId, Faction.MAO, 22, 1);
            assert diplomacy.credit(playerId, Faction.MAO, 22) == 0;
            assert diplomacy.stock(Faction.MAO, 22, 4) == 3;

            MissionTelemetryRepository telemetry = new MissionTelemetryRepository(database);
            UUID telemetryId = UUID.randomUUID();
            telemetry.start(telemetryId, MissionType.SCOUT, 2, 180L, "{}");
            assert telemetry.countByResult("active") == 1;
            telemetry.finish(telemetryId, "succeeded", "", 190L);
            assert telemetry.countByResult("succeeded") == 1;
            UUID activityPlayer = UUID.randomUUID();
            PlayerActivitySnapshot activity = new PlayerActivitySnapshot(activityPlayer, 200L, 2);
            telemetry.saveActivity(activity);
            assert telemetry.activity(activityPlayer).orElseThrow().equals(activity);

            AcceptanceRepository acceptance = new AcceptanceRepository(database);
            UUID acceptanceId = UUID.randomUUID();
            AcceptanceRunSnapshot acceptanceRun = new AcceptanceRunSnapshot(acceptanceId,
                    AcceptanceState.PREPARING, "test", 30, 80, 40, 2, 3, "測試", 210L, 220L);
            acceptance.saveRun(acceptanceRun);
            assert acceptance.activeRun().orElseThrow().equals(acceptanceRun);
            AcceptanceBlockSnapshot acceptanceBlock = new AcceptanceBlockSnapshot(acceptanceId, "test",
                    30, 80, 40, "minecraft:air", "minecraft:stone_bricks");
            acceptance.saveBlocks(java.util.List.of(acceptanceBlock));
            assert acceptance.loadBlocks(acceptanceId).equals(java.util.List.of(acceptanceBlock));
            AcceptanceRunSnapshot restoredRun = new AcceptanceRunSnapshot(acceptanceId,
                    AcceptanceState.RESTORED, "test", 30, 80, 40, 3, 3, "已復原", 210L, 230L);
            acceptance.saveRun(restoredRun);
            assert acceptance.activeRun().isEmpty();
            assert acceptance.latestRun().orElseThrow().equals(restoredRun);
            for (int index = 1; index <= 3; index++) {
                UUID oldId = UUID.randomUUID();
                acceptance.saveRun(new AcceptanceRunSnapshot(oldId, AcceptanceState.RESTORED, "test",
                        index, 80, 40, 3, 3, "歷史", 210L - index, 230L));
            }
            acceptance.pruneCompletedRuns(2);
            try (var connection = database.openConnection();
                 var statement = connection.prepareStatement("SELECT COUNT(*) FROM acceptance_run");
                 var rows = statement.executeQuery()) {
                assert rows.next();
                assert rows.getInt(1) == 2;
            }
            acceptance.deleteRun(acceptanceId);

            WorldEventRepository events = new WorldEventRepository(database);
            WorldEventSnapshot event = new WorldEventSnapshot(eventId, "east_patrol",
                    WorldEventState.ACTIVE, worldId, 10.5, 64, -8.5, "{}", 200L);
            events.save(event);
            WorldEventSnapshot loadedEvent = events.find(eventId).orElseThrow();
            assert loadedEvent.equals(event);
            assert events.findAll().size() == 1;
            events.delete(eventId);
            assert events.find(eventId).isEmpty();

            LivingEventRepository livingEvents = new LivingEventRepository(database);
            UUID livingEventId = UUID.randomUUID();
            LivingEventSnapshot livingActive = new LivingEventSnapshot(livingEventId,
                    LivingEventType.LOST_SIGNAL, LivingEventState.ACTIVE, LivingEventApproach.NONE,
                    2, 2, 3, 22000L, 22003L, 0, 240L, 0L, 240L);
            livingEvents.save(livingActive);
            assert livingEvents.active().orElseThrow().equals(livingActive);

            CrisisSceneRepository crisisScenes = new CrisisSceneRepository(database);
            var crisisScene = new tw.zack.evilisland.model.CrisisSceneSnapshot(livingEventId,
                    LivingEventType.LOST_SIGNAL, tw.zack.evilisland.model.CrisisSceneState.ACTIVE,
                    "test", 120, 70, -30, 245L);
            crisisScenes.saveScene(crisisScene);
            var crisisBlock = new tw.zack.evilisland.model.CrisisSceneBlockSnapshot(livingEventId,
                    "test", 120, 70, -30, "minecraft:air", "minecraft:lodestone",
                    "minecraft:lantern", "minecraft:cracked_stone_bricks", "minecraft:lodestone");
            crisisScenes.saveBlocks(java.util.List.of(crisisBlock));
            crisisScenes.saveScene(crisisScene.withState(
                    tw.zack.evilisland.model.CrisisSceneState.RESOLVED, 250L));
            crisisScenes.saveScene(crisisScene);
            assert crisisScenes.loadScenes().get(livingEventId).state()
                    == tw.zack.evilisland.model.CrisisSceneState.RESOLVED;
            assert crisisScenes.loadBlocks(livingEventId).equals(java.util.List.of(crisisBlock));
            SupplyRouteRepository supplyRoutes = new SupplyRouteRepository(database);
            UUID dispatcherId = UUID.randomUUID();
            var supplyRoute = new tw.zack.evilisland.model.SupplyRouteSnapshot(livingEventId,
                    tw.zack.evilisland.model.SupplyRouteState.TRANSIT, dispatcherId, null,
                    246L, 300L, 246L);
            supplyRoutes.save(supplyRoute);
            var arrivedRoute = supplyRoute.arrive(301L);
            supplyRoutes.save(arrivedRoute);
            supplyRoutes.save(supplyRoute);
            assert supplyRoutes.active().orElseThrow().equals(arrivedRoute);
            ResidentIntelRepository residentIntel = new ResidentIntelRepository(database);
            var intelReport = new tw.zack.evilisland.model.IntelReportSnapshot(livingEventId,
                    tw.zack.evilisland.model.ResidentRole.WATCHER, playerId, 302L);
            assert residentIntel.add(intelReport);
            assert !residentIntel.add(new tw.zack.evilisland.model.IntelReportSnapshot(livingEventId,
                    tw.zack.evilisland.model.ResidentRole.WATCHER, dispatcherId, 303L));
            assert residentIntel.load(livingEventId).equals(java.util.List.of(intelReport));
            LivingEventSnapshot livingResolved = livingActive.resolve(LivingEventApproach.FIELD, 2, 250L);
            livingEvents.save(livingResolved);
            livingEvents.save(livingActive);
            assert livingEvents.active().isEmpty();
            assert livingEvents.findRecent(4).get(0).equals(livingResolved);
            database.close();

            DatabaseManager reopened = new DatabaseManager(directory, 3, logger);
            reopened.initialize();
            assert new PlayerProfileRepository(reopened).find(playerId).isPresent();
            assert new CampaignRepository(reopened).find().orElseThrow().equals(campaign);
            assert new NpcRosterRepository(reopened).findAll().get(NpcRole.WUJI).equals(wuji);
            assert new DevelopmentRepository(reopened).loadWorld().orElseThrow().equals(newerWorld);
            assert new DevelopmentRepository(reopened).loadRoute(2).orElseThrow() == CityRoute.EXPEDITION;
            assert new DevelopmentRepository(reopened).loadConditions().equals(conditions);
            assert new GrowthRepository(reopened).loadGrowth(playerId).orElseThrow().equals(growthState);
            assert java.util.Set.copyOf(new GrowthRepository(reopened).loadSources(playerId))
                    .equals(java.util.Set.copyOf(sources));
            assert new GrowthRepository(reopened).loadInheritances(playerId).equals(inheritances);
            assert new LivingEventRepository(reopened).findRecent(4).get(0).equals(livingResolved);
            assert new CrisisSceneRepository(reopened).loadScenes().get(livingEventId).state()
                    == tw.zack.evilisland.model.CrisisSceneState.RESOLVED;
            assert new CrisisSceneRepository(reopened).loadBlocks(livingEventId).equals(
                    java.util.List.of(crisisBlock));
            assert new SupplyRouteRepository(reopened).find(livingEventId).orElseThrow().equals(arrivedRoute);
            assert new ResidentIntelRepository(reopened).load(livingEventId).equals(java.util.List.of(intelReport));
            try (var backups = Files.list(directory.resolve("backups"))) {
                assert backups.filter(Files::isRegularFile).count() == 1;
            }
            new PlayerProfileRepository(reopened).delete(playerId);
            assert new PlayerProfileRepository(reopened).find(playerId).isEmpty();
            assert new GrowthRepository(reopened).loadGrowth(playerId).isEmpty();
            assert new GrowthRepository(reopened).loadSources(playerId).isEmpty();
            assert new GrowthRepository(reopened).loadInheritances(playerId).isEmpty();
            reopened.close();
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
        System.out.println("DatabaseIntegrationTest passed");
    }
}
