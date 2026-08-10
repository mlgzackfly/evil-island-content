package tw.zack.evilisland.model;

public final class ExpeditionRules {
    private ExpeditionRules() { }

    public static ExpeditionOperation operation(long seed) {
        return ExpeditionOperation.values()[Math.floorMod(seed, ExpeditionOperation.values().length)];
    }

    public static int requiredClues(ExpeditionRoute route) {
        return route == ExpeditionRoute.RIVERBED ? 3 : 2;
    }

    public static int requiredClues(ExpeditionOperation operation, ExpeditionRoute route) {
        return operation == ExpeditionOperation.LOST_CONVOY || route == ExpeditionRoute.RIVERBED ? 3 : 2;
    }

    public static long syncWindowMillis(ExpeditionOperation operation, ExpeditionRoute route) {
        long base = operation == ExpeditionOperation.SUPPLY_NODE_SABOTAGE ? 14_000L : 20_000L;
        return route == ExpeditionRoute.RIDGE ? base + 6_000L : base;
    }

    public static int enemyCount(ExpeditionOperation operation, ExpeditionRoute route, int participants,
                                 int alert) {
        int count = 3 + Math.max(0, participants - 1) * 2;
        if (route == ExpeditionRoute.RIDGE) count++;
        if (route == ExpeditionRoute.RIVERBED) count--;
        if (operation == ExpeditionOperation.BLOCKADE_INFILTRATION) count++;
        if (operation == ExpeditionOperation.SUPPLY_NODE_SABOTAGE) count--;
        if (operation == ExpeditionOperation.CASUALTY_EVACUATION) count--;
        return Math.max(2, count + Math.max(0, alert));
    }

    public static int misleadingClue(long seed) {
        return Math.floorMod(seed >>> 3, 3);
    }

    public static ExpeditionOutcome withdrawalOutcome(ExpeditionPhase phase, int clues, int objectiveMask) {
        if (phase == ExpeditionPhase.OBJECTIVE || phase == ExpeditionPhase.ESCALATION
                || phase == ExpeditionPhase.EXTRACTION || clues >= 2 || objectiveMask != 0) {
            return ExpeditionOutcome.PARTIAL;
        }
        return ExpeditionOutcome.WITHDRAWN;
    }

    public static int regionDelta(ExpeditionOutcome outcome, int participants) {
        return switch (outcome) {
            case COMPLETE -> participants > 1 ? 9 : 8;
            case PARTIAL -> 4;
            case WITHDRAWN -> 0;
            case ABANDONED -> -2;
        };
    }
}
