package tw.zack.evilisland.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DatabaseManager implements AutoCloseable {
    private static final int CURRENT_SCHEMA = 5;
    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path dataDirectory;
    private final Path databasePath;
    private final int backupRetention;
    private final Logger logger;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "EvilIsland-SQLite");
        thread.setDaemon(true);
        return thread;
    });

    public DatabaseManager(Path dataDirectory, int backupRetention, Logger logger) {
        this.dataDirectory = dataDirectory;
        this.databasePath = dataDirectory.resolve("evilisland.sqlite3");
        this.backupRetention = Math.max(1, backupRetention);
        this.logger = logger;
    }

    public void initialize() {
        try {
            Class.forName("org.sqlite.JDBC");
            Files.createDirectories(dataDirectory);
            checkpointAndBackup();
            try (Connection connection = openConnection()) {
                migrate(connection);
            }
            logger.info("SQLite schema v" + CURRENT_SCHEMA + " ready at " + databasePath.getFileName() + ".");
        } catch (ClassNotFoundException | IOException | SQLException exception) {
            throw new IllegalStateException("Cannot initialize EvilIsland SQLite database", exception);
        }
    }

    public Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
            statement.execute("PRAGMA journal_mode = WAL");
        }
        return connection;
    }

    public <T> CompletableFuture<T> submit(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, writer);
    }

    public CompletableFuture<Void> submit(Runnable task) {
        return CompletableFuture.runAsync(task, writer);
    }

    public Path databasePath() {
        return databasePath;
    }

    public int schemaVersion() throws SQLException {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version")) {
            return result.next() ? result.getInt(1) : 0;
        }
    }

    @Override
    public void close() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(15, TimeUnit.SECONDS)) {
                logger.warning("SQLite writer did not stop within 15 seconds; waiting tasks were cancelled.");
                writer.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            writer.shutdownNow();
        }
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
        } catch (SQLException exception) {
            logger.log(Level.WARNING, "Cannot checkpoint SQLite database during shutdown", exception);
        }
    }

    private void checkpointAndBackup() throws SQLException, IOException {
        if (!Files.isRegularFile(databasePath) || Files.size(databasePath) == 0L) {
            return;
        }
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA wal_checkpoint(FULL)");
        }
        Path backupDirectory = dataDirectory.resolve("backups");
        Files.createDirectories(backupDirectory);
        Path target = backupDirectory.resolve("evilisland-" + BACKUP_TIME.format(LocalDateTime.now()) + ".sqlite3");
        Files.copy(databasePath, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        try (var paths = Files.list(backupDirectory)) {
            List<Path> backups = paths
                    .filter(path -> path.getFileName().toString().startsWith("evilisland-"))
                    .filter(path -> path.getFileName().toString().endsWith(".sqlite3"))
                    .sorted(Comparator.comparing(Path::getFileName).reversed())
                    .toList();
            for (int index = backupRetention; index < backups.size(); index++) {
                Files.deleteIfExists(backups.get(index));
            }
        }
    }

    private void migrate(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER PRIMARY KEY, applied_at INTEGER NOT NULL)");
        }
        int version;
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version")) {
            version = result.next() ? result.getInt(1) : 0;
        }
        if (version > CURRENT_SCHEMA) {
            throw new SQLException("Database schema v" + version + " is newer than plugin schema v" + CURRENT_SCHEMA);
        }
        if (version < 1) {
            applyVersionOne(connection);
            version = 1;
        }
        if (version < 2) {
            applyVersionTwo(connection);
            version = 2;
        }
        if (version < 3) {
            applyVersionThree(connection);
            version = 3;
        }
        if (version < 4) {
            applyVersionFour(connection);
            version = 4;
        }
        if (version < 5) {
            applyVersionFive(connection);
        }
    }

    private void applyVersionOne(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE player_profile (
                        uuid TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        tendency TEXT,
                        primary_formula TEXT,
                        secondary_formula TEXT,
                        formula_primary_percent INTEGER,
                        qi INTEGER NOT NULL DEFAULT 0,
                        essence INTEGER NOT NULL DEFAULT 0,
                        transformations INTEGER NOT NULL DEFAULT 0,
                        objective INTEGER NOT NULL DEFAULT 0,
                        zaochi_kills INTEGER NOT NULL DEFAULT 0,
                        updated_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE world_event (
                        id TEXT PRIMARY KEY,
                        type TEXT NOT NULL,
                        state TEXT NOT NULL,
                        world TEXT NOT NULL,
                        anchor_x REAL NOT NULL,
                        anchor_y REAL NOT NULL,
                        anchor_z REAL NOT NULL,
                        payload TEXT NOT NULL DEFAULT '{}',
                        updated_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("CREATE INDEX world_event_state_idx ON world_event(state)");
            statement.execute("INSERT INTO schema_version(version, applied_at) VALUES (1, "
                    + System.currentTimeMillis() + ")");
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void applyVersionTwo(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE campaign_state (
                        id INTEGER PRIMARY KEY CHECK (id = 1),
                        cycle INTEGER NOT NULL,
                        week INTEGER NOT NULL,
                        day INTEGER NOT NULL,
                        defense INTEGER NOT NULL,
                        supply INTEGER NOT NULL,
                        intelligence INTEGER NOT NULL,
                        morale INTEGER NOT NULL,
                        epoch_day INTEGER NOT NULL,
                        completed_today INTEGER NOT NULL DEFAULT 0,
                        completed_contract TEXT NOT NULL DEFAULT '',
                        updated_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("INSERT INTO schema_version(version, applied_at) VALUES (2, "
                    + System.currentTimeMillis() + ")");
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void applyVersionThree(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE npc_roster (
                        role TEXT PRIMARY KEY,
                        fatigue INTEGER NOT NULL DEFAULT 0,
                        injured_until INTEGER NOT NULL DEFAULT 0,
                        updated_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("INSERT INTO schema_version(version, applied_at) VALUES (3, "
                    + System.currentTimeMillis() + ")");
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void applyVersionFour(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE campaign_state ADD COLUMN weekly_resolved INTEGER NOT NULL DEFAULT 0");
            statement.execute("ALTER TABLE campaign_state ADD COLUMN weekly_strategy TEXT NOT NULL DEFAULT 'none'");
            statement.execute("ALTER TABLE campaign_state ADD COLUMN fortify_points INTEGER NOT NULL DEFAULT 0");
            statement.execute("ALTER TABLE campaign_state ADD COLUMN provision_points INTEGER NOT NULL DEFAULT 0");
            statement.execute("ALTER TABLE campaign_state ADD COLUMN recon_points INTEGER NOT NULL DEFAULT 0");
            statement.execute("INSERT INTO schema_version(version, applied_at) VALUES (4, "
                    + System.currentTimeMillis() + ")");
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void applyVersionFive(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE development_state (
                        id INTEGER PRIMARY KEY CHECK (id = 1),
                        cycle INTEGER NOT NULL DEFAULT 1,
                        last_ending TEXT NOT NULL DEFAULT '',
                        updated_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("CREATE TABLE development_resource (resource TEXT PRIMARY KEY, amount INTEGER NOT NULL DEFAULT 0)");
            statement.execute("CREATE TABLE city_project (project TEXT PRIMARY KEY, level INTEGER NOT NULL DEFAULT 0)");
            statement.execute("CREATE TABLE faction_relation (faction TEXT PRIMARY KEY, reputation INTEGER NOT NULL DEFAULT 0)");
            statement.execute("CREATE TABLE exploration_site (site TEXT PRIMARY KEY, discovered_cycle INTEGER NOT NULL DEFAULT 0)");
            statement.execute("CREATE TABLE event_chain (chain TEXT PRIMARY KEY, progress INTEGER NOT NULL DEFAULT 0)");
            statement.execute("""
                    CREATE TABLE player_weapon_mastery (
                        player_uuid TEXT NOT NULL,
                        weapon TEXT NOT NULL,
                        mastery INTEGER NOT NULL DEFAULT 0,
                        technique TEXT NOT NULL DEFAULT 'untrained',
                        updated_at INTEGER NOT NULL,
                        PRIMARY KEY(player_uuid, weapon),
                        FOREIGN KEY(player_uuid) REFERENCES player_profile(uuid) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE cycle_history (
                        cycle INTEGER PRIMARY KEY,
                        ending TEXT NOT NULL,
                        summary TEXT NOT NULL,
                        completed_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("INSERT INTO schema_version(version, applied_at) VALUES (5, "
                    + System.currentTimeMillis() + ")");
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }
}
