package tw.zack.evilisland.model;

public final class RegionControlRules {
    public static final int INITIAL_STABILITY = 60;
    public static final int BASE_SUPPLIES = 6;
    public static final int MAX_SUPPLIES = 8;

    private RegionControlRules() { }

    public static int clampStability(int stability) {
        return Math.max(0, Math.min(100, stability));
    }

    public static int clampCampLevel(int level) {
        return Math.max(1, Math.min(2, level));
    }

    public static int clampSupplies(int supplies) {
        return Math.max(0, Math.min(MAX_SUPPLIES, supplies));
    }

    public static int campCapacity(int level) {
        return clampCampLevel(level) >= 2 ? MAX_SUPPLIES : BASE_SUPPLIES;
    }

    public static RegionState stateAfter(RegionState previous, int stability) {
        int value = clampStability(stability);
        if (value <= 20) return RegionState.LOST;
        if (value >= 70) return RegionState.STABLE;
        if (previous == RegionState.LOST || previous == RegionState.RECOVERING) {
            return RegionState.RECOVERING;
        }
        return RegionState.TENSE;
    }

    public static int eventDelta(LivingEventState state, LivingEventApproach approach) {
        if (state == LivingEventState.ACTIVE) return -5;
        if (state == LivingEventState.EXPIRED) return -24;
        if (state != LivingEventState.RESOLVED) return 0;
        return approach == LivingEventApproach.FIELD ? 14 : 9;
    }

    public static int missionDelta(int participants) {
        return participants >= 2 ? 3 : 2;
    }

    public static boolean campOperational(RegionControlSnapshot region) {
        return region != null && region.state() != RegionState.LOST && region.supplies() > 0;
    }
}
