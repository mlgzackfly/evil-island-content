package tw.zack.evilisland.model;

import java.util.UUID;

public record ExpeditionSnapshot(
        UUID id,
        ExpeditionOperation operation,
        ExpeditionRoute route,
        ExpeditionPhase phase,
        ExpeditionOutcome outcome,
        String world,
        double anchorX,
        double anchorY,
        double anchorZ,
        UUID leader,
        UUID partner,
        UUID companion,
        long seed,
        int approachMask,
        int clueMask,
        int objectiveMask,
        UUID firstActivator,
        long objectiveDeadline,
        int alert,
        int enemiesRemaining,
        long startedAt,
        long phaseStartedAt,
        long completedAt,
        long updatedAt) {

    public int participants() { return partner == null ? 1 : 2; }
    public boolean member(UUID playerId) { return leader.equals(playerId) || playerId.equals(partner); }
    public int cluesFound() { return Integer.bitCount(clueMask); }
    public boolean active() { return phase.running(); }
}
