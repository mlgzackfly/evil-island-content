package tw.zack.evilisland.persistence;

import tw.zack.evilisland.model.NpcRole;
import tw.zack.evilisland.model.NpcRosterSnapshot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.Map;

public final class NpcRosterRepository {
    private final DatabaseManager database;

    public NpcRosterRepository(DatabaseManager database) {
        this.database = database;
    }

    public Map<NpcRole, NpcRosterSnapshot> findAll() {
        Map<NpcRole, NpcRosterSnapshot> result = new EnumMap<>(NpcRole.class);
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM npc_roster");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                NpcRole role = NpcRole.parse(rows.getString("role"));
                if (role == null) continue;
                result.put(role, new NpcRosterSnapshot(role, rows.getInt("fatigue"),
                        rows.getLong("injured_until"), rows.getLong("updated_at")));
            }
            return result;
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot load NPC roster", exception);
        }
    }

    public void save(NpcRosterSnapshot state) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO npc_roster(role, fatigue, injured_until, updated_at)
                     VALUES (?, ?, ?, ?)
                     ON CONFLICT(role) DO UPDATE SET
                         fatigue = excluded.fatigue,
                         injured_until = excluded.injured_until,
                         updated_at = excluded.updated_at
                     """)) {
            statement.setString(1, state.role().id());
            statement.setInt(2, state.fatigue());
            statement.setLong(3, state.injuredUntil());
            statement.setLong(4, state.updatedAt());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot save NPC roster", exception);
        }
    }
}
