package tw.zack.evilisland.model;

public record ExpeditionStoryProgressSnapshot(
        ExplorationSite site,
        int chapter,
        boolean completed,
        int secureChoices,
        int connectChoices,
        ExpeditionStoryChoice lastChoice,
        int lastCycle,
        int lastWeek,
        long updatedAt
) {
    public static ExpeditionStoryProgressSnapshot initial(ExplorationSite site) {
        return new ExpeditionStoryProgressSnapshot(site, 1, false, 0, 0, null, 0, 0, 0L);
    }

    public ExpeditionStoryChoice direction() {
        if (secureChoices == connectChoices) return lastChoice;
        return secureChoices > connectChoices ? ExpeditionStoryChoice.SECURE : ExpeditionStoryChoice.CONNECT;
    }
}
