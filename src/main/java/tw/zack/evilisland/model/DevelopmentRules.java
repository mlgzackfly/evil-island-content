package tw.zack.evilisland.model;

import java.util.EnumMap;
import java.util.Map;

public final class DevelopmentRules {
    private DevelopmentRules() {
    }

    public static Map<WorldResource, Integer> missionYield(MissionType type, int risk, boolean fullReward) {
        EnumMap<WorldResource, Integer> result = new EnumMap<>(WorldResource.class);
        if (!fullReward) return result;
        int amount = 1 + Math.max(1, risk) / 3;
        WorldResource primary = switch (type) {
            case PATROL -> WorldResource.COMPONENTS;
            case GATHER -> WorldResource.TIMBER;
            case SCOUT -> WorldResource.SPECIAL;
            case ESCORT -> WorldResource.PROVISIONS;
            case RESCUE -> WorldResource.PROVISIONS;
            case DEFENSE -> WorldResource.MASONRY;
        };
        result.put(primary, amount);
        if (risk >= 4) result.put(WorldResource.MASONRY, result.getOrDefault(WorldResource.MASONRY, 0) + 1);
        return Map.copyOf(result);
    }

    public static int masteryGain(int risk, boolean fullReward) {
        return fullReward ? 2 + Math.max(1, risk) : 1;
    }

    public static int techniqueRequirement(int tier) {
        return switch (Math.max(1, tier)) {
            case 1 -> 12;
            case 2 -> 30;
            default -> 60;
        };
    }

    public static int carryOverResource(int amount) {
        return Math.max(0, Math.min(12, amount / 2));
    }

    public static int defenseEnemyPerEntranceModifier(int week, boolean displacedComplete,
                                                       boolean musterComplete) {
        if (week < 3) return 0;
        int modifier = displacedComplete ? 0 : 1;
        if (musterComplete) modifier--;
        return Math.max(-1, Math.min(1, modifier));
    }

    public static int bossEscortModifier(int week, int musterProgress, int musterStages) {
        if (week < 4) return 0;
        if (musterProgress >= musterStages) return -1;
        return musterProgress == 0 ? 1 : 0;
    }

    public static String ending(Map<CityProject, Integer> projects, Map<Faction, Integer> reputation,
                                int completedChains, int discoveredSites) {
        int projectScore = projects.values().stream().mapToInt(Integer::intValue).sum();
        int diplomacyScore = reputation.values().stream().mapToInt(value -> Math.max(0, value)).sum();
        if (completedChains >= 3 && discoveredSites >= 4) return "遠路重開";
        if (diplomacyScore >= 120) return "諸族互市";
        if (projectScore >= 8) return "新城固守";
        return "艱難續存";
    }
}
