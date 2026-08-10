package tw.zack.evilisland.model;

import java.util.UUID;

public record ExpeditionConsequenceSnapshot(ExplorationSite site, UUID expeditionId,
        ExpeditionOperation operation, ExpeditionOutcome outcome, String world, double x, double y, double z,
        long updatedAt) { }
