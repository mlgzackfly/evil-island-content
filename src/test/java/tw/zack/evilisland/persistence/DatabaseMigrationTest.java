package tw.zack.evilisland.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Comparator;
import java.util.logging.Logger;

public final class DatabaseMigrationTest {
    private DatabaseMigrationTest() {
    }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("evil-island-migration-test-");
        Path databasePath = directory.resolve("evilisland.sqlite3");
        try {
            Class.forName("org.sqlite.JDBC");
            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
                 var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE schema_version (version INTEGER PRIMARY KEY, applied_at INTEGER NOT NULL)");
                statement.execute("INSERT INTO schema_version(version, applied_at) VALUES (1, 1)");
            }

            DatabaseManager database = new DatabaseManager(directory, 3, Logger.getLogger("MigrationTest"));
            database.initialize();
            assert database.schemaVersion() == 13;
            assert new CampaignRepository(database).find().isEmpty();
            assert new NpcRosterRepository(database).findAll().isEmpty();
            assert new DevelopmentRepository(database).loadWorld().isEmpty();
            assert new DevelopmentRepository(database).loadConditions().isEmpty();
            assert new LivingEventRepository(database).findRecent(4).isEmpty();
            assert new CrisisSceneRepository(database).loadScenes().isEmpty();
            assert new SupplyRouteRepository(database).active().isEmpty();
            database.close();
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
        System.out.println("DatabaseMigrationTest passed");
    }
}
