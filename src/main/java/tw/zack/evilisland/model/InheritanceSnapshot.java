package tw.zack.evilisland.model;

import java.util.UUID;

public record InheritanceSnapshot(
        UUID playerId,
        InheritanceType inheritance,
        int progress,
        boolean completed,
        boolean attuned,
        long updatedAt
) {
    public InheritanceSnapshot {
        progress = Math.max(0, Math.min(2, progress));
    }
}
