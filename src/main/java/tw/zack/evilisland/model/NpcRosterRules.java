package tw.zack.evilisland.model;

public final class NpcRosterRules {
    private NpcRosterRules() {
    }

    public static NpcRosterSnapshot normalize(NpcRosterSnapshot state, long now, long recoveryIntervalMs) {
        long interval = Math.max(1L, recoveryIntervalMs);
        long elapsed = Math.max(0L, now - state.updatedAt());
        int recovered = (int) Math.min(100L, elapsed / interval);
        long injury = state.injuredUntil() <= now ? 0L : state.injuredUntil();
        if (recovered == 0 && injury == state.injuredUntil()) return state;
        return new NpcRosterSnapshot(state.role(), state.fatigue() - recovered, injury, now);
    }

    public static NpcRosterSnapshot completeMission(NpcRosterSnapshot state, int fatigueGain, long now) {
        return new NpcRosterSnapshot(state.role(), state.fatigue() + Math.max(0, fatigueGain),
                state.injuredUntil(), now);
    }

    public static NpcRosterSnapshot injure(NpcRosterSnapshot state, long injuryDurationMs, long now) {
        long until = now + Math.max(1000L, injuryDurationMs);
        return new NpcRosterSnapshot(state.role(), Math.max(state.fatigue(), 80), until, now);
    }

    public static NpcRosterSnapshot treat(NpcRosterSnapshot state, int fatigueRelief, long now) {
        return new NpcRosterSnapshot(state.role(), state.fatigue() - Math.max(0, fatigueRelief), 0L, now);
    }
}
