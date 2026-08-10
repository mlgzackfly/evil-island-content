package tw.zack.evilisland.persistence;

import tw.zack.evilisland.model.JourneySnapshot;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public final class JourneyRepository {
    private final DatabaseManager database;

    public JourneyRepository(DatabaseManager database) {
        this.database = database;
    }

    public Optional<JourneySnapshot> find(UUID playerId) {
        try (var connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM player_journey WHERE player_uuid = ?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(new JourneySnapshot(playerId, rows.getInt("milestone_mask"),
                        rows.getLong("started_at"), rows.getLong("updated_at"))) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot load player journey", exception);
        }
    }

    public void save(JourneySnapshot snapshot) {
        try (var connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO player_journey(player_uuid, milestone_mask, started_at, updated_at)
                     VALUES (?, ?, ?, ?)
                     ON CONFLICT(player_uuid) DO UPDATE SET milestone_mask=excluded.milestone_mask,
                         started_at=MIN(player_journey.started_at, excluded.started_at),
                         updated_at=excluded.updated_at
                     WHERE excluded.updated_at >= player_journey.updated_at
                     """)) {
            statement.setString(1, snapshot.playerId().toString());
            statement.setInt(2, snapshot.milestoneMask());
            statement.setLong(3, snapshot.startedAt());
            statement.setLong(4, snapshot.updatedAt());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot save player journey", exception);
        }
    }

    public void delete(UUID playerId) {
        try (var connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM player_journey WHERE player_uuid = ?")) {
            statement.setString(1, playerId.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot reset player journey", exception);
        }
    }
}
