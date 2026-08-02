package tw.zack.evilisland.persistence;

import tw.zack.evilisland.model.Formula;
import tw.zack.evilisland.model.FormulaPath;
import tw.zack.evilisland.model.ObjectiveStage;
import tw.zack.evilisland.model.PlayerProfileSnapshot;
import tw.zack.evilisland.model.QiTendency;
import tw.zack.evilisland.model.WorldEventSnapshot;
import tw.zack.evilisland.model.WorldEventState;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
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
            assert database.schemaVersion() == 1;

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
