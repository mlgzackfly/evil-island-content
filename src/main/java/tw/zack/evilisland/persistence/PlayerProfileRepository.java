package tw.zack.evilisland.persistence;

import tw.zack.evilisland.model.Formula;
import tw.zack.evilisland.model.FormulaPath;
import tw.zack.evilisland.model.ObjectiveStage;
import tw.zack.evilisland.model.PlayerProfileSnapshot;
import tw.zack.evilisland.model.QiTendency;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public final class PlayerProfileRepository {
    private static final String UPSERT = """
            INSERT INTO player_profile(
                uuid, name, tendency, primary_formula, secondary_formula, formula_primary_percent,
                qi, essence, transformations, objective, zaochi_kills, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET
                name = excluded.name,
                tendency = excluded.tendency,
                primary_formula = excluded.primary_formula,
                secondary_formula = excluded.secondary_formula,
                formula_primary_percent = excluded.formula_primary_percent,
                qi = excluded.qi,
                essence = excluded.essence,
                transformations = excluded.transformations,
                objective = excluded.objective,
                zaochi_kills = excluded.zaochi_kills,
                updated_at = excluded.updated_at
            """;

    private final DatabaseManager database;

    public PlayerProfileRepository(DatabaseManager database) {
        this.database = database;
    }

    public Optional<PlayerProfileSnapshot> find(UUID uuid) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM player_profile WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(read(result)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot load player profile " + uuid, exception);
        }
    }

    public void save(PlayerProfileSnapshot profile) {
        saveAll(java.util.List.of(profile));
    }

    public void saveAll(Collection<PlayerProfileSnapshot> profiles) {
        if (profiles.isEmpty()) {
            return;
        }
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(UPSERT)) {
            connection.setAutoCommit(false);
            try {
                for (PlayerProfileSnapshot profile : profiles) {
                    bind(statement, profile);
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
            throw new IllegalStateException("Cannot save player profiles", exception);
        }
    }

    public void delete(UUID uuid) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM player_profile WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot delete player profile " + uuid, exception);
        }
    }

    private void bind(PreparedStatement statement, PlayerProfileSnapshot profile) throws SQLException {
        FormulaPath path = profile.formulaPath();
        statement.setString(1, profile.uuid().toString());
        statement.setString(2, profile.name());
        nullableString(statement, 3, profile.tendency() == null ? null : profile.tendency().id());
        nullableString(statement, 4, path == null ? null : path.primary().id());
        nullableString(statement, 5, path == null || path.secondary() == null ? null : path.secondary().id());
        if (path == null || path.secondary() == null) {
            statement.setNull(6, java.sql.Types.INTEGER);
        } else {
            statement.setInt(6, path.primaryPercent());
        }
        statement.setInt(7, Math.max(0, profile.qi()));
        statement.setInt(8, Math.max(0, profile.essence()));
        statement.setInt(9, Math.max(0, profile.transformations()));
        statement.setInt(10, profile.objective().id());
        statement.setInt(11, Math.max(0, profile.zaochiKills()));
        statement.setLong(12, profile.updatedAt());
    }

    private PlayerProfileSnapshot read(ResultSet result) throws SQLException {
        UUID uuid = UUID.fromString(result.getString("uuid"));
        QiTendency tendency = QiTendency.parse(result.getString("tendency"));
        Formula primary = Formula.parse(result.getString("primary_formula"));
        Formula secondary = Formula.parse(result.getString("secondary_formula"));
        FormulaPath path = null;
        if (primary != null) {
            if (secondary == null) {
                path = FormulaPath.pure(primary);
            } else {
                int ratio = result.getInt("formula_primary_percent");
                try {
                    path = FormulaPath.mixed(primary, secondary, result.wasNull() ? 50 : ratio);
                } catch (IllegalArgumentException ignored) {
                    path = FormulaPath.pure(primary);
                }
            }
        }
        return new PlayerProfileSnapshot(
                uuid,
                result.getString("name"),
                tendency,
                path,
                Math.max(0, result.getInt("qi")),
                Math.max(0, result.getInt("essence")),
                Math.max(0, result.getInt("transformations")),
                ObjectiveStage.fromId(result.getInt("objective")),
                Math.max(0, result.getInt("zaochi_kills")),
                result.getLong("updated_at")
        );
    }

    private void nullableString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }
}
