package tw.zack.evilisland.persistence;

import tw.zack.evilisland.model.IntelReportSnapshot;
import tw.zack.evilisland.model.ResidentRole;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ResidentIntelRepository {
    private final DatabaseManager database;

    public ResidentIntelRepository(DatabaseManager database) {
        this.database = database;
    }

    public List<IntelReportSnapshot> load(UUID eventId) {
        List<IntelReportSnapshot> reports = new ArrayList<>();
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM resident_intel WHERE event_id = ?")) {
            statement.setString(1, eventId.toString());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    ResidentRole role = ResidentRole.parse(rows.getString("resident"));
                    if (role == null) throw new SQLException("Invalid resident role");
                    reports.add(new IntelReportSnapshot(eventId, role,
                            UUID.fromString(rows.getString("reporter")), rows.getLong("collected_at")));
                }
            }
            return List.copyOf(reports);
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot load resident intel", exception);
        }
    }

    public boolean add(IntelReportSnapshot report) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT OR IGNORE INTO resident_intel(event_id, resident, reporter, collected_at)
                     VALUES (?, ?, ?, ?)
                     """)) {
            statement.setString(1, report.eventId().toString());
            statement.setString(2, report.resident().id());
            statement.setString(3, report.reporter().toString());
            statement.setLong(4, report.collectedAt());
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot save resident intel", exception);
        }
    }
}
