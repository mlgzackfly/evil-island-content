package tw.zack.evilisland.model;

public record ExpeditionStoryResolution(
        boolean recorded,
        boolean advanced,
        ExpeditionStoryProgressSnapshot progress
) { }
