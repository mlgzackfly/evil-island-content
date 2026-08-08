package tw.zack.evilisland.persistence;

import tw.zack.evilisland.model.EssenceSourceSnapshot;
import tw.zack.evilisland.model.InheritanceSnapshot;
import tw.zack.evilisland.model.InheritanceType;
import tw.zack.evilisland.model.PlayerGrowthSnapshot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class GrowthRepository {
    private final DatabaseManager database;

    public GrowthRepository(DatabaseManager database) {
        this.database = database;
    }

    public Optional<PlayerGrowthSnapshot> loadGrowth(UUID playerId) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT rejection, updated_at FROM player_growth WHERE player_uuid = ?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(new PlayerGrowthSnapshot(playerId,
                        row.getInt("rejection"), row.getLong("updated_at"))) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot load player growth", exception);
        }
    }

    public void saveGrowth(PlayerGrowthSnapshot state) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO player_growth(player_uuid, rejection, updated_at) VALUES (?, ?, ?)
                     ON CONFLICT(player_uuid) DO UPDATE SET rejection=excluded.rejection,
                     updated_at=excluded.updated_at
                     """)) {
            statement.setString(1, state.playerId().toString());
            statement.setInt(2, state.rejection());
            statement.setLong(3, state.updatedAt());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot save player growth", exception);
        }
    }

    public List<EssenceSourceSnapshot> loadSources(UUID playerId) {
        List<EssenceSourceSnapshot> result = new ArrayList<>();
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT source, amount, purity_points, updated_at
                     FROM player_essence_source WHERE player_uuid = ?
                     """)) {
            statement.setString(1, playerId.toString());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(new EssenceSourceSnapshot(playerId, rows.getString("source"),
                        rows.getInt("amount"), rows.getInt("purity_points"), rows.getLong("updated_at")));
            }
            return result;
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot load essence sources", exception);
        }
    }

    public void replaceSources(UUID playerId, List<EssenceSourceSnapshot> sources) {
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM player_essence_source WHERE player_uuid = ?");
                 PreparedStatement insert = connection.prepareStatement("""
                         INSERT INTO player_essence_source(player_uuid, source, amount, purity_points, updated_at)
                         VALUES (?, ?, ?, ?, ?)
                         """)) {
                delete.setString(1, playerId.toString());
                delete.executeUpdate();
                for (EssenceSourceSnapshot source : sources) {
                    if (source.amount() <= 0) continue;
                    insert.setString(1, playerId.toString());
                    insert.setString(2, source.source());
                    insert.setInt(3, source.amount());
                    insert.setInt(4, source.purityPoints());
                    insert.setLong(5, source.updatedAt());
                    insert.addBatch();
                }
                insert.executeBatch();
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot replace essence sources", exception);
        }
    }

    public Map<InheritanceType, InheritanceSnapshot> loadInheritances(UUID playerId) {
        Map<InheritanceType, InheritanceSnapshot> result = new EnumMap<>(InheritanceType.class);
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT inheritance, progress, completed, attuned, updated_at
                     FROM player_inheritance WHERE player_uuid = ?
                     """)) {
            statement.setString(1, playerId.toString());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    InheritanceType type = InheritanceType.parse(rows.getString("inheritance"));
                    if (type != null) result.put(type, new InheritanceSnapshot(playerId, type,
                            rows.getInt("progress"), rows.getInt("completed") != 0,
                            rows.getInt("attuned") != 0, rows.getLong("updated_at")));
                }
            }
            return result;
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot load inheritances", exception);
        }
    }

    public void replaceInheritances(UUID playerId, Map<InheritanceType, InheritanceSnapshot> states) {
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM player_inheritance WHERE player_uuid = ?");
                 PreparedStatement insert = connection.prepareStatement("""
                         INSERT INTO player_inheritance(player_uuid, inheritance, progress,
                         completed, attuned, updated_at) VALUES (?, ?, ?, ?, ?, ?)
                         """)) {
                delete.setString(1, playerId.toString());
                delete.executeUpdate();
                for (InheritanceSnapshot state : states.values()) {
                    insert.setString(1, playerId.toString());
                    insert.setString(2, state.inheritance().id());
                    insert.setInt(3, state.progress());
                    insert.setInt(4, state.completed() ? 1 : 0);
                    insert.setInt(5, state.attuned() ? 1 : 0);
                    insert.setLong(6, state.updatedAt());
                    insert.addBatch();
                }
                insert.executeBatch();
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot replace inheritances", exception);
        }
    }
}
