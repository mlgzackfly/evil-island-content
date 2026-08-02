package tw.zack.evilisland.model;

public record PatrolScaling(
        int playerCount,
        boolean companion,
        int zaochiCount,
        double zaochiHealthMultiplier,
        double zaochiDamageMultiplier,
        double bossHealthMultiplier,
        double bossDamageMultiplier
) {
    public static PatrolScaling forPlayers(int requestedPlayers, int baseZaochi, int zaochiPerExtraPlayer,
                                           double healthPerExtraPlayer, double damagePerExtraPlayer,
                                           double bossHealthPerExtraPlayer, double bossDamagePerExtraPlayer) {
        int players = Math.max(1, Math.min(2, requestedPlayers));
        int extra = players - 1;
        return new PatrolScaling(
                players,
                players == 1,
                Math.max(1, baseZaochi + zaochiPerExtraPlayer * extra),
                Math.max(1.0, 1.0 + healthPerExtraPlayer * extra),
                Math.max(1.0, 1.0 + damagePerExtraPlayer * extra),
                Math.max(1.0, 1.0 + bossHealthPerExtraPlayer * extra),
                Math.max(1.0, 1.0 + bossDamagePerExtraPlayer * extra)
        );
    }
}
