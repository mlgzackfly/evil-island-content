package tw.zack.evilisland.persistence;

import tw.zack.evilisland.model.ExpeditionCampBlockSnapshot;
import tw.zack.evilisland.model.ExplorationSite;
import tw.zack.evilisland.model.RegionControlSnapshot;
import tw.zack.evilisland.model.RegionState;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class RegionControlRepository {
    private final DatabaseManager database;

    public RegionControlRepository(DatabaseManager database) {
        this.database = database;
    }

    public Map<ExplorationSite, RegionControlSnapshot> loadAll() {
        EnumMap<ExplorationSite, RegionControlSnapshot> result = new EnumMap<>(ExplorationSite.class);
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM region_control");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                RegionControlSnapshot snapshot = read(rows);
                result.put(snapshot.site(), snapshot);
            }
            return Map.copyOf(result);
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot load region control", exception);
        }
    }

    public void save(RegionControlSnapshot snapshot) {
        try (Connection connection = database.openConnection()) {
            save(connection, snapshot);
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot save region control", exception);
        }
    }

    public boolean applyEffect(String effectId, String source, int delta, RegionControlSnapshot snapshot) {
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement effect = connection.prepareStatement("""
                    INSERT OR IGNORE INTO region_control_effect(effect_id, site, source, delta, created_at)
                    VALUES (?, ?, ?, ?, ?)
                    """)) {
                effect.setString(1, effectId);
                effect.setString(2, snapshot.site().id());
                effect.setString(3, source);
                effect.setInt(4, delta);
                effect.setLong(5, snapshot.updatedAt());
                if (effect.executeUpdate() == 0) {
                    connection.rollback();
                    return false;
                }
                save(connection, snapshot);
                connection.commit();
                return true;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot apply region effect", exception);
        }
    }

    public List<ExpeditionCampBlockSnapshot> loadBlocks(ExplorationSite site) {
        List<ExpeditionCampBlockSnapshot> result = new ArrayList<>();
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM expedition_camp_block WHERE site = ? ORDER BY y, x, z")) {
            statement.setString(1, site.id());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(readBlock(rows));
            }
            return List.copyOf(result);
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot load expedition camp blocks", exception);
        }
    }

    public void saveBlocks(List<ExpeditionCampBlockSnapshot> blocks) {
        if (blocks.isEmpty()) return;
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO expedition_camp_block(site, world, x, y, z, original_data, level_one_data,
                         level_two_data, lost_data, placed_data) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT(site, world, x, y, z) DO UPDATE SET
                         original_data=excluded.original_data, level_one_data=excluded.level_one_data,
                         level_two_data=excluded.level_two_data, lost_data=excluded.lost_data,
                         placed_data=excluded.placed_data
                     """)) {
            for (ExpeditionCampBlockSnapshot block : blocks) {
                statement.setString(1, block.site().id());
                statement.setString(2, block.world());
                statement.setInt(3, block.x());
                statement.setInt(4, block.y());
                statement.setInt(5, block.z());
                statement.setString(6, block.originalData());
                statement.setString(7, block.levelOneData());
                statement.setString(8, block.levelTwoData());
                statement.setString(9, block.lostData());
                statement.setString(10, block.placedData());
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot save expedition camp blocks", exception);
        }
    }

    private void save(Connection connection, RegionControlSnapshot snapshot) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO region_control(site, state, stability, camp_level, supplies, world, x, y, z, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(site) DO UPDATE SET state=excluded.state, stability=excluded.stability,
                    camp_level=excluded.camp_level, supplies=excluded.supplies, world=excluded.world,
                    x=excluded.x, y=excluded.y, z=excluded.z, updated_at=excluded.updated_at
                WHERE excluded.updated_at >= region_control.updated_at
                """)) {
            statement.setString(1, snapshot.site().id());
            statement.setString(2, snapshot.state().id());
            statement.setInt(3, snapshot.stability());
            statement.setInt(4, snapshot.campLevel());
            statement.setInt(5, snapshot.supplies());
            statement.setString(6, snapshot.world());
            statement.setInt(7, snapshot.x());
            statement.setInt(8, snapshot.y());
            statement.setInt(9, snapshot.z());
            statement.setLong(10, snapshot.updatedAt());
            statement.executeUpdate();
        }
    }

    private RegionControlSnapshot read(ResultSet rows) throws SQLException {
        ExplorationSite site = ExplorationSite.parse(rows.getString("site"));
        RegionState state = RegionState.parse(rows.getString("state"));
        if (site == null || state == null) throw new SQLException("Invalid region control row");
        return new RegionControlSnapshot(site, state, rows.getInt("stability"), rows.getInt("camp_level"),
                rows.getInt("supplies"), rows.getString("world"), rows.getInt("x"), rows.getInt("y"),
                rows.getInt("z"), rows.getLong("updated_at"));
    }

    private ExpeditionCampBlockSnapshot readBlock(ResultSet rows) throws SQLException {
        ExplorationSite site = ExplorationSite.parse(rows.getString("site"));
        if (site == null) throw new SQLException("Invalid expedition camp site");
        return new ExpeditionCampBlockSnapshot(site, rows.getString("world"), rows.getInt("x"), rows.getInt("y"),
                rows.getInt("z"), rows.getString("original_data"), rows.getString("level_one_data"),
                rows.getString("level_two_data"), rows.getString("lost_data"), rows.getString("placed_data"));
    }
}
