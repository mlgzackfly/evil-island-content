package tw.zack.evilisland.model;

import java.util.UUID;

public record WorldEventSnapshot(
        UUID id,
        String type,
        WorldEventState state,
        UUID world,
        double anchorX,
        double anchorY,
        double anchorZ,
        String payload,
        long updatedAt
) {
    public WorldEventSnapshot withState(WorldEventState next, long now) {
        if (!state.canTransitionTo(next)) {
            throw new IllegalStateException("Invalid world event transition: " + state + " -> " + next);
        }
        return new WorldEventSnapshot(id, type, next, world, anchorX, anchorY, anchorZ, payload, now);
    }
}
