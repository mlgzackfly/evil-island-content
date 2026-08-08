package tw.zack.evilisland.model;

public final class GrowthRules {
    public static final int MAX_TRANSFORMATIONS = 3;

    private GrowthRules() {
    }

    public static int capacity(int transformations) {
        return 8 + Math.max(0, Math.min(MAX_TRANSFORMATIONS, transformations)) * 6;
    }

    public static int requiredEssence(int nextStage) {
        return switch (nextStage) {
            case 1 -> 3;
            case 2 -> 5;
            case 3 -> 7;
            default -> Integer.MAX_VALUE;
        };
    }

    public static double requiredPurity(int nextStage) {
        return switch (nextStage) {
            case 1 -> 1.0;
            case 2 -> 1.5;
            case 3 -> 2.0;
            default -> Double.MAX_VALUE;
        };
    }

    public static double successChance(int nextStage, double averagePurity, int dao, int rejection) {
        if (nextStage == 1) return 1.0;
        double chance = 0.50 + averagePurity * 0.12 + Math.max(0, dao) / 500.0
                - Math.max(0, rejection) * 0.06 - (nextStage - 2) * 0.08;
        return Math.max(0.20, Math.min(0.95, chance));
    }

    public static int failureLoss(int nextStage) {
        return Math.max(1, requiredEssence(nextStage) / 3);
    }
}
