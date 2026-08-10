package tw.zack.evilisland.model;

import java.util.UUID;

public record ExpeditionStoryDecisionSnapshot(
        UUID expeditionId,
        ExplorationSite site,
        int chapter,
        ExpeditionStoryChoice choice,
        UUID leader,
        UUID partner,
        int cycle,
        int week,
        long decidedAt
) { }
