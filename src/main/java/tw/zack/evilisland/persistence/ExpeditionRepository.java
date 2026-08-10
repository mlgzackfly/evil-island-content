package tw.zack.evilisland.persistence;

import tw.zack.evilisland.model.ExpeditionOperation;
import tw.zack.evilisland.model.ExpeditionOutcome;
import tw.zack.evilisland.model.ExpeditionPhase;
import tw.zack.evilisland.model.ExpeditionRoute;
import tw.zack.evilisland.model.ExpeditionSnapshot;
import tw.zack.evilisland.model.ExpeditionStageSnapshot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ExpeditionRepository {
    private final DatabaseManager database;

    public ExpeditionRepository(DatabaseManager database) {
        this.database = database;
    }

    public List<ExpeditionSnapshot> loadActive() {
        List<ExpeditionSnapshot> result = new ArrayList<>();
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM expedition_instance WHERE outcome = '' ORDER BY started_at");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) result.add(read(rows));
            return List.copyOf(result);
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot load active expeditions", exception);
        }
    }

    public Optional<ExpeditionSnapshot> find(UUID id) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM expedition_instance WHERE id = ?")) {
            statement.setString(1, id.toString());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(read(rows)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot load expedition " + id, exception);
        }
    }

    public void save(ExpeditionSnapshot snapshot) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO expedition_instance(id, operation, route, phase, outcome, world, anchor_x,
                         anchor_y, anchor_z, leader, partner, companion, seed, approach_mask, clue_mask,
                         objective_mask, first_activator, objective_deadline, alert, enemies_remaining,
                         started_at, phase_started_at, completed_at, updated_at)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT(id) DO UPDATE SET operation=excluded.operation, route=excluded.route,
                         phase=excluded.phase, outcome=excluded.outcome, world=excluded.world,
                         anchor_x=excluded.anchor_x, anchor_y=excluded.anchor_y, anchor_z=excluded.anchor_z,
                         leader=excluded.leader, partner=excluded.partner, companion=excluded.companion,
                         seed=excluded.seed, approach_mask=excluded.approach_mask, clue_mask=excluded.clue_mask,
                         objective_mask=excluded.objective_mask, first_activator=excluded.first_activator,
                         objective_deadline=excluded.objective_deadline, alert=excluded.alert,
                         enemies_remaining=excluded.enemies_remaining, started_at=excluded.started_at,
                         phase_started_at=excluded.phase_started_at, completed_at=excluded.completed_at,
                         updated_at=excluded.updated_at
                     WHERE excluded.updated_at >= expedition_instance.updated_at
                     """)) {
            int column = 1;
            statement.setString(column++, snapshot.id().toString());
            statement.setString(column++, snapshot.operation().id());
            statement.setString(column++, snapshot.route().id());
            statement.setString(column++, snapshot.phase().id());
            statement.setString(column++, snapshot.outcome() == null ? "" : snapshot.outcome().id());
            statement.setString(column++, snapshot.world());
            statement.setDouble(column++, snapshot.anchorX());
            statement.setDouble(column++, snapshot.anchorY());
            statement.setDouble(column++, snapshot.anchorZ());
            statement.setString(column++, snapshot.leader().toString());
            statement.setString(column++, value(snapshot.partner()));
            statement.setString(column++, value(snapshot.companion()));
            statement.setLong(column++, snapshot.seed());
            statement.setInt(column++, snapshot.approachMask());
            statement.setInt(column++, snapshot.clueMask());
            statement.setInt(column++, snapshot.objectiveMask());
            statement.setString(column++, value(snapshot.firstActivator()));
            statement.setLong(column++, snapshot.objectiveDeadline());
            statement.setInt(column++, snapshot.alert());
            statement.setInt(column++, snapshot.enemiesRemaining());
            statement.setLong(column++, snapshot.startedAt());
            statement.setLong(column++, snapshot.phaseStartedAt());
            statement.setLong(column++, snapshot.completedAt());
            statement.setLong(column, snapshot.updatedAt());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot save expedition " + snapshot.id(), exception);
        }
    }

    public void beginStage(UUID expeditionId, ExpeditionPhase phase, long startedAt) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO expedition_stage_log(expedition_id, phase, started_at, completed_at)
                     VALUES (?, ?, ?, 0)
                     ON CONFLICT(expedition_id, phase) DO UPDATE SET started_at=excluded.started_at,
                         completed_at=0
                     """)) {
            statement.setString(1, expeditionId.toString());
            statement.setString(2, phase.id());
            statement.setLong(3, startedAt);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot begin expedition stage", exception);
        }
    }

    public void finishStage(UUID expeditionId, ExpeditionPhase phase, long completedAt) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE expedition_stage_log SET completed_at = ? WHERE expedition_id = ? AND phase = ?
                     """)) {
            statement.setLong(1, completedAt);
            statement.setString(2, expeditionId.toString());
            statement.setString(3, phase.id());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot finish expedition stage", exception);
        }
    }

    public List<ExpeditionStageSnapshot> stages(UUID expeditionId) {
        List<ExpeditionStageSnapshot> result = new ArrayList<>();
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT * FROM expedition_stage_log WHERE expedition_id = ? ORDER BY started_at
                     """)) {
            statement.setString(1, expeditionId.toString());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(new ExpeditionStageSnapshot(expeditionId,
                        ExpeditionPhase.parse(rows.getString("phase")), rows.getLong("started_at"),
                        rows.getLong("completed_at")));
            }
            return List.copyOf(result);
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot load expedition stages", exception);
        }
    }

    public long countByOutcome(ExpeditionOutcome outcome) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM expedition_instance WHERE outcome = ?")) {
            statement.setString(1, outcome.id());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getLong(1) : 0L;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot count expedition outcomes", exception);
        }
    }

    private ExpeditionSnapshot read(ResultSet rows) throws SQLException {
        ExpeditionOperation operation = ExpeditionOperation.parse(rows.getString("operation"));
        ExpeditionRoute route = ExpeditionRoute.parse(rows.getString("route"));
        ExpeditionPhase phase = ExpeditionPhase.parse(rows.getString("phase"));
        if (operation == null || route == null || phase == null) throw new SQLException("Invalid expedition row");
        return new ExpeditionSnapshot(UUID.fromString(rows.getString("id")), operation, route, phase,
                ExpeditionOutcome.parse(rows.getString("outcome")), rows.getString("world"),
                rows.getDouble("anchor_x"), rows.getDouble("anchor_y"), rows.getDouble("anchor_z"),
                UUID.fromString(rows.getString("leader")), uuid(rows.getString("partner")),
                uuid(rows.getString("companion")), rows.getLong("seed"), rows.getInt("approach_mask"),
                rows.getInt("clue_mask"), rows.getInt("objective_mask"),
                uuid(rows.getString("first_activator")), rows.getLong("objective_deadline"),
                rows.getInt("alert"), rows.getInt("enemies_remaining"), rows.getLong("started_at"),
                rows.getLong("phase_started_at"), rows.getLong("completed_at"), rows.getLong("updated_at"));
    }

    private String value(UUID id) { return id == null ? "" : id.toString(); }
    private UUID uuid(String value) { return value == null || value.isBlank() ? null : UUID.fromString(value); }
}
