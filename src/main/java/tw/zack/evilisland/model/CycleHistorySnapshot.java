package tw.zack.evilisland.model;

public record CycleHistorySnapshot(
        int cycle,
        String ending,
        String summary,
        long completedAt,
        BossVariant bossVariant,
        long bossEngagedAt
) {
    public CycleHistorySnapshot {
        if (cycle < 1 || ending == null || summary == null || completedAt < 0 || bossEngagedAt < 0) {
            throw new IllegalArgumentException("Invalid cycle history");
        }
    }
}
