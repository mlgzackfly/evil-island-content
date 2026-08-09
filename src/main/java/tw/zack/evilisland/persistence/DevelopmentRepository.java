package tw.zack.evilisland.persistence;

import tw.zack.evilisland.model.CityProject;
import tw.zack.evilisland.model.CityRoute;
import tw.zack.evilisland.model.EventChain;
import tw.zack.evilisland.model.ExplorationSite;
import tw.zack.evilisland.model.Faction;
import tw.zack.evilisland.model.TechniquePath;
import tw.zack.evilisland.model.WeaponMasterySnapshot;
import tw.zack.evilisland.model.WeaponType;
import tw.zack.evilisland.model.WorldDevelopmentSnapshot;
import tw.zack.evilisland.model.WorldResource;
import tw.zack.evilisland.model.ProjectConditionSnapshot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class DevelopmentRepository {
    private final DatabaseManager database;

    public DevelopmentRepository(DatabaseManager database) {
        this.database = database;
    }

    public Optional<WorldDevelopmentSnapshot> loadWorld() {
        try (Connection connection = database.openConnection()) {
            int cycle;
            String ending;
            long updatedAt;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT cycle, last_ending, updated_at FROM development_state WHERE id = 1");
                 ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                cycle = result.getInt("cycle");
                ending = result.getString("last_ending");
                updatedAt = result.getLong("updated_at");
            }
            return Optional.of(new WorldDevelopmentSnapshot(cycle,
                    readEnumMap(connection, "development_resource", "resource", "amount", WorldResource.class),
                    readEnumMap(connection, "city_project", "project", "level", CityProject.class),
                    readEnumMap(connection, "faction_relation", "faction", "reputation", Faction.class),
                    readEnumMap(connection, "exploration_site", "site", "discovered_cycle", ExplorationSite.class),
                    readEnumMap(connection, "event_chain", "chain", "progress", EventChain.class),
                    ending, updatedAt));
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot load development state", exception);
        }
    }

    public void saveWorld(WorldDevelopmentSnapshot state) {
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement current = connection.prepareStatement(
                        "SELECT updated_at FROM development_state WHERE id = 1");
                     ResultSet result = current.executeQuery()) {
                    if (result.next() && result.getLong(1) > state.updatedAt()) {
                        connection.rollback();
                        return;
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO development_state(id, cycle, last_ending, updated_at) VALUES (1, ?, ?, ?)
                        ON CONFLICT(id) DO UPDATE SET cycle=excluded.cycle, last_ending=excluded.last_ending,
                        updated_at=excluded.updated_at
                        """)) {
                    statement.setInt(1, state.cycle());
                    statement.setString(2, state.lastEnding());
                    statement.setLong(3, state.updatedAt());
                    statement.executeUpdate();
                }
                replaceMap(connection, "development_resource", "resource", "amount", state.resources(),
                        WorldResource::id);
                replaceMap(connection, "city_project", "project", "level", state.projects(), CityProject::id);
                replaceMap(connection, "faction_relation", "faction", "reputation", state.reputation(), Faction::id);
                replaceMap(connection, "exploration_site", "site", "discovered_cycle", state.discoveries(),
                        ExplorationSite::id);
                replaceMap(connection, "event_chain", "chain", "progress", state.chains(), EventChain::id);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot save development state", exception);
        }
    }

    public Map<WeaponType, WeaponMasterySnapshot> loadMastery(UUID playerId) {
        Map<WeaponType, WeaponMasterySnapshot> result = new EnumMap<>(WeaponType.class);
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT weapon, mastery, technique, updated_at FROM player_weapon_mastery WHERE player_uuid = ?
                     """)) {
            statement.setString(1, playerId.toString());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    WeaponType weapon = WeaponType.parse(rows.getString("weapon"));
                    if (weapon != null) result.put(weapon, new WeaponMasterySnapshot(playerId, weapon,
                            rows.getInt("mastery"), TechniquePath.parse(rows.getString("technique")),
                            rows.getLong("updated_at")));
                }
            }
            return result;
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot load weapon mastery", exception);
        }
    }

    public void saveMastery(WeaponMasterySnapshot state) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO player_weapon_mastery(player_uuid, weapon, mastery, technique, updated_at)
                     VALUES (?, ?, ?, ?, ?)
                     ON CONFLICT(player_uuid, weapon) DO UPDATE SET mastery=excluded.mastery,
                     technique=excluded.technique, updated_at=excluded.updated_at
                     """)) {
            statement.setString(1, state.playerId().toString());
            statement.setString(2, state.weapon().id());
            statement.setInt(3, state.mastery());
            statement.setString(4, state.technique().id());
            statement.setLong(5, state.updatedAt());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot save weapon mastery", exception);
        }
    }

    public void recordCycle(int cycle, String ending, String summary, long completedAt) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO cycle_history(cycle, ending, summary, completed_at) VALUES (?, ?, ?, ?)
                     ON CONFLICT(cycle) DO UPDATE SET ending=excluded.ending, summary=excluded.summary,
                     completed_at=excluded.completed_at
                     """)) {
            statement.setInt(1, cycle);
            statement.setString(2, ending);
            statement.setString(3, summary);
            statement.setLong(4, completedAt);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot record cycle ending", exception);
        }
    }

    public Optional<CityRoute> loadRoute(int cycle) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT route FROM city_route WHERE cycle = ?")) {
            statement.setInt(1, cycle);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.ofNullable(CityRoute.parse(rows.getString(1))) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot load city route", exception);
        }
    }

    public void saveRoute(int cycle, CityRoute route, long chosenAt) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO city_route(cycle, route, chosen_at) VALUES (?, ?, ?)
                     ON CONFLICT(cycle) DO NOTHING
                     """)) {
            statement.setInt(1, cycle);
            statement.setString(2, route.id());
            statement.setLong(3, chosenAt);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot save city route", exception);
        }
    }

    public Map<CityProject, ProjectConditionSnapshot> loadConditions() {
        Map<CityProject, ProjectConditionSnapshot> result = new EnumMap<>(CityProject.class);
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT project, condition, updated_at FROM city_project_condition");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                CityProject project = CityProject.parse(rows.getString("project"));
                if (project != null) result.put(project, new ProjectConditionSnapshot(project,
                        rows.getInt("condition"), rows.getLong("updated_at")));
            }
            return result;
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot load project conditions", exception);
        }
    }

    public void saveCondition(ProjectConditionSnapshot state) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO city_project_condition(project, condition, updated_at) VALUES (?, ?, ?)
                     ON CONFLICT(project) DO UPDATE SET condition=excluded.condition,
                     updated_at=excluded.updated_at
                     WHERE excluded.updated_at >= city_project_condition.updated_at
                     """)) {
            statement.setString(1, state.project().id());
            statement.setInt(2, state.condition());
            statement.setLong(3, state.updatedAt());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot save project condition", exception);
        }
    }

    public void saveConditions(Map<CityProject, ProjectConditionSnapshot> states) {
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO city_project_condition(project, condition, updated_at) VALUES (?, ?, ?)
                    ON CONFLICT(project) DO UPDATE SET condition=excluded.condition,
                    updated_at=excluded.updated_at
                    WHERE excluded.updated_at >= city_project_condition.updated_at
                    """)) {
                for (ProjectConditionSnapshot state : states.values()) {
                    statement.setString(1, state.project().id());
                    statement.setInt(2, state.condition());
                    statement.setLong(3, state.updatedAt());
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
            throw new IllegalStateException("Cannot save project conditions", exception);
        }
    }

    private <E extends Enum<E>> Map<E, Integer> readEnumMap(Connection connection, String table, String keyColumn,
                                                            String valueColumn, Class<E> type) throws SQLException {
        Map<E, Integer> result = new EnumMap<>(type);
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + keyColumn + ", " + valueColumn + " FROM " + table);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                E key = parse(type, rows.getString(1));
                if (key != null) result.put(key, rows.getInt(2));
            }
        }
        return result;
    }

    private <E> void replaceMap(Connection connection, String table, String keyColumn, String valueColumn,
                                Map<E, Integer> values, java.util.function.Function<E, String> id) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM " + table)) {
            delete.executeUpdate();
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO " + table + "(" + keyColumn + ", " + valueColumn + ") VALUES (?, ?)")) {
            for (Map.Entry<E, Integer> entry : values.entrySet()) {
                insert.setString(1, id.apply(entry.getKey()));
                insert.setInt(2, entry.getValue());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    @SuppressWarnings("unchecked")
    private <E extends Enum<E>> E parse(Class<E> type, String value) {
        if (type == WorldResource.class) return (E) WorldResource.parse(value);
        if (type == CityProject.class) return (E) CityProject.parse(value);
        if (type == Faction.class) return (E) Faction.parse(value);
        if (type == ExplorationSite.class) return (E) ExplorationSite.parse(value);
        if (type == EventChain.class) return (E) EventChain.parse(value);
        return null;
    }
}
