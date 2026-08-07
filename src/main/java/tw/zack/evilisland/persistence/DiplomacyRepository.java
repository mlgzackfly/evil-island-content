package tw.zack.evilisland.persistence;

import tw.zack.evilisland.model.ContractResolution;
import tw.zack.evilisland.model.Faction;
import tw.zack.evilisland.model.FactionContract;
import tw.zack.evilisland.model.FactionContractSnapshot;
import tw.zack.evilisland.model.FactionContractState;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public final class DiplomacyRepository {
    private final DatabaseManager database;

    public DiplomacyRepository(DatabaseManager database) {
        this.database = database;
    }

    public Optional<FactionContractSnapshot> loadContract(int cycle, Faction faction) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT progress, resolution, state, updated_at
                     FROM faction_contract WHERE cycle = ? AND faction = ?
                     """)) {
            statement.setInt(1, cycle);
            statement.setString(2, faction.id());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) return Optional.empty();
                FactionContract contract = FactionContract.forFaction(faction);
                return Optional.of(new FactionContractSnapshot(cycle, contract, row.getInt("progress"),
                        ContractResolution.parse(row.getString("resolution")),
                        FactionContractState.parse(row.getString("state")), row.getLong("updated_at")));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot load faction contract", exception);
        }
    }

    public void saveContract(FactionContractSnapshot snapshot) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO faction_contract(cycle, faction, progress, resolution, state, updated_at)
                     VALUES (?, ?, ?, ?, ?, ?)
                     ON CONFLICT(cycle, faction) DO UPDATE SET progress=excluded.progress,
                     resolution=excluded.resolution, state=excluded.state, updated_at=excluded.updated_at
                     """)) {
            statement.setInt(1, snapshot.cycle());
            statement.setString(2, snapshot.contract().faction().id());
            statement.setInt(3, snapshot.progress());
            statement.setString(4, snapshot.resolution().id());
            statement.setString(5, snapshot.state().id());
            statement.setLong(6, snapshot.updatedAt());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot save faction contract", exception);
        }
    }

    public int addCredit(UUID playerId, Faction faction, int weekKey, int maximum, long now) {
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try {
                int amount = credit(connection, playerId, faction, weekKey);
                int updated = Math.min(maximum, amount + 1);
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO player_faction_credit(player_uuid, faction, week, amount, updated_at)
                        VALUES (?, ?, ?, ?, ?)
                        ON CONFLICT(player_uuid, faction, week) DO UPDATE SET
                        amount=excluded.amount, updated_at=excluded.updated_at
                        """)) {
                    statement.setString(1, playerId.toString());
                    statement.setString(2, faction.id());
                    statement.setInt(3, weekKey);
                    statement.setInt(4, updated);
                    statement.setLong(5, now);
                    statement.executeUpdate();
                }
                connection.commit();
                return updated;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot add faction credit", exception);
        }
    }

    public int credit(UUID playerId, Faction faction, int weekKey) {
        try (Connection connection = database.openConnection()) {
            return credit(connection, playerId, faction, weekKey);
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot load faction credit", exception);
        }
    }

    public int stock(Faction faction, int weekKey, int initial) {
        try (Connection connection = database.openConnection()) {
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO faction_stock(faction, week, remaining) VALUES (?, ?, ?)
                    ON CONFLICT(faction, week) DO NOTHING
                    """)) {
                insert.setString(1, faction.id());
                insert.setInt(2, weekKey);
                insert.setInt(3, initial);
                insert.executeUpdate();
            }
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT remaining FROM faction_stock WHERE faction = ? AND week = ?")) {
                query.setString(1, faction.id());
                query.setInt(2, weekKey);
                try (ResultSet row = query.executeQuery()) {
                    return row.next() ? row.getInt(1) : 0;
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot load faction stock", exception);
        }
    }

    public boolean purchase(UUID playerId, Faction faction, int weekKey, int cost) {
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try {
                int credit = credit(connection, playerId, faction, weekKey);
                int stock;
                try (PreparedStatement query = connection.prepareStatement(
                        "SELECT remaining FROM faction_stock WHERE faction = ? AND week = ?")) {
                    query.setString(1, faction.id());
                    query.setInt(2, weekKey);
                    try (ResultSet row = query.executeQuery()) {
                        stock = row.next() ? row.getInt(1) : 0;
                    }
                }
                if (credit < cost || stock < 1) {
                    connection.rollback();
                    return false;
                }
                try (PreparedStatement debit = connection.prepareStatement("""
                        UPDATE player_faction_credit SET amount = amount - ?
                        WHERE player_uuid = ? AND faction = ? AND week = ?
                        """);
                     PreparedStatement takeStock = connection.prepareStatement("""
                        UPDATE faction_stock SET remaining = remaining - 1
                        WHERE faction = ? AND week = ? AND remaining > 0
                        """)) {
                    debit.setInt(1, cost);
                    debit.setString(2, playerId.toString());
                    debit.setString(3, faction.id());
                    debit.setInt(4, weekKey);
                    debit.executeUpdate();
                    takeStock.setString(1, faction.id());
                    takeStock.setInt(2, weekKey);
                    if (takeStock.executeUpdate() != 1) {
                        connection.rollback();
                        return false;
                    }
                }
                connection.commit();
                return true;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot purchase faction stock", exception);
        }
    }

    private int credit(Connection connection, UUID playerId, Faction faction, int weekKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT amount FROM player_faction_credit
                WHERE player_uuid = ? AND faction = ? AND week = ?
                """)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, faction.id());
            statement.setInt(3, weekKey);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? row.getInt(1) : 0;
            }
        }
    }
}
