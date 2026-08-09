package tw.zack.evilisland.persistence;

import tw.zack.evilisland.model.CrisisSceneBlockSnapshot;
import tw.zack.evilisland.model.CrisisSceneSnapshot;
import tw.zack.evilisland.model.CrisisSceneState;
import tw.zack.evilisland.model.LivingEventType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CrisisSceneRepository {
    private final DatabaseManager database;

    public CrisisSceneRepository(DatabaseManager database) {
        this.database = database;
    }

    public Map<UUID, CrisisSceneSnapshot> loadScenes() {
        Map<UUID, CrisisSceneSnapshot> scenes = new HashMap<>();
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM crisis_scene");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                CrisisSceneSnapshot scene = readScene(rows);
                scenes.put(scene.eventId(), scene);
            }
            return Map.copyOf(scenes);
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot load crisis scenes", exception);
        }
    }

    public List<CrisisSceneBlockSnapshot> loadBlocks(UUID eventId) {
        List<CrisisSceneBlockSnapshot> blocks = new ArrayList<>();
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT * FROM crisis_scene_block WHERE event_id = ?
                     """)) {
            statement.setString(1, eventId.toString());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) blocks.add(readBlock(rows));
            }
            return List.copyOf(blocks);
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot load crisis scene blocks", exception);
        }
    }

    public void saveScene(CrisisSceneSnapshot scene) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO crisis_scene(event_id, type, state, world, x, y, z, updated_at)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT(event_id) DO UPDATE SET type=excluded.type, state=excluded.state,
                     world=excluded.world, x=excluded.x, y=excluded.y, z=excluded.z,
                     updated_at=excluded.updated_at
                     WHERE excluded.updated_at >= crisis_scene.updated_at
                     """)) {
            statement.setString(1, scene.eventId().toString());
            statement.setString(2, scene.type().id());
            statement.setString(3, scene.state().id());
            statement.setString(4, scene.world());
            statement.setInt(5, scene.x());
            statement.setInt(6, scene.y());
            statement.setInt(7, scene.z());
            statement.setLong(8, scene.updatedAt());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot save crisis scene", exception);
        }
    }

    public void saveBlocks(List<CrisisSceneBlockSnapshot> blocks) {
        if (blocks.isEmpty()) return;
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO crisis_scene_block(event_id, world, x, y, z, original_data,
                    active_data, resolved_data, expired_data, placed_data)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(event_id, world, x, y, z) DO UPDATE SET
                    original_data=excluded.original_data, active_data=excluded.active_data,
                    resolved_data=excluded.resolved_data, expired_data=excluded.expired_data,
                    placed_data=excluded.placed_data
                    """)) {
                for (CrisisSceneBlockSnapshot block : blocks) {
                    statement.setString(1, block.eventId().toString());
                    statement.setString(2, block.world());
                    statement.setInt(3, block.x());
                    statement.setInt(4, block.y());
                    statement.setInt(5, block.z());
                    statement.setString(6, block.originalData());
                    statement.setString(7, block.activeData());
                    statement.setString(8, block.resolvedData());
                    statement.setString(9, block.expiredData());
                    statement.setString(10, block.placedData());
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
            throw new IllegalStateException("Cannot save crisis scene blocks", exception);
        }
    }

    public void deleteScene(UUID eventId) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM crisis_scene WHERE event_id = ?")) {
            statement.setString(1, eventId.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot delete crisis scene", exception);
        }
    }

    private CrisisSceneSnapshot readScene(ResultSet rows) throws SQLException {
        LivingEventType type = LivingEventType.parse(rows.getString("type"));
        CrisisSceneState state = CrisisSceneState.parse(rows.getString("state"));
        if (type == null || state == null) throw new SQLException("Invalid crisis scene enum value");
        return new CrisisSceneSnapshot(UUID.fromString(rows.getString("event_id")), type, state,
                rows.getString("world"), rows.getInt("x"), rows.getInt("y"), rows.getInt("z"),
                rows.getLong("updated_at"));
    }

    private CrisisSceneBlockSnapshot readBlock(ResultSet rows) throws SQLException {
        return new CrisisSceneBlockSnapshot(UUID.fromString(rows.getString("event_id")), rows.getString("world"),
                rows.getInt("x"), rows.getInt("y"), rows.getInt("z"), rows.getString("original_data"),
                rows.getString("active_data"), rows.getString("resolved_data"), rows.getString("expired_data"),
                rows.getString("placed_data"));
    }
}
