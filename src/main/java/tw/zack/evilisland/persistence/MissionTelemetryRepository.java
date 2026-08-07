package tw.zack.evilisland.persistence;

import tw.zack.evilisland.model.MissionType;
import tw.zack.evilisland.model.PlayerActivitySnapshot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public final class MissionTelemetryRepository {
    private final DatabaseManager database;

    public MissionTelemetryRepository(DatabaseManager database) {
        this.database = database;
    }

    public void start(UUID id, MissionType type, int players, long startedAt, String payload) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO mission_telemetry(id, mission_type, players, started_at, payload)
                     VALUES (?, ?, ?, ?, ?) ON CONFLICT(id) DO NOTHING
                     """)) {
            statement.setString(1, id.toString());
            statement.setString(2, type.name().toLowerCase(java.util.Locale.ROOT));
            statement.setInt(3, Math.max(1, players));
            statement.setLong(4, startedAt);
            statement.setString(5, payload == null ? "{}" : payload);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot start mission telemetry", exception);
        }
    }

    public void finish(UUID id, String result, String failureReason, long completedAt) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE mission_telemetry SET completed_at = ?, result = ?, failure_reason = ?
                     WHERE id = ? AND result = 'active'
                     """)) {
            statement.setLong(1, completedAt);
            statement.setString(2, result);
            statement.setString(3, failureReason == null ? "" : failureReason);
            statement.setString(4, id.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot finish mission telemetry", exception);
        }
    }

    public void delete(UUID id) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM mission_telemetry WHERE id = ?")) {
            statement.setString(1, id.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot delete mission telemetry", exception);
        }
    }

    public Optional<PlayerActivitySnapshot> activity(UUID playerId) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT last_seen, last_catchup_cycle FROM player_activity WHERE player_uuid = ?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(new PlayerActivitySnapshot(playerId, row.getLong(1), row.getInt(2)))
                        : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot load player activity", exception);
        }
    }

    public void saveActivity(PlayerActivitySnapshot activity) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO player_activity(player_uuid, last_seen, last_catchup_cycle) VALUES (?, ?, ?)
                     ON CONFLICT(player_uuid) DO UPDATE SET last_seen=excluded.last_seen,
                     last_catchup_cycle=excluded.last_catchup_cycle
                     """)) {
            statement.setString(1, activity.playerId().toString());
            statement.setLong(2, activity.lastSeen());
            statement.setInt(3, activity.lastCatchupCycle());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot save player activity", exception);
        }
    }

    public int countByResult(String result) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM mission_telemetry WHERE result = ?")) {
            statement.setString(1, result);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? row.getInt(1) : 0;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot count mission telemetry", exception);
        }
    }
}
