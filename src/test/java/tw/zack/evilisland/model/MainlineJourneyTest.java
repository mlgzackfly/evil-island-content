package tw.zack.evilisland.model;

import tw.zack.evilisland.expedition.ExpeditionScenario;
import tw.zack.evilisland.expedition.ExpeditionScenarioRegistry;
import tw.zack.evilisland.expedition.ExpeditionTeamPolicy;
import tw.zack.evilisland.expedition.ExpeditionCombatDirector;
import tw.zack.evilisland.expedition.ExpeditionTextService;

import java.util.UUID;

public final class MainlineJourneyTest {
    private MainlineJourneyTest() { }

    public static void main(String[] args) {
        JourneySnapshot journey = JourneySnapshot.initial(new UUID(0L, 30L), 1L);
        assert journey.step() == JourneyStep.AWAKEN_QI;
        for (JourneyMilestone milestone : JourneyMilestone.values()) {
            journey = journey.record(milestone, journey.updatedAt() + 1L);
        }
        assert journey.step() == JourneyStep.MAINLINE;
        assert MainlineChapter.values().length == 4;
        assert MainlineChapter.fromWeek(1) == MainlineChapter.FOOTHOLD;
        assert MainlineChapter.fromWeek(4) == MainlineChapter.HOLD_NEW_CITY;
        assert java.util.Arrays.stream(MainlineChapter.values()).allMatch(chapter ->
                !chapter.purpose().isBlank() && !chapter.objective().isBlank());

        ExpeditionScenarioRegistry registry = ExpeditionScenarioRegistry.standard();
        assert registry.size() == ExplorationSite.values().length;
        ExpeditionScenario rongxu = registry.forSite(ExplorationSite.RONGXU_APPROACH);
        assert !rongxu.combatRequired();
        assert rongxu.phaseAfterObjective() == ExpeditionPhase.EXTRACTION;
        ExpeditionScenario east = registry.forSite(ExplorationSite.EASTERN_ROUTE);
        int secureEnemies = east.enemyCount(ExpeditionOperation.LOST_CONVOY, ExpeditionRoute.OLD_ROAD,
                1, 0, ExpeditionStoryChoice.SECURE);
        int connectEnemies = east.enemyCount(ExpeditionOperation.LOST_CONVOY, ExpeditionRoute.OLD_ROAD,
                1, 0, ExpeditionStoryChoice.CONNECT);
        assert secureEnemies < connectEnemies;
        assert east.syncWindowMillis(ExpeditionOperation.LOST_CONVOY, ExpeditionRoute.OLD_ROAD,
                ExpeditionStoryChoice.SECURE) < east.syncWindowMillis(ExpeditionOperation.LOST_CONVOY,
                ExpeditionRoute.OLD_ROAD, ExpeditionStoryChoice.CONNECT);
        assert ExpeditionPhase.OBJECTIVE.canAdvanceTo(ExpeditionPhase.EXTRACTION);
        ExpeditionTeamPolicy teams = new ExpeditionTeamPolicy();
        assert teams.rejection(false, false, false, false) != null;
        assert teams.rejection(true, true, false, false) == null;
        ExpeditionCombatDirector combat = new ExpeditionCombatDirector();
        assert combat.enemyCount(east, ExpeditionOperation.SUPPLY_NODE_SABOTAGE, ExpeditionRoute.OLD_ROAD,
                1, 0, null, ExpeditionKit.DEMOLITION.mask())
                < east.enemyCount(ExpeditionOperation.SUPPLY_NODE_SABOTAGE, ExpeditionRoute.OLD_ROAD,
                1, 0, null);
        ExpeditionTextService text = new ExpeditionTextService();
        assert text.stageInstruction(rongxu, ExpeditionPhase.EXTRACTION, ExpeditionRoute.OLD_ROAD,
                true, 0).contains("地下領域");
        System.out.println("MainlineJourneyTest passed");
    }
}
