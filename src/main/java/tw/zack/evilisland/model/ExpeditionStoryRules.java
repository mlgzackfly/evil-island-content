package tw.zack.evilisland.model;

public final class ExpeditionStoryRules {
    private ExpeditionStoryRules() { }

    public static boolean canAdvance(ExpeditionStoryProgressSnapshot current, int storyChapter,
                                     int cycle, int week) {
        return !current.completed() && current.chapter() == storyChapter
                && (current.lastCycle() != cycle || current.lastWeek() != week);
    }

    public static ExpeditionStoryProgressSnapshot advance(ExpeditionStoryProgressSnapshot current,
                                                           ExpeditionStoryChoice choice, int cycle, int week,
                                                           long now) {
        int nextChapter = Math.min(3, current.chapter() + 1);
        boolean completed = current.chapter() == 3;
        return new ExpeditionStoryProgressSnapshot(current.site(), nextChapter, completed,
                current.secureChoices() + (choice == ExpeditionStoryChoice.SECURE ? 1 : 0),
                current.connectChoices() + (choice == ExpeditionStoryChoice.CONNECT ? 1 : 0),
                choice, cycle, week, now);
    }

    public static boolean allCompleted(java.util.List<ExpeditionStoryProgressSnapshot> progress) {
        return progress.stream().filter(ExpeditionStoryProgressSnapshot::completed)
                .map(ExpeditionStoryProgressSnapshot::site).distinct().count() == ExplorationSite.values().length;
    }
}
