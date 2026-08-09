package tw.zack.evilisland.model;

import java.util.UUID;

public record SupplyRouteSnapshot(
        UUID eventId,
        SupplyRouteState state,
        UUID dispatcher,
        UUID receiver,
        long departedAt,
        long arrivesAt,
        long updatedAt
) {
    public SupplyRouteSnapshot {
        if (eventId == null || state == null || dispatcher == null || departedAt < 0 || arrivesAt < departedAt) {
            throw new IllegalArgumentException("Invalid supply route snapshot");
        }
    }

    public SupplyRouteSnapshot arrive(long now) {
        return new SupplyRouteSnapshot(eventId, SupplyRouteState.ARRIVED, dispatcher, receiver,
                departedAt, arrivesAt, Math.max(now, updatedAt + 1));
    }

    public SupplyRouteSnapshot receive(UUID player, long now) {
        return new SupplyRouteSnapshot(eventId, SupplyRouteState.COMPLETED, dispatcher, player,
                departedAt, arrivesAt, Math.max(now, updatedAt + 1));
    }

    public SupplyRouteSnapshot close(SupplyRouteState next, long now) {
        return new SupplyRouteSnapshot(eventId, next, dispatcher, receiver, departedAt, arrivesAt,
                Math.max(now, updatedAt + 1));
    }
}
