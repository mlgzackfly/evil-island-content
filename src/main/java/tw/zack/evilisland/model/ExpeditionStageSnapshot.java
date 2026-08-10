package tw.zack.evilisland.model;

import java.util.UUID;

public record ExpeditionStageSnapshot(UUID expeditionId, ExpeditionPhase phase, long startedAt, long completedAt) { }
