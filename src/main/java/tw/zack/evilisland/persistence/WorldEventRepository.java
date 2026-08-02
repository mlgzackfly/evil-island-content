package tw.zack.evilisland.persistence;

import tw.zack.evilisland.model.WorldEventSnapshot;
import tw.zack.evilisland.model.WorldEventState;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class WorldEventRepository {
    private final DatabaseManager database;

    public WorldEventRepository(DatabaseManager database) {
        this.database = database;
    }

    public List<WorldEventSnapshot> findAll() {
        List<WorldEventSnapshot> events = new ArrayList<>();
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM world_event");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                read(result).ifPresent(events::add);
            }
            return events;
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot load world events", exception);
        }
    }

    public Optional<WorldEventSnapshot> find(UUID id) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM world_event WHERE id = ?")) {
            statement.setString(1, id.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? read(result) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot load world event " + id, exception);
        }
    }

    public void save(WorldEventSnapshot event) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO world_event(id, type, state, world, anchor_x, anchor_y, anchor_z, payload, updated_at)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT(id) DO UPDATE SET
                         type = excluded.type,
                         state = excluded.state,
                         world = excluded.world,
                         anchor_x = excluded.anchor_x,
                         anchor_y = excluded.anchor_y,
                         anchor_z = excluded.anchor_z,
                         payload = excluded.payload,
                         updated_at = excluded.updated_at
                     """)) {
            statement.setString(1, event.id().toString());
            statement.setString(2, event.type());
            statement.setString(3, event.state().name());
            statement.setString(4, event.world().toString());
            statement.setDouble(5, event.anchorX());
            statement.setDouble(6, event.anchorY());
            statement.setDouble(7, event.anchorZ());
            statement.setString(8, event.payload());
            statement.setLong(9, event.updatedAt());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot save world event " + event.id(), exception);
        }
    }

    public void delete(UUID id) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM world_event WHERE id = ?")) {
            statement.setString(1, id.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot delete world event " + id, exception);
        }
    }

    private Optional<WorldEventSnapshot> read(ResultSet result) throws SQLException {
        try {
            return Optional.of(new WorldEventSnapshot(
                    UUID.fromString(result.getString("id")),
                    result.getString("type"),
                    WorldEventState.valueOf(result.getString("state")),
                    UUID.fromString(result.getString("world")),
                    result.getDouble("anchor_x"),
                    result.getDouble("anchor_y"),
                    result.getDouble("anchor_z"),
                    result.getString("payload"),
                    result.getLong("updated_at")
            ));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
