package tw.zack.evilisland.model;

import java.util.EnumMap;
import java.util.Map;

public final class ProjectConditionRules {
    public static final int MAX_CONDITION = 100;
    public static final int REPAIR_AMOUNT = 25;

    private ProjectConditionRules() {
    }

    public static int functionalLevel(int builtLevel, int condition) {
        return functionalLevel(builtLevel, condition, 30, 60);
    }

    public static int functionalLevel(int builtLevel, int condition, int offlineBelow, int fullAt) {
        int built = Math.max(0, Math.min(3, builtLevel));
        int offline = Math.max(1, Math.min(99, offlineBelow));
        int full = Math.max(offline + 1, Math.min(100, fullAt));
        if (built == 0 || condition < offline) return 0;
        if (condition < full) return Math.max(0, built - 1);
        return built;
    }

    public static int repairedCondition(int condition) {
        return repairedCondition(condition, REPAIR_AMOUNT);
    }

    public static int repairedCondition(int condition, int amount) {
        return Math.min(MAX_CONDITION, Math.max(0, condition) + Math.max(1, amount));
    }

    public static Map<WorldResource, Integer> repairCost(CityProject project) {
        EnumMap<WorldResource, Integer> cost = new EnumMap<>(WorldResource.class);
        switch (project) {
            case WALLS -> {
                cost.put(WorldResource.MASONRY, 3);
                cost.put(WorldResource.TIMBER, 1);
            }
            case QI_MIRROR -> {
                cost.put(WorldResource.COMPONENTS, 3);
                cost.put(WorldResource.SPECIAL, 1);
            }
            case WORKSHOP -> {
                cost.put(WorldResource.TIMBER, 2);
                cost.put(WorldResource.COMPONENTS, 2);
            }
            case SCOUT_POST -> {
                cost.put(WorldResource.TIMBER, 2);
                cost.put(WorldResource.PROVISIONS, 2);
            }
            case AIR_DEFENSE -> {
                cost.put(WorldResource.MASONRY, 2);
                cost.put(WorldResource.COMPONENTS, 3);
            }
        }
        return Map.copyOf(cost);
    }

    public static Map<CityProject, Integer> defenseFailureDamage(int breaches, int week) {
        return defenseFailureDamage(breaches, week, 18, 4, 12, 15);
    }

    public static Map<CityProject, Integer> defenseFailureDamage(int breaches, int week,
                                                                  int wallBase, int wallPerBreach,
                                                                  int earlySecondary, int lateSecondary) {
        EnumMap<CityProject, Integer> damage = new EnumMap<>(CityProject.class);
        damage.put(CityProject.WALLS, Math.max(0, wallBase) + Math.max(0, breaches) * Math.max(0, wallPerBreach));
        CityProject secondary = switch (Math.max(1, Math.min(4, week))) {
            case 1 -> CityProject.SCOUT_POST;
            case 2 -> CityProject.QI_MIRROR;
            case 3 -> CityProject.AIR_DEFENSE;
            default -> CityProject.WORKSHOP;
        };
        damage.put(secondary, Math.max(0, week >= 3 ? lateSecondary : earlySecondary));
        return Map.copyOf(damage);
    }

    public static String status(int builtLevel, int condition) {
        return status(builtLevel, condition, 30, 60);
    }

    public static String status(int builtLevel, int condition, int offlineBelow, int fullAt) {
        if (builtLevel <= 0) return "尚未建設";
        int functional = functionalLevel(builtLevel, condition, offlineBelow, fullAt);
        if (functional == 0) return "停擺";
        if (functional < builtLevel) return "降效";
        return condition < MAX_CONDITION ? "輕度受損" : "完整";
    }
}
