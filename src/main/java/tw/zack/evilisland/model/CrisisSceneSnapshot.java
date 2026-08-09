package tw.zack.evilisland.model;

import java.util.UUID;

public record CrisisSceneSnapshot(
        UUID eventId,
        LivingEventType type,
        CrisisSceneState state,
        String world,
        int x,
        int y,
        int z,
        long updatedAt
) {
    public CrisisSceneSnapshot {
        if (eventId == null || type == null || state == null || world == null || world.isBlank()) {
            throw new IllegalArgumentException("Crisis scene fields cannot be null");
        }
    }

    public CrisisSceneSnapshot withState(CrisisSceneState next, long now) {
        return new CrisisSceneSnapshot(eventId, type, next, world, x, y, z,
                Math.max(now, updatedAt + 1));
    }
}
