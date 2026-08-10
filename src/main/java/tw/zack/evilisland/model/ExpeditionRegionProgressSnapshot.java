package tw.zack.evilisland.model;

public record ExpeditionRegionProgressSnapshot(
        ExplorationSite site,
        int completed,
        int partial,
        int withdrawn,
        int abandoned,
        ExpeditionOperation lastOperation,
        ExpeditionOutcome lastOutcome,
        long updatedAt
) { }
