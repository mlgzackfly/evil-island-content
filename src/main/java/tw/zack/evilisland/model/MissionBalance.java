package tw.zack.evilisland.model;

public final class MissionBalance {
    private MissionBalance() {
    }

    public static int sharedObjectiveAmount(int baseAmount, int players) {
        return sharedObjectiveAmount(baseAmount, players, 1.5);
    }

    public static int sharedObjectiveAmount(int baseAmount, int players, double duoMultiplier) {
        int partySize = Math.max(1, Math.min(2, players));
        double multiplier = partySize == 1 ? 1.0 : Math.max(1.0, Math.min(2.0, duoMultiplier));
        return Math.max(1, (int) Math.ceil(Math.max(1, baseAmount) * multiplier));
    }

    public static double regularHealth(double multiplier) {
        return clamp(multiplier, 0.85, 1.60);
    }

    public static double regularDamage(double multiplier) {
        return clamp(multiplier, 0.90, 1.45);
    }

    public static double bossHealth(double multiplier) {
        return clamp(multiplier, 0.90, 1.80);
    }

    public static double bossDamage(double multiplier) {
        return clamp(multiplier, 0.90, 1.50);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
