package tw.zack.evilisland.persistence;

import tw.zack.evilisland.model.BossVariant;
import tw.zack.evilisland.model.CycleHistorySnapshot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class CycleArchiveRepository {
    private final DatabaseManager database;

    public CycleArchiveRepository(DatabaseManager database) {
        this.database = database;
    }

    public void recordBoss(int cycle, BossVariant variant, long engagedAt) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO cycle_boss_history(cycle, variant, engaged_at) VALUES (?, ?, ?)
                     ON CONFLICT(cycle) DO NOTHING
                     """)) {
            statement.setInt(1, cycle);
            statement.setString(2, variant.id());
            statement.setLong(3, engagedAt);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot record cycle boss", exception);
        }
    }

    public Optional<BossVariant> boss(int cycle) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT variant FROM cycle_boss_history WHERE cycle = ?")) {
            statement.setInt(1, cycle);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.ofNullable(BossVariant.parse(rows.getString(1))) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot load cycle boss", exception);
        }
    }

    public List<CycleHistorySnapshot> recent(int limit) {
        List<CycleHistorySnapshot> history = new ArrayList<>();
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT h.cycle, h.ending, h.summary, h.completed_at, b.variant, b.engaged_at
                     FROM cycle_history h LEFT JOIN cycle_boss_history b ON b.cycle = h.cycle
                     ORDER BY h.cycle DESC LIMIT ?
                     """)) {
            statement.setInt(1, Math.max(1, limit));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) history.add(new CycleHistorySnapshot(rows.getInt("cycle"),
                        rows.getString("ending"), rows.getString("summary"), rows.getLong("completed_at"),
                        BossVariant.parse(rows.getString("variant")), rows.getLong("engaged_at")));
            }
            return List.copyOf(history);
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot load cycle archive", exception);
        }
    }
}
