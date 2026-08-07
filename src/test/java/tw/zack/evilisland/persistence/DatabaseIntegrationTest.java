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
import tw.zack.evilisland.model.TechniquePath;
import tw.zack.evilisland.model.WeaponMasterySnapshot;
import tw.zack.evilisland.model.WeaponType;
import tw.zack.evilisland.model.WorldDevelopmentSnapshot;
import tw.zack.evilisland.model.WorldResource;

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
            assert database.schemaVersion() == 6;

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

            WorldEventRepository events = new WorldEventRepository(database);
            WorldEventSnapshot event = new WorldEventSnapshot(eventId, "east_patrol",
                    WorldEventState.ACTIVE, worldId, 10.5, 64, -8.5, "{}", 200L);
            events.save(event);
            WorldEventSnapshot loadedEvent = events.find(eventId).orElseThrow();
            assert loadedEvent.equals(event);
            assert events.findAll().size() == 1;
            events.delete(eventId);
            assert events.find(eventId).isEmpty();
            database.close();

            DatabaseManager reopened = new DatabaseManager(directory, 3, logger);
            reopened.initialize();
            assert new PlayerProfileRepository(reopened).find(playerId).isPresent();
            assert new CampaignRepository(reopened).find().orElseThrow().equals(campaign);
            assert new NpcRosterRepository(reopened).findAll().get(NpcRole.WUJI).equals(wuji);
            assert new DevelopmentRepository(reopened).loadWorld().orElseThrow().equals(newerWorld);
            assert new DevelopmentRepository(reopened).loadRoute(2).orElseThrow() == CityRoute.EXPEDITION;
            try (var backups = Files.list(directory.resolve("backups"))) {
                assert backups.filter(Files::isRegularFile).count() == 1;
            }
            new PlayerProfileRepository(reopened).delete(playerId);
            assert new PlayerProfileRepository(reopened).find(playerId).isEmpty();
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
