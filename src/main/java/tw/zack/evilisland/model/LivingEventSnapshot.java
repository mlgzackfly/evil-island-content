package tw.zack.evilisland.model;

import java.util.UUID;

public record LivingEventSnapshot(
        UUID id,
        LivingEventType type,
        LivingEventState state,
        LivingEventApproach approach,
        int cycle,
        int week,
        int day,
        long startedEpochDay,
        long expiresEpochDay,
        int participants,
        long createdAt,
        long resolvedAt,
        long updatedAt
) {
    public LivingEventSnapshot {
        if (id == null || type == null || state == null || approach == null) {
            throw new IllegalArgumentException("Living event fields cannot be null");
        }
        cycle = Math.max(1, cycle);
        week = Math.max(1, Math.min(4, week));
        day = Math.max(1, Math.min(7, day));
        expiresEpochDay = Math.max(startedEpochDay + 1, expiresEpochDay);
        participants = Math.max(0, Math.min(2, participants));
    }

    public LivingEventSnapshot resolve(LivingEventApproach selected, int memberCount, long now) {
        if (state != LivingEventState.ACTIVE || selected == null || selected == LivingEventApproach.NONE) {
            return this;
        }
        return new LivingEventSnapshot(id, type, LivingEventState.RESOLVED, selected, cycle, week, day,
                startedEpochDay, expiresEpochDay, memberCount, createdAt, now, Math.max(now, updatedAt + 1));
    }

    public LivingEventSnapshot expire(long now) {
        if (state != LivingEventState.ACTIVE) return this;
        return new LivingEventSnapshot(id, type, LivingEventState.EXPIRED, LivingEventApproach.NONE,
                cycle, week, day, startedEpochDay, expiresEpochDay, 0, createdAt, now,
                Math.max(now, updatedAt + 1));
    }
}
