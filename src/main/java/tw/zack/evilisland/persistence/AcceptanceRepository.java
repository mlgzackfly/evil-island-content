package tw.zack.evilisland.persistence;

import tw.zack.evilisland.model.AcceptanceBlockSnapshot;
import tw.zack.evilisland.model.AcceptanceRunSnapshot;
import tw.zack.evilisland.model.AcceptanceState;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class AcceptanceRepository {
    private final DatabaseManager database;

    public AcceptanceRepository(DatabaseManager database) {
        this.database = database;
    }

    public Optional<AcceptanceRunSnapshot> activeRun() {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT * FROM acceptance_run WHERE state IN ('preparing', 'preview')
                     ORDER BY started_at DESC LIMIT 1
                     """);
             ResultSet row = statement.executeQuery()) {
            return row.next() ? Optional.of(readRun(row)) : Optional.empty();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot load active acceptance run", exception);
        }
    }

    public Optional<AcceptanceRunSnapshot> latestRun() {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM acceptance_run ORDER BY started_at DESC LIMIT 1");
             ResultSet row = statement.executeQuery()) {
            return row.next() ? Optional.of(readRun(row)) : Optional.empty();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot load latest acceptance run", exception);
        }
    }

    public void saveRun(AcceptanceRunSnapshot run) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO acceptance_run(id, state, world, center_x, center_y, center_z,
                     checks_passed, checks_total, summary, started_at, updated_at)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT(id) DO UPDATE SET state=excluded.state, world=excluded.world,
                     center_x=excluded.center_x, center_y=excluded.center_y, center_z=excluded.center_z,
                     checks_passed=excluded.checks_passed, checks_total=excluded.checks_total,
                     summary=excluded.summary, updated_at=excluded.updated_at
                     """)) {
            statement.setString(1, run.id().toString());
            statement.setString(2, run.state().id());
            statement.setString(3, run.world());
            statement.setInt(4, run.centerX());
            statement.setInt(5, run.centerY());
            statement.setInt(6, run.centerZ());
            statement.setInt(7, run.checksPassed());
            statement.setInt(8, run.checksTotal());
            statement.setString(9, run.summary());
            statement.setLong(10, run.startedAt());
            statement.setLong(11, run.updatedAt());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot save acceptance run", exception);
        }
    }

    public void saveBlocks(List<AcceptanceBlockSnapshot> blocks) {
        if (blocks.isEmpty()) return;
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO acceptance_block(run_id, world, x, y, z, original_data, placed_data)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(run_id, world, x, y, z) DO UPDATE SET placed_data=excluded.placed_data
                    """)) {
                for (AcceptanceBlockSnapshot block : blocks) {
                    statement.setString(1, block.runId().toString());
                    statement.setString(2, block.world());
                    statement.setInt(3, block.x());
                    statement.setInt(4, block.y());
                    statement.setInt(5, block.z());
                    statement.setString(6, block.originalData());
                    statement.setString(7, block.placedData());
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot save acceptance blocks", exception);
        }
    }

    public List<AcceptanceBlockSnapshot> loadBlocks(UUID runId) {
        List<AcceptanceBlockSnapshot> blocks = new ArrayList<>();
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT world, x, y, z, original_data, placed_data
                     FROM acceptance_block WHERE run_id = ?
                     """)) {
            statement.setString(1, runId.toString());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) blocks.add(new AcceptanceBlockSnapshot(runId, rows.getString("world"),
                        rows.getInt("x"), rows.getInt("y"), rows.getInt("z"),
                        rows.getString("original_data"), rows.getString("placed_data")));
            }
            return blocks;
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot load acceptance blocks", exception);
        }
    }

    public void deleteRun(UUID runId) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM acceptance_run WHERE id = ?")) {
            statement.setString(1, runId.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot delete acceptance run", exception);
        }
    }

    public void pruneCompletedRuns(int retainedRuns) {
        int retained = Math.max(1, retainedRuns);
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     DELETE FROM acceptance_run
                     WHERE state NOT IN ('preparing', 'preview')
                     AND id NOT IN (
                         SELECT id FROM acceptance_run
                         WHERE state NOT IN ('preparing', 'preview')
                         ORDER BY started_at DESC LIMIT ?
                     )
                     """)) {
            statement.setInt(1, retained);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot prune completed acceptance runs", exception);
        }
    }

    private AcceptanceRunSnapshot readRun(ResultSet row) throws SQLException {
        return new AcceptanceRunSnapshot(UUID.fromString(row.getString("id")),
                AcceptanceState.parse(row.getString("state")), row.getString("world"),
                row.getInt("center_x"), row.getInt("center_y"), row.getInt("center_z"),
                row.getInt("checks_passed"), row.getInt("checks_total"), row.getString("summary"),
                row.getLong("started_at"), row.getLong("updated_at"));
    }
}
