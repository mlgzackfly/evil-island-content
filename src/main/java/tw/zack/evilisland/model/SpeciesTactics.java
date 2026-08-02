package tw.zack.evilisland.model;

import java.util.UUID;

public final class SpeciesTactics {
    private SpeciesTactics() {
    }

    public static int formationLane(UUID id) {
        return Math.floorMod(id.hashCode(), 3) - 1;
    }

    public static double healthRatio(double health, double maxHealth) {
        if (maxHealth <= 0.0) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, health / maxHealth));
    }

    public static boolean isEnraged(double health, double maxHealth, double threshold) {
        return healthRatio(health, maxHealth) <= threshold;
    }

    public static long scaledCooldown(long baseMillis, boolean enraged, double enragedMultiplier) {
        if (baseMillis < 0 || enragedMultiplier <= 0.0 || enragedMultiplier > 1.0) {
            throw new IllegalArgumentException("Invalid tactical cooldown settings");
        }
        return enraged ? Math.max(1L, Math.round(baseMillis * enragedMultiplier)) : baseMillis;
    }
}
