package tw.zack.evilisland.persistence;

import tw.zack.evilisland.model.CityProject;
import tw.zack.evilisland.model.ConstructionBlockSnapshot;
import tw.zack.evilisland.model.ConstructionPlot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ConstructionRepository {
    private final DatabaseManager database;

    public ConstructionRepository(DatabaseManager database) {
        this.database = database;
    }

    public Map<CityProject, ConstructionPlot> loadPlots() {
        Map<CityProject, ConstructionPlot> plots = new EnumMap<>(CityProject.class);
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT project, world, x, y, z, rotation, level, status FROM construction_plot");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                CityProject project = CityProject.parse(rows.getString("project"));
                if (project != null) plots.put(project, new ConstructionPlot(project, rows.getString("world"),
                        rows.getInt("x"), rows.getInt("y"), rows.getInt("z"), rows.getInt("rotation"),
                        rows.getInt("level"), rows.getString("status")));
            }
            return plots;
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot load construction plots", exception);
        }
    }

    public List<ConstructionBlockSnapshot> loadBlocks(CityProject project) {
        List<ConstructionBlockSnapshot> blocks = new ArrayList<>();
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT world, x, y, z, original_data, placed_data
                     FROM construction_block WHERE project = ?
                     """)) {
            statement.setString(1, project.id());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) blocks.add(new ConstructionBlockSnapshot(project, rows.getString("world"),
                        rows.getInt("x"), rows.getInt("y"), rows.getInt("z"),
                        rows.getString("original_data"), rows.getString("placed_data")));
            }
            return blocks;
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot load construction blocks", exception);
        }
    }

    public void savePlot(ConstructionPlot plot) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO construction_plot(project, world, x, y, z, rotation, level, status)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT(project) DO UPDATE SET world=excluded.world, x=excluded.x, y=excluded.y,
                     z=excluded.z, rotation=excluded.rotation, level=excluded.level, status=excluded.status
                     """)) {
            statement.setString(1, plot.project().id());
            statement.setString(2, plot.world());
            statement.setInt(3, plot.x());
            statement.setInt(4, plot.y());
            statement.setInt(5, plot.z());
            statement.setInt(6, plot.rotation());
            statement.setInt(7, plot.level());
            statement.setString(8, plot.status());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot save construction plot", exception);
        }
    }

    public void saveBlocks(List<ConstructionBlockSnapshot> blocks) {
        if (blocks.isEmpty()) return;
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO construction_block(project, world, x, y, z, original_data, placed_data)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(project, world, x, y, z) DO UPDATE SET placed_data=excluded.placed_data
                    """)) {
                for (ConstructionBlockSnapshot block : blocks) {
                    statement.setString(1, block.project().id());
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
            throw new IllegalStateException("Cannot save construction blocks", exception);
        }
    }
}
