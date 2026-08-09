package tw.zack.evilisland.persistence;

import tw.zack.evilisland.model.SupplyRouteSnapshot;
import tw.zack.evilisland.model.SupplyRouteState;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public final class SupplyRouteRepository {
    private final DatabaseManager database;

    public SupplyRouteRepository(DatabaseManager database) {
        this.database = database;
    }

    public Optional<SupplyRouteSnapshot> active() {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT * FROM supply_route WHERE state IN ('transit', 'arrived')
                     ORDER BY updated_at DESC LIMIT 1
                     """); ResultSet rows = statement.executeQuery()) {
            return rows.next() ? Optional.of(read(rows)) : Optional.empty();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot load active supply route", exception);
        }
    }

    public Optional<SupplyRouteSnapshot> find(UUID eventId) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM supply_route WHERE event_id = ?")) {
            statement.setString(1, eventId.toString());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(read(rows)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot load supply route", exception);
        }
    }

    public void save(SupplyRouteSnapshot route) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO supply_route(event_id, state, dispatcher, receiver, departed_at, arrives_at, updated_at)
                     VALUES (?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT(event_id) DO UPDATE SET state=excluded.state, dispatcher=excluded.dispatcher,
                     receiver=excluded.receiver, departed_at=excluded.departed_at, arrives_at=excluded.arrives_at,
                     updated_at=excluded.updated_at WHERE excluded.updated_at >= supply_route.updated_at
                     """)) {
            statement.setString(1, route.eventId().toString());
            statement.setString(2, route.state().id());
            statement.setString(3, route.dispatcher().toString());
            statement.setString(4, route.receiver() == null ? "" : route.receiver().toString());
            statement.setLong(5, route.departedAt());
            statement.setLong(6, route.arrivesAt());
            statement.setLong(7, route.updatedAt());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot save supply route", exception);
        }
    }

    private SupplyRouteSnapshot read(ResultSet rows) throws SQLException {
        SupplyRouteState state = SupplyRouteState.parse(rows.getString("state"));
        if (state == null) throw new SQLException("Invalid supply route state");
        String receiver = rows.getString("receiver");
        return new SupplyRouteSnapshot(UUID.fromString(rows.getString("event_id")), state,
                UUID.fromString(rows.getString("dispatcher")), receiver == null || receiver.isBlank()
                ? null : UUID.fromString(receiver), rows.getLong("departed_at"), rows.getLong("arrives_at"),
                rows.getLong("updated_at"));
    }
}
