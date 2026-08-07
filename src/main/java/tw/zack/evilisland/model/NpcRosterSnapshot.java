package tw.zack.evilisland.model;

public record NpcRosterSnapshot(
        NpcRole role,
        int fatigue,
        long injuredUntil,
        long updatedAt
) {
    public NpcRosterSnapshot {
        fatigue = Math.max(0, Math.min(100, fatigue));
        injuredUntil = Math.max(0L, injuredUntil);
    }

    public boolean injured(long now) {
        return injuredUntil > now;
    }

    public boolean available(long now, int fatigueLimit) {
        return !injured(now) && fatigue < Math.max(1, Math.min(100, fatigueLimit));
    }
}
