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
        boolean weeklyResolved,
        CampaignStrategy weeklyStrategy,
        int fortifyPoints,
        int provisionPoints,
        int reconPoints,
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
        weeklyStrategy = weeklyStrategy == null ? CampaignStrategy.NONE : weeklyStrategy;
        if (!weeklyResolved) weeklyStrategy = CampaignStrategy.NONE;
        fortifyPoints = Math.max(0, fortifyPoints);
        provisionPoints = Math.max(0, provisionPoints);
        reconPoints = Math.max(0, reconPoints);
    }

    public static CampaignSnapshot initial(long epochDay, long now) {
        return new CampaignSnapshot(1, 1, 1, 50, 50, 50, 50,
                epochDay, false, "", false, CampaignStrategy.NONE, 0, 0, 0, now);
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

    public CampaignStrategy dominantStrategy() {
        int maximum = Math.max(fortifyPoints, Math.max(provisionPoints, reconPoints));
        if (maximum == 0) return weeklyStrategy == CampaignStrategy.NONE
                ? CampaignStrategy.FORTIFY : weeklyStrategy;
        if (weeklyStrategy == CampaignStrategy.FORTIFY && fortifyPoints == maximum) return weeklyStrategy;
        if (weeklyStrategy == CampaignStrategy.PROVISION && provisionPoints == maximum) return weeklyStrategy;
        if (weeklyStrategy == CampaignStrategy.RECON && reconPoints == maximum) return weeklyStrategy;
        if (reconPoints == maximum) return CampaignStrategy.RECON;
        if (provisionPoints == maximum) return CampaignStrategy.PROVISION;
        return CampaignStrategy.FORTIFY;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
