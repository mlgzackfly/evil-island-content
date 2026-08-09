package tw.zack.evilisland.persistence;

import tw.zack.evilisland.model.LivingEventApproach;
import tw.zack.evilisland.model.LivingEventSnapshot;
import tw.zack.evilisland.model.LivingEventState;
import tw.zack.evilisland.model.LivingEventType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class LivingEventRepository {
    private final DatabaseManager database;

    public LivingEventRepository(DatabaseManager database) {
        this.database = database;
    }

    public List<LivingEventSnapshot> findRecent(int limit) {
        List<LivingEventSnapshot> events = new ArrayList<>();
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT * FROM living_event ORDER BY created_at DESC LIMIT ?
                     """)) {
            statement.setInt(1, Math.max(1, limit));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) events.add(read(rows));
            }
            return List.copyOf(events);
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot load living events", exception);
        }
    }

    public Optional<LivingEventSnapshot> active() {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT * FROM living_event WHERE state = 'active' ORDER BY created_at DESC LIMIT 1
                     """);
             ResultSet rows = statement.executeQuery()) {
            return rows.next() ? Optional.of(read(rows)) : Optional.empty();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot load active living event", exception);
        }
    }

    public void save(LivingEventSnapshot event) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO living_event(id, type, state, approach, cycle, week, day,
                     started_epoch_day, expires_epoch_day, participants, created_at, resolved_at, updated_at)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT(id) DO UPDATE SET type=excluded.type, state=excluded.state,
                     approach=excluded.approach, cycle=excluded.cycle, week=excluded.week, day=excluded.day,
                     started_epoch_day=excluded.started_epoch_day, expires_epoch_day=excluded.expires_epoch_day,
                     participants=excluded.participants, created_at=excluded.created_at,
                     resolved_at=excluded.resolved_at, updated_at=excluded.updated_at
                     WHERE excluded.updated_at >= living_event.updated_at
                     """)) {
            statement.setString(1, event.id().toString());
            statement.setString(2, event.type().id());
            statement.setString(3, event.state().id());
            statement.setString(4, event.approach().id());
            statement.setInt(5, event.cycle());
            statement.setInt(6, event.week());
            statement.setInt(7, event.day());
            statement.setLong(8, event.startedEpochDay());
            statement.setLong(9, event.expiresEpochDay());
            statement.setInt(10, event.participants());
            statement.setLong(11, event.createdAt());
            statement.setLong(12, event.resolvedAt());
            statement.setLong(13, event.updatedAt());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot save living event", exception);
        }
    }

    public void delete(UUID id) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM living_event WHERE id = ?")) {
            statement.setString(1, id.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot delete living event", exception);
        }
    }

    public void prune(int retained) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     DELETE FROM living_event WHERE id NOT IN (
                         SELECT id FROM living_event ORDER BY created_at DESC LIMIT ?
                     ) AND state <> 'active'
                     """)) {
            statement.setInt(1, Math.max(12, retained));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot prune living events", exception);
        }
    }

    private LivingEventSnapshot read(ResultSet rows) throws SQLException {
        LivingEventType type = LivingEventType.parse(rows.getString("type"));
        LivingEventState state = LivingEventState.parse(rows.getString("state"));
        LivingEventApproach approach = LivingEventApproach.parse(rows.getString("approach"));
        if (type == null || state == null || approach == null) {
            throw new SQLException("Invalid living event enum value for " + rows.getString("id"));
        }
        return new LivingEventSnapshot(UUID.fromString(rows.getString("id")), type, state, approach,
                rows.getInt("cycle"), rows.getInt("week"), rows.getInt("day"),
                rows.getLong("started_epoch_day"), rows.getLong("expires_epoch_day"),
                rows.getInt("participants"), rows.getLong("created_at"),
                rows.getLong("resolved_at"), rows.getLong("updated_at"));
    }
}
