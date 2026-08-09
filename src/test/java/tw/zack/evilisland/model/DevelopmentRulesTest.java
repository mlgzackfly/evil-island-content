package tw.zack.evilisland.model;

import java.util.Map;

public final class DevelopmentRulesTest {
    private DevelopmentRulesTest() {
    }

    public static void main(String[] args) {
        assert CityProject.values().length == 5;
        assert Faction.values().length == 6;
        assert ExplorationSite.values().length == 5;
        assert EventChain.values().length == 3;
        assert CityRoute.values().length == 3;
        assert CityRouteRules.canChoose(3, false);
        assert !CityRouteRules.canChoose(4, false);
        assert !CityRouteRules.canChoose(2, true);
        assert CityRouteRules.deploymentScoutRequirement(CityRoute.EXPEDITION) == 1;
        assert CityRouteRules.defenseModifier(CityRoute.FORTRESS) == -1;
        assert CityRouteRules.cityQiBonus(CityRoute.QI_CIVIC) == 1;
        assert FactionContract.forWeek(1, CityRoute.FORTRESS) == FactionContract.QUANRONG_HUNT;
        assert FactionContract.forWeek(1, CityRoute.EXPEDITION) == FactionContract.NAJIN_CARAVAN;
        assert FactionContract.forWeek(1, CityRoute.QI_CIVIC) == FactionContract.MAO_SETTLEMENT;
        assert CityRouteRules.projectCost(CityProject.WALLS, 1, CityRoute.FORTRESS)
                .get(WorldResource.MASONRY) < CityProject.WALLS.costForLevel(1).get(WorldResource.MASONRY);
        assert EventChain.SAFE_ROUTE.requiredType(0) == MissionType.SCOUT;
        assert EventChain.SAFE_ROUTE.requiredType(3) == null;
        assert DevelopmentRules.missionYield(MissionType.ESCORT, 3, true)
                .get(WorldResource.PROVISIONS) == 2;
        assert DevelopmentRules.missionYield(MissionType.PATROL, 4, false).isEmpty();
        assert DevelopmentRules.techniqueRequirement(1) == 12;
        assert DevelopmentRules.carryOverResource(30) == 12;
        assert DevelopmentRules.defenseEnemyPerEntranceModifier(2, false, false) == 0;
        assert DevelopmentRules.defenseEnemyPerEntranceModifier(3, false, false) == 1;
        assert DevelopmentRules.defenseEnemyPerEntranceModifier(3, true, true) == -1;
        assert DevelopmentRules.bossEscortModifier(4, 0, 3) == 1;
        assert DevelopmentRules.bossEscortModifier(4, 3, 3) == -1;
        assert DevelopmentRules.ending(Map.of(), Map.of(), 3, 4).equals("遠路重開");
        assert DevelopmentRules.ending(Map.of(CityProject.WALLS, 3, CityProject.WORKSHOP, 3,
                CityProject.SCOUT_POST, 2), Map.of(), 0, 0).equals("新城固守");
        assert ProjectConditionRules.functionalLevel(3, 100) == 3;
        assert ProjectConditionRules.functionalLevel(3, 59) == 2;
        assert ProjectConditionRules.functionalLevel(3, 29) == 0;
        assert ProjectConditionRules.repairedCondition(90) == 100;
        assert ProjectConditionRules.repairCost(CityProject.WALLS).get(WorldResource.MASONRY) == 3;
        assert ProjectConditionRules.defenseFailureDamage(3, 3).containsKey(CityProject.AIR_DEFENSE);
        assert ProjectConditionRules.status(2, 40).equals("降效");
        System.out.println("DevelopmentRulesTest passed");
    }
}
