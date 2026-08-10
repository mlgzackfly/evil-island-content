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
    private static final int CURRENT_SCHEMA = 18;
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
            version = 5;
        }
        if (version < 6) {
            applyVersionSix(connection);
            version = 6;
        }
        if (version < 7) {
            applyVersionSeven(connection);
            version = 7;
        }
        if (version < 8) {
            applyVersionEight(connection);
            version = 8;
        }
        if (version < 9) {
            applyVersionNine(connection);
            version = 9;
        }
        if (version < 10) {
            applyVersionTen(connection);
            version = 10;
        }
        if (version < 11) {
            applyVersionEleven(connection);
            version = 11;
        }
        if (version < 12) {
            applyVersionTwelve(connection);
            version = 12;
        }
        if (version < 13) {
            applyVersionThirteen(connection);
            version = 13;
        }
        if (version < 14) {
            applyVersionFourteen(connection);
            version = 14;
        }
        if (version < 15) {
            applyVersionFifteen(connection);
            version = 15;
        }
        if (version < 16) {
            applyVersionSixteen(connection);
            version = 16;
        }
        if (version < 17) {
            applyVersionSeventeen(connection);
            version = 17;
        }
        if (version < 18) {
            applyVersionEighteen(connection);
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

    private void applyVersionSix(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE city_route (
                        cycle INTEGER PRIMARY KEY,
                        route TEXT NOT NULL,
                        chosen_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE construction_plot (
                        project TEXT PRIMARY KEY,
                        world TEXT NOT NULL,
                        x INTEGER NOT NULL,
                        y INTEGER NOT NULL,
                        z INTEGER NOT NULL,
                        rotation INTEGER NOT NULL DEFAULT 0,
                        level INTEGER NOT NULL DEFAULT 0,
                        status TEXT NOT NULL DEFAULT 'pending'
                    )
                    """);
            statement.execute("""
                    CREATE TABLE construction_block (
                        project TEXT NOT NULL,
                        world TEXT NOT NULL,
                        x INTEGER NOT NULL,
                        y INTEGER NOT NULL,
                        z INTEGER NOT NULL,
                        original_data TEXT NOT NULL,
                        placed_data TEXT NOT NULL,
                        PRIMARY KEY(project, world, x, y, z),
                        FOREIGN KEY(project) REFERENCES construction_plot(project) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE faction_contract (
                        cycle INTEGER NOT NULL,
                        faction TEXT NOT NULL,
                        progress INTEGER NOT NULL DEFAULT 0,
                        resolution TEXT NOT NULL DEFAULT 'none',
                        state TEXT NOT NULL DEFAULT 'active',
                        updated_at INTEGER NOT NULL,
                        PRIMARY KEY(cycle, faction)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE faction_stock (
                        faction TEXT NOT NULL,
                        week INTEGER NOT NULL,
                        remaining INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(faction, week)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE player_faction_credit (
                        player_uuid TEXT NOT NULL,
                        faction TEXT NOT NULL,
                        week INTEGER NOT NULL,
                        amount INTEGER NOT NULL DEFAULT 0,
                        updated_at INTEGER NOT NULL,
                        PRIMARY KEY(player_uuid, faction, week)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE mission_telemetry (
                        id TEXT PRIMARY KEY,
                        mission_type TEXT NOT NULL,
                        players INTEGER NOT NULL,
                        started_at INTEGER NOT NULL,
                        completed_at INTEGER,
                        result TEXT NOT NULL DEFAULT 'active',
                        failure_reason TEXT NOT NULL DEFAULT '',
                        payload TEXT NOT NULL DEFAULT '{}'
                    )
                    """);
            statement.execute("CREATE INDEX mission_telemetry_result_idx ON mission_telemetry(result)");
            statement.execute("""
                    CREATE TABLE player_activity (
                        player_uuid TEXT PRIMARY KEY,
                        last_seen INTEGER NOT NULL,
                        last_catchup_cycle INTEGER NOT NULL DEFAULT 0
                    )
                    """);
            statement.execute("INSERT INTO schema_version(version, applied_at) VALUES (6, "
                    + System.currentTimeMillis() + ")");
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void applyVersionSeven(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE acceptance_run (
                        id TEXT PRIMARY KEY,
                        state TEXT NOT NULL,
                        world TEXT NOT NULL,
                        center_x INTEGER NOT NULL,
                        center_y INTEGER NOT NULL,
                        center_z INTEGER NOT NULL,
                        checks_passed INTEGER NOT NULL DEFAULT 0,
                        checks_total INTEGER NOT NULL DEFAULT 0,
                        summary TEXT NOT NULL DEFAULT '',
                        started_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("CREATE INDEX acceptance_run_state_idx ON acceptance_run(state)");
            statement.execute("""
                    CREATE TABLE acceptance_block (
                        run_id TEXT NOT NULL,
                        world TEXT NOT NULL,
                        x INTEGER NOT NULL,
                        y INTEGER NOT NULL,
                        z INTEGER NOT NULL,
                        original_data TEXT NOT NULL,
                        placed_data TEXT NOT NULL,
                        PRIMARY KEY(run_id, world, x, y, z),
                        FOREIGN KEY(run_id) REFERENCES acceptance_run(id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("INSERT INTO schema_version(version, applied_at) VALUES (7, "
                    + System.currentTimeMillis() + ")");
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void applyVersionEight(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE player_growth (
                        player_uuid TEXT PRIMARY KEY,
                        rejection INTEGER NOT NULL DEFAULT 0,
                        updated_at INTEGER NOT NULL,
                        FOREIGN KEY(player_uuid) REFERENCES player_profile(uuid) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE player_essence_source (
                        player_uuid TEXT NOT NULL,
                        source TEXT NOT NULL,
                        amount INTEGER NOT NULL DEFAULT 0,
                        purity_points INTEGER NOT NULL DEFAULT 0,
                        updated_at INTEGER NOT NULL,
                        PRIMARY KEY(player_uuid, source),
                        FOREIGN KEY(player_uuid) REFERENCES player_profile(uuid) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE player_inheritance (
                        player_uuid TEXT NOT NULL,
                        inheritance TEXT NOT NULL,
                        progress INTEGER NOT NULL DEFAULT 0,
                        completed INTEGER NOT NULL DEFAULT 0,
                        attuned INTEGER NOT NULL DEFAULT 0,
                        updated_at INTEGER NOT NULL,
                        PRIMARY KEY(player_uuid, inheritance),
                        FOREIGN KEY(player_uuid) REFERENCES player_profile(uuid) ON DELETE CASCADE
                    )
                    """);
            statement.execute("CREATE INDEX player_inheritance_attuned_idx ON player_inheritance(player_uuid, attuned)");
            statement.execute("INSERT INTO schema_version(version, applied_at) VALUES (8, "
                    + System.currentTimeMillis() + ")");
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void applyVersionNine(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE city_project_condition (
                        project TEXT PRIMARY KEY,
                        condition INTEGER NOT NULL DEFAULT 100,
                        updated_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("INSERT INTO schema_version(version, applied_at) VALUES (9, "
                    + System.currentTimeMillis() + ")");
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void applyVersionTen(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE living_event (
                        id TEXT PRIMARY KEY,
                        type TEXT NOT NULL,
                        state TEXT NOT NULL,
                        approach TEXT NOT NULL DEFAULT 'none',
                        cycle INTEGER NOT NULL,
                        week INTEGER NOT NULL,
                        day INTEGER NOT NULL,
                        started_epoch_day INTEGER NOT NULL,
                        expires_epoch_day INTEGER NOT NULL,
                        participants INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL,
                        resolved_at INTEGER NOT NULL DEFAULT 0,
                        updated_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("CREATE INDEX living_event_state_idx ON living_event(state)");
            statement.execute("CREATE INDEX living_event_cycle_idx ON living_event(cycle, created_at)");
            statement.execute("INSERT INTO schema_version(version, applied_at) VALUES (10, "
                    + System.currentTimeMillis() + ")");
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void applyVersionEleven(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE crisis_scene (
                        event_id TEXT PRIMARY KEY,
                        type TEXT NOT NULL,
                        state TEXT NOT NULL,
                        world TEXT NOT NULL,
                        x INTEGER NOT NULL,
                        y INTEGER NOT NULL,
                        z INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("CREATE INDEX crisis_scene_type_idx ON crisis_scene(type, state)");
            statement.execute("""
                    CREATE TABLE crisis_scene_block (
                        event_id TEXT NOT NULL,
                        world TEXT NOT NULL,
                        x INTEGER NOT NULL,
                        y INTEGER NOT NULL,
                        z INTEGER NOT NULL,
                        original_data TEXT NOT NULL,
                        active_data TEXT NOT NULL,
                        resolved_data TEXT NOT NULL,
                        expired_data TEXT NOT NULL,
                        placed_data TEXT NOT NULL,
                        PRIMARY KEY(event_id, world, x, y, z),
                        FOREIGN KEY(event_id) REFERENCES crisis_scene(event_id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("INSERT INTO schema_version(version, applied_at) VALUES (11, "
                    + System.currentTimeMillis() + ")");
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void applyVersionTwelve(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE supply_route (
                        event_id TEXT PRIMARY KEY,
                        state TEXT NOT NULL,
                        dispatcher TEXT NOT NULL,
                        receiver TEXT NOT NULL DEFAULT '',
                        departed_at INTEGER NOT NULL,
                        arrives_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("CREATE INDEX supply_route_state_idx ON supply_route(state, updated_at)");
            statement.execute("INSERT INTO schema_version(version, applied_at) VALUES (12, "
                    + System.currentTimeMillis() + ")");
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void applyVersionThirteen(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE resident_intel (
                        event_id TEXT NOT NULL,
                        resident TEXT NOT NULL,
                        reporter TEXT NOT NULL,
                        collected_at INTEGER NOT NULL,
                        PRIMARY KEY(event_id, resident),
                        FOREIGN KEY(event_id) REFERENCES living_event(id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("CREATE INDEX resident_intel_event_idx ON resident_intel(event_id, collected_at)");
            statement.execute("INSERT INTO schema_version(version, applied_at) VALUES (13, "
                    + System.currentTimeMillis() + ")");
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void applyVersionFourteen(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE cycle_boss_history (
                        cycle INTEGER PRIMARY KEY,
                        variant TEXT NOT NULL,
                        engaged_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("INSERT INTO schema_version(version, applied_at) VALUES (14, "
                    + System.currentTimeMillis() + ")");
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void applyVersionFifteen(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE region_control (
                        site TEXT PRIMARY KEY,
                        state TEXT NOT NULL,
                        stability INTEGER NOT NULL,
                        camp_level INTEGER NOT NULL DEFAULT 1,
                        supplies INTEGER NOT NULL DEFAULT 3,
                        world TEXT NOT NULL DEFAULT '',
                        x INTEGER NOT NULL DEFAULT 0,
                        y INTEGER NOT NULL DEFAULT 0,
                        z INTEGER NOT NULL DEFAULT 0,
                        updated_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE region_control_effect (
                        effect_id TEXT PRIMARY KEY,
                        site TEXT NOT NULL,
                        source TEXT NOT NULL,
                        delta INTEGER NOT NULL,
                        created_at INTEGER NOT NULL,
                        FOREIGN KEY(site) REFERENCES region_control(site) ON DELETE CASCADE
                    )
                    """);
            statement.execute("CREATE INDEX region_control_effect_site_idx ON region_control_effect(site, created_at)");
            statement.execute("""
                    CREATE TABLE expedition_camp_block (
                        site TEXT NOT NULL,
                        world TEXT NOT NULL,
                        x INTEGER NOT NULL,
                        y INTEGER NOT NULL,
                        z INTEGER NOT NULL,
                        original_data TEXT NOT NULL,
                        level_one_data TEXT NOT NULL,
                        level_two_data TEXT NOT NULL,
                        lost_data TEXT NOT NULL,
                        placed_data TEXT NOT NULL,
                        PRIMARY KEY(site, world, x, y, z),
                        FOREIGN KEY(site) REFERENCES region_control(site) ON DELETE CASCADE
                    )
                    """);
            statement.execute("INSERT INTO schema_version(version, applied_at) VALUES (15, "
                    + System.currentTimeMillis() + ")");
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void applyVersionSixteen(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE expedition_instance (
                        id TEXT PRIMARY KEY,
                        operation TEXT NOT NULL,
                        route TEXT NOT NULL,
                        phase TEXT NOT NULL,
                        outcome TEXT NOT NULL DEFAULT '',
                        world TEXT NOT NULL,
                        anchor_x REAL NOT NULL,
                        anchor_y REAL NOT NULL,
                        anchor_z REAL NOT NULL,
                        leader TEXT NOT NULL,
                        partner TEXT NOT NULL DEFAULT '',
                        companion TEXT NOT NULL DEFAULT '',
                        seed INTEGER NOT NULL,
                        approach_mask INTEGER NOT NULL DEFAULT 0,
                        clue_mask INTEGER NOT NULL DEFAULT 0,
                        objective_mask INTEGER NOT NULL DEFAULT 0,
                        first_activator TEXT NOT NULL DEFAULT '',
                        objective_deadline INTEGER NOT NULL DEFAULT 0,
                        alert INTEGER NOT NULL DEFAULT 0,
                        enemies_remaining INTEGER NOT NULL DEFAULT 0,
                        started_at INTEGER NOT NULL,
                        phase_started_at INTEGER NOT NULL,
                        completed_at INTEGER NOT NULL DEFAULT 0,
                        updated_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("CREATE INDEX expedition_active_idx ON expedition_instance(outcome, updated_at)");
            statement.execute("CREATE INDEX expedition_leader_idx ON expedition_instance(leader, started_at)");
            statement.execute("""
                    CREATE TABLE expedition_stage_log (
                        expedition_id TEXT NOT NULL,
                        phase TEXT NOT NULL,
                        started_at INTEGER NOT NULL,
                        completed_at INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(expedition_id, phase),
                        FOREIGN KEY(expedition_id) REFERENCES expedition_instance(id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("INSERT INTO schema_version(version, applied_at) VALUES (16, "
                    + System.currentTimeMillis() + ")");
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void applyVersionSeventeen(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE expedition_run_state (
                        expedition_id TEXT PRIMARY KEY,
                        site TEXT NOT NULL DEFAULT 'eastern_route',
                        kit_mask INTEGER NOT NULL DEFAULT 0,
                        event_mask INTEGER NOT NULL DEFAULT 0,
                        event_score INTEGER NOT NULL DEFAULT 0,
                        updated_at INTEGER NOT NULL,
                        FOREIGN KEY(expedition_id) REFERENCES expedition_instance(id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE expedition_weekly_reward (
                        site TEXT NOT NULL,
                        route TEXT NOT NULL,
                        cycle INTEGER NOT NULL,
                        week INTEGER NOT NULL,
                        expedition_id TEXT NOT NULL UNIQUE,
                        claimed_at INTEGER NOT NULL,
                        PRIMARY KEY(site, route, cycle, week),
                        FOREIGN KEY(expedition_id) REFERENCES expedition_instance(id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE expedition_consequence (
                        site TEXT PRIMARY KEY,
                        expedition_id TEXT NOT NULL,
                        operation TEXT NOT NULL,
                        outcome TEXT NOT NULL,
                        world TEXT NOT NULL,
                        x REAL NOT NULL,
                        y REAL NOT NULL,
                        z REAL NOT NULL,
                        updated_at INTEGER NOT NULL,
                        FOREIGN KEY(expedition_id) REFERENCES expedition_instance(id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("INSERT INTO schema_version(version, applied_at) VALUES (17, "
                    + System.currentTimeMillis() + ")");
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void applyVersionEighteen(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE expedition_region_progress (
                        site TEXT PRIMARY KEY,
                        completed INTEGER NOT NULL DEFAULT 0,
                        partial INTEGER NOT NULL DEFAULT 0,
                        withdrawn INTEGER NOT NULL DEFAULT 0,
                        abandoned INTEGER NOT NULL DEFAULT 0,
                        last_operation TEXT NOT NULL,
                        last_outcome TEXT NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("INSERT INTO schema_version(version, applied_at) VALUES (18, "
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
