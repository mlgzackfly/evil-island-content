package tw.zack.evilisland.persistence;

import tw.zack.evilisland.model.CampaignSnapshot;
import tw.zack.evilisland.model.CampaignStrategy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public final class CampaignRepository {
    private final DatabaseManager database;

    public CampaignRepository(DatabaseManager database) {
        this.database = database;
    }

    public Optional<CampaignSnapshot> find() {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM campaign_state WHERE id = 1");
             ResultSet result = statement.executeQuery()) {
            if (!result.next()) return Optional.empty();
            return Optional.of(new CampaignSnapshot(
                    result.getInt("cycle"), result.getInt("week"), result.getInt("day"),
                    result.getInt("defense"), result.getInt("supply"), result.getInt("intelligence"),
                    result.getInt("morale"), result.getLong("epoch_day"),
                    result.getInt("completed_today") != 0, result.getString("completed_contract"),
                    result.getInt("weekly_resolved") != 0,
                    CampaignStrategy.parse(result.getString("weekly_strategy")),
                    result.getInt("fortify_points"), result.getInt("provision_points"),
                    result.getInt("recon_points"),
                    result.getLong("updated_at")
            ));
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot load campaign state", exception);
        }
    }

    public void save(CampaignSnapshot state) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO campaign_state(id, cycle, week, day, defense, supply, intelligence, morale,
                                                epoch_day, completed_today, completed_contract, weekly_resolved,
                                                weekly_strategy, fortify_points, provision_points, recon_points,
                                                updated_at)
                     VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT(id) DO UPDATE SET
                         cycle = excluded.cycle,
                         week = excluded.week,
                         day = excluded.day,
                         defense = excluded.defense,
                         supply = excluded.supply,
                         intelligence = excluded.intelligence,
                         morale = excluded.morale,
                         epoch_day = excluded.epoch_day,
                         completed_today = excluded.completed_today,
                         completed_contract = excluded.completed_contract,
                         weekly_resolved = excluded.weekly_resolved,
                         weekly_strategy = excluded.weekly_strategy,
                         fortify_points = excluded.fortify_points,
                         provision_points = excluded.provision_points,
                         recon_points = excluded.recon_points,
                         updated_at = excluded.updated_at
                     """)) {
            statement.setInt(1, state.cycle());
            statement.setInt(2, state.week());
            statement.setInt(3, state.day());
            statement.setInt(4, state.defense());
            statement.setInt(5, state.supply());
            statement.setInt(6, state.intelligence());
            statement.setInt(7, state.morale());
            statement.setLong(8, state.epochDay());
            statement.setInt(9, state.completedToday() ? 1 : 0);
            statement.setString(10, state.completedContract());
            statement.setInt(11, state.weeklyResolved() ? 1 : 0);
            statement.setString(12, state.weeklyStrategy().id());
            statement.setInt(13, state.fortifyPoints());
            statement.setInt(14, state.provisionPoints());
            statement.setInt(15, state.reconPoints());
            statement.setLong(16, state.updatedAt());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot save campaign state", exception);
        }
    }
}
