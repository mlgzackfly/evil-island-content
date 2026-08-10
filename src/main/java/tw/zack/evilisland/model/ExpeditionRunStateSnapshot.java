package tw.zack.evilisland.model;

import java.util.UUID;

public record ExpeditionRunStateSnapshot(UUID expeditionId, ExplorationSite site, int kitMask, int eventMask,
                                         int eventScore, long updatedAt) {
    public static ExpeditionRunStateSnapshot initial(UUID id, ExplorationSite site, int kitMask, long now) {
        return new ExpeditionRunStateSnapshot(id, site, kitMask, 0, 0, now);
    }
}
