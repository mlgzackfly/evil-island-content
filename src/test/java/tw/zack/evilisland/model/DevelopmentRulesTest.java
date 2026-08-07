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
        System.out.println("DevelopmentRulesTest passed");
    }
}
