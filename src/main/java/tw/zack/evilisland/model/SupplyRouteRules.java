package tw.zack.evilisland.model;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public final class SupplyRouteRules {
    private SupplyRouteRules() { }

    public static Map<WorldResource, Integer> discountedCost(Map<WorldResource, Integer> base, double multiplier) {
        double safeMultiplier = Math.max(0.25, Math.min(1.0, multiplier));
        EnumMap<WorldResource, Integer> result = new EnumMap<>(WorldResource.class);
        base.forEach((resource, amount) -> result.put(resource,
                Math.max(1, (int) Math.ceil(Math.max(0, amount) * safeMultiplier))));
        return Map.copyOf(result);
    }

    public static long arrivalTime(long now, long minutes) {
        long safeMinutes = Math.max(1L, Math.min(24L * 60L, minutes));
        return now + safeMinutes * 60_000L;
    }

    public static boolean canReceive(SupplyRouteSnapshot route, UUID eventId, long now) {
        return route != null && eventId != null && route.eventId().equals(eventId)
                && (route.state() == SupplyRouteState.ARRIVED
                || route.state() == SupplyRouteState.TRANSIT && now >= route.arrivesAt());
    }
}
