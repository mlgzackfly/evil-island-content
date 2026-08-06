package tw.zack.evilisland.model;

public record CampaignSnapshot(
        int cycle,
        int week,
        int day,
        int defense,
        int supply,
        int intelligence,
        int morale,
        long epochDay,
        boolean completedToday,
        String completedContract,
        long updatedAt
) {
    public CampaignSnapshot {
        cycle = Math.max(1, cycle);
        week = clamp(week, 1, 4);
        day = clamp(day, 1, 7);
        defense = clamp(defense, 0, 100);
        supply = clamp(supply, 0, 100);
        intelligence = clamp(intelligence, 0, 100);
        morale = clamp(morale, 0, 100);
        completedContract = completedContract == null ? "" : completedContract;
    }

    public static CampaignSnapshot initial(long epochDay, long now) {
        return new CampaignSnapshot(1, 1, 1, 50, 50, 50, 50,
                epochDay, false, "", now);
    }

    public int metric(CampaignMetric metric) {
        return switch (metric) {
            case DEFENSE -> defense;
            case SUPPLY -> supply;
            case INTELLIGENCE -> intelligence;
            case MORALE -> morale;
        };
    }

    public int absoluteDay() {
        return (cycle - 1) * 28 + (week - 1) * 7 + day;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
