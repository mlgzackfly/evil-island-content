package tw.zack.evilisland.persistence;

import tw.zack.evilisland.model.ExpeditionOperation;
import tw.zack.evilisland.model.ExpeditionOutcome;
import tw.zack.evilisland.model.ExpeditionPhase;
import tw.zack.evilisland.model.ExpeditionRoute;
import tw.zack.evilisland.model.ExpeditionSnapshot;
import tw.zack.evilisland.model.ExpeditionStageSnapshot;
import tw.zack.evilisland.model.ExpeditionRunStateSnapshot;
import tw.zack.evilisland.model.ExpeditionConsequenceSnapshot;
import tw.zack.evilisland.model.ExpeditionRegionProgressSnapshot;
import tw.zack.evilisland.model.ExpeditionStoryChoice;
import tw.zack.evilisland.model.ExpeditionStoryDecisionSnapshot;
import tw.zack.evilisland.model.ExpeditionStoryProgressSnapshot;
import tw.zack.evilisland.model.ExpeditionStoryResolution;
import tw.zack.evilisland.model.ExpeditionStoryRules;
import tw.zack.evilisland.model.ExplorationSite;

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

    public Optional<ExpeditionRunStateSnapshot> state(UUID expeditionId) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM expedition_run_state WHERE expedition_id = ?")) {
            statement.setString(1, expeditionId.toString());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                ExplorationSite site = ExplorationSite.parse(rows.getString("site"));
                if (site == null) throw new SQLException("Invalid expedition site");
                return Optional.of(new ExpeditionRunStateSnapshot(expeditionId, site, rows.getInt("kit_mask"),
                        rows.getInt("event_mask"), rows.getInt("event_score"), rows.getInt("story_chapter"),
                        ExpeditionStoryChoice.parse(rows.getString("story_previous_choice")),
                        rows.getLong("updated_at")));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot load expedition state", exception);
        }
    }

    public void saveState(ExpeditionRunStateSnapshot snapshot) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO expedition_run_state(expedition_id, site, kit_mask, event_mask, event_score,
                         story_chapter, story_previous_choice, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT(expedition_id) DO UPDATE SET site=excluded.site, kit_mask=excluded.kit_mask,
                         event_mask=excluded.event_mask, event_score=excluded.event_score,
                         story_chapter=excluded.story_chapter,
                         story_previous_choice=excluded.story_previous_choice,
                         updated_at=excluded.updated_at
                     WHERE excluded.updated_at >= expedition_run_state.updated_at
                     """)) {
            statement.setString(1, snapshot.expeditionId().toString());
            statement.setString(2, snapshot.site().id());
            statement.setInt(3, snapshot.kitMask());
            statement.setInt(4, snapshot.eventMask());
            statement.setInt(5, snapshot.eventScore());
            statement.setInt(6, snapshot.storyChapter());
            statement.setString(7, snapshot.previousStoryChoice() == null
                    ? "" : snapshot.previousStoryChoice().id());
            statement.setLong(8, snapshot.updatedAt());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot save expedition state", exception);
        }
    }

    public boolean weeklyRewardAvailable(ExplorationSite site, ExpeditionRoute route, int cycle, int week) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT 1 FROM expedition_weekly_reward WHERE site = ? AND route = ? AND cycle = ? AND week = ?
                     """)) {
            statement.setString(1, site.id());
            statement.setString(2, route.id());
            statement.setInt(3, cycle);
            statement.setInt(4, week);
            try (ResultSet rows = statement.executeQuery()) {
                return !rows.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot inspect weekly expedition reward", exception);
        }
    }

    public boolean claimWeeklyReward(ExplorationSite site, ExpeditionRoute route, int cycle, int week,
                                     UUID expeditionId, long now) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT OR IGNORE INTO expedition_weekly_reward(site, route, cycle, week, expedition_id,
                         claimed_at) VALUES (?, ?, ?, ?, ?, ?)
                     """)) {
            statement.setString(1, site.id());
            statement.setString(2, route.id());
            statement.setInt(3, cycle);
            statement.setInt(4, week);
            statement.setString(5, expeditionId.toString());
            statement.setLong(6, now);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot claim weekly expedition reward", exception);
        }
    }

    public List<ExpeditionConsequenceSnapshot> consequences() {
        List<ExpeditionConsequenceSnapshot> result = new ArrayList<>();
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM expedition_consequence");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                ExplorationSite site = ExplorationSite.parse(rows.getString("site"));
                ExpeditionOperation operation = ExpeditionOperation.parse(rows.getString("operation"));
                ExpeditionOutcome outcome = ExpeditionOutcome.parse(rows.getString("outcome"));
                if (site == null || operation == null || outcome == null) throw new SQLException("Invalid consequence");
                result.add(new ExpeditionConsequenceSnapshot(site,
                        UUID.fromString(rows.getString("expedition_id")), operation, outcome,
                        rows.getString("world"), rows.getDouble("x"), rows.getDouble("y"), rows.getDouble("z"),
                        rows.getLong("updated_at")));
            }
            return List.copyOf(result);
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot load expedition consequences", exception);
        }
    }

    public void saveConsequence(ExpeditionConsequenceSnapshot snapshot) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO expedition_consequence(site, expedition_id, operation, outcome, world, x, y, z,
                         updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT(site) DO UPDATE SET expedition_id=excluded.expedition_id,
                         operation=excluded.operation, outcome=excluded.outcome, world=excluded.world,
                         x=excluded.x, y=excluded.y, z=excluded.z, updated_at=excluded.updated_at
                     WHERE excluded.updated_at >= expedition_consequence.updated_at
                     """)) {
            statement.setString(1, snapshot.site().id());
            statement.setString(2, snapshot.expeditionId().toString());
            statement.setString(3, snapshot.operation().id());
            statement.setString(4, snapshot.outcome().id());
            statement.setString(5, snapshot.world());
            statement.setDouble(6, snapshot.x());
            statement.setDouble(7, snapshot.y());
            statement.setDouble(8, snapshot.z());
            statement.setLong(9, snapshot.updatedAt());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot save expedition consequence", exception);
        }
    }

    public void recordRegionOutcome(ExplorationSite site, ExpeditionOperation operation,
                                    ExpeditionOutcome outcome, long now) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO expedition_region_progress(site, completed, partial, withdrawn, abandoned,
                         last_operation, last_outcome, updated_at)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT(site) DO UPDATE SET
                         completed=expedition_region_progress.completed + excluded.completed,
                         partial=expedition_region_progress.partial + excluded.partial,
                         withdrawn=expedition_region_progress.withdrawn + excluded.withdrawn,
                         abandoned=expedition_region_progress.abandoned + excluded.abandoned,
                         last_operation=excluded.last_operation, last_outcome=excluded.last_outcome,
                         updated_at=excluded.updated_at
                     """)) {
            statement.setString(1, site.id());
            statement.setInt(2, outcome == ExpeditionOutcome.COMPLETE ? 1 : 0);
            statement.setInt(3, outcome == ExpeditionOutcome.PARTIAL ? 1 : 0);
            statement.setInt(4, outcome == ExpeditionOutcome.WITHDRAWN ? 1 : 0);
            statement.setInt(5, outcome == ExpeditionOutcome.ABANDONED ? 1 : 0);
            statement.setString(6, operation.id());
            statement.setString(7, outcome.id());
            statement.setLong(8, now);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot record expedition region outcome", exception);
        }
    }

    public List<ExpeditionRegionProgressSnapshot> regionProgress() {
        List<ExpeditionRegionProgressSnapshot> result = new ArrayList<>();
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM expedition_region_progress ORDER BY site");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                ExplorationSite site = ExplorationSite.parse(rows.getString("site"));
                ExpeditionOperation operation = ExpeditionOperation.parse(rows.getString("last_operation"));
                ExpeditionOutcome outcome = ExpeditionOutcome.parse(rows.getString("last_outcome"));
                if (site == null || operation == null || outcome == null) throw new SQLException("Invalid progress");
                result.add(new ExpeditionRegionProgressSnapshot(site, rows.getInt("completed"),
                        rows.getInt("partial"), rows.getInt("withdrawn"), rows.getInt("abandoned"), operation,
                        outcome, rows.getLong("updated_at")));
            }
            return List.copyOf(result);
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot load expedition region progress", exception);
        }
    }

    public ExpeditionStoryProgressSnapshot storyProgress(ExplorationSite site) {
        try (Connection connection = database.openConnection()) {
            ExpeditionStoryProgressSnapshot progress = readStoryProgress(connection, site);
            return progress == null ? ExpeditionStoryProgressSnapshot.initial(site) : progress;
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot load expedition story progress", exception);
        }
    }

    public List<ExpeditionStoryProgressSnapshot> storyProgress() {
        List<ExpeditionStoryProgressSnapshot> result = new ArrayList<>();
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM expedition_story_progress ORDER BY site");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) result.add(readStoryProgress(rows));
            return List.copyOf(result);
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot load expedition story progress", exception);
        }
    }

    public Optional<ExpeditionStoryDecisionSnapshot> lastStoryDecision(UUID playerId, ExplorationSite site) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT * FROM expedition_story_decision
                     WHERE site = ? AND (leader = ? OR partner = ?)
                     ORDER BY decided_at DESC LIMIT 1
                     """)) {
            statement.setString(1, site.id());
            statement.setString(2, playerId.toString());
            statement.setString(3, playerId.toString());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(readStoryDecision(rows)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot load player expedition story decision", exception);
        }
    }

    public ExpeditionStoryResolution recordStoryDecision(ExpeditionStoryDecisionSnapshot decision) {
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try {
                int inserted;
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT OR IGNORE INTO expedition_story_decision(expedition_id, site, chapter, choice,
                            leader, partner, cycle, week, decided_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    statement.setString(1, decision.expeditionId().toString());
                    statement.setString(2, decision.site().id());
                    statement.setInt(3, decision.chapter());
                    statement.setString(4, decision.choice().id());
                    statement.setString(5, decision.leader().toString());
                    statement.setString(6, value(decision.partner()));
                    statement.setInt(7, decision.cycle());
                    statement.setInt(8, decision.week());
                    statement.setLong(9, decision.decidedAt());
                    inserted = statement.executeUpdate();
                }
                ExpeditionStoryProgressSnapshot current = readStoryProgress(connection, decision.site());
                if (current == null) current = ExpeditionStoryProgressSnapshot.initial(decision.site());
                boolean advanced = inserted == 1 && ExpeditionStoryRules.canAdvance(current,
                        decision.chapter(), decision.cycle(), decision.week());
                ExpeditionStoryProgressSnapshot result = current;
                if (advanced) {
                    result = ExpeditionStoryRules.advance(current, decision.choice(), decision.cycle(),
                            decision.week(), decision.decidedAt());
                    saveStoryProgress(connection, result);
                }
                connection.commit();
                return new ExpeditionStoryResolution(inserted == 1, advanced, result);
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot record expedition story decision", exception);
        }
    }

    private void saveStoryProgress(Connection connection, ExpeditionStoryProgressSnapshot snapshot)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO expedition_story_progress(site, chapter, completed, secure_choices,
                    connect_choices, last_choice, last_cycle, last_week, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(site) DO UPDATE SET chapter=excluded.chapter, completed=excluded.completed,
                    secure_choices=excluded.secure_choices, connect_choices=excluded.connect_choices,
                    last_choice=excluded.last_choice, last_cycle=excluded.last_cycle,
                    last_week=excluded.last_week, updated_at=excluded.updated_at
                WHERE excluded.updated_at >= expedition_story_progress.updated_at
                """)) {
            statement.setString(1, snapshot.site().id());
            statement.setInt(2, snapshot.chapter());
            statement.setInt(3, snapshot.completed() ? 1 : 0);
            statement.setInt(4, snapshot.secureChoices());
            statement.setInt(5, snapshot.connectChoices());
            statement.setString(6, snapshot.lastChoice() == null ? "" : snapshot.lastChoice().id());
            statement.setInt(7, snapshot.lastCycle());
            statement.setInt(8, snapshot.lastWeek());
            statement.setLong(9, snapshot.updatedAt());
            statement.executeUpdate();
        }
    }

    private ExpeditionStoryProgressSnapshot readStoryProgress(Connection connection, ExplorationSite site)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM expedition_story_progress WHERE site = ?")) {
            statement.setString(1, site.id());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? readStoryProgress(rows) : null;
            }
        }
    }

    private ExpeditionStoryProgressSnapshot readStoryProgress(ResultSet rows) throws SQLException {
        ExplorationSite site = ExplorationSite.parse(rows.getString("site"));
        if (site == null) throw new SQLException("Invalid expedition story site");
        return new ExpeditionStoryProgressSnapshot(site, rows.getInt("chapter"),
                rows.getInt("completed") != 0, rows.getInt("secure_choices"), rows.getInt("connect_choices"),
                ExpeditionStoryChoice.parse(rows.getString("last_choice")), rows.getInt("last_cycle"),
                rows.getInt("last_week"), rows.getLong("updated_at"));
    }

    private ExpeditionStoryDecisionSnapshot readStoryDecision(ResultSet rows) throws SQLException {
        ExplorationSite site = ExplorationSite.parse(rows.getString("site"));
        ExpeditionStoryChoice choice = ExpeditionStoryChoice.parse(rows.getString("choice"));
        if (site == null || choice == null) throw new SQLException("Invalid expedition story decision");
        return new ExpeditionStoryDecisionSnapshot(UUID.fromString(rows.getString("expedition_id")), site,
                rows.getInt("chapter"), choice, UUID.fromString(rows.getString("leader")),
                uuid(rows.getString("partner")), rows.getInt("cycle"), rows.getInt("week"),
                rows.getLong("decided_at"));
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
