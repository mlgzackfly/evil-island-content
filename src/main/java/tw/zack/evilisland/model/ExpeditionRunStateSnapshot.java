package tw.zack.evilisland.model;

import java.util.UUID;

public record ExpeditionRunStateSnapshot(UUID expeditionId, ExplorationSite site, int kitMask, int eventMask,
                                         int eventScore, int storyChapter,
                                         ExpeditionStoryChoice previousStoryChoice, long updatedAt) {
    public static ExpeditionRunStateSnapshot initial(UUID id, ExplorationSite site, int kitMask, int storyChapter,
                                                     long now) {
        return new ExpeditionRunStateSnapshot(id, site, kitMask, 0, 0, storyChapter, null, now);
    }
}
