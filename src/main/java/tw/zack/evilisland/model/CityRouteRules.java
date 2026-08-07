package tw.zack.evilisland.model;

import java.util.EnumMap;
import java.util.Map;

public final class CityRouteRules {
    private CityRouteRules() {
    }

    public static Map<WorldResource, Integer> projectCost(CityProject project, int nextLevel, CityRoute route) {
        EnumMap<WorldResource, Integer> cost = new EnumMap<>(WorldResource.class);
        cost.putAll(project.costForLevel(nextLevel));
        if (route != null && route.prefers(project)) {
            cost.replaceAll((resource, amount) -> Math.max(1, (int) Math.ceil(amount * 0.80)));
        }
        return Map.copyOf(cost);
    }

    public static int deploymentScoutRequirement(CityRoute route) {
        return route == CityRoute.EXPEDITION ? 1 : 2;
    }

    public static int cityQiBonus(CityRoute route) {
        return route == CityRoute.QI_CIVIC ? 1 : 0;
    }

    public static int defenseModifier(CityRoute route) {
        return route == CityRoute.FORTRESS ? -1 : 0;
    }

    public static boolean canChoose(int day, boolean alreadyChosen) {
        return day <= 3 && !alreadyChosen;
    }
}
