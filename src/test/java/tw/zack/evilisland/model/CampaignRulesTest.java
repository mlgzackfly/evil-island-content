package tw.zack.evilisland.model;

import java.util.HashSet;
import java.util.List;

public final class CampaignRulesTest {
    private CampaignRulesTest() {
    }

    public static void main(String[] args) {
        CampaignSnapshot initial = CampaignSnapshot.initial(1000L, 10L);
        assert initial.week() == 1 && initial.day() == 1;
        assert initial.defense() == 50 && initial.supply() == 50;

        List<MissionContract> board = CampaignRules.board(initial);
        assert board.size() == 3;
        assert new HashSet<>(board).size() == 3;
        assert board.stream().map(MissionContract::missionType).distinct().count() == 3;
        assert board.stream().anyMatch(contract -> contract.missionType() == MissionType.PATROL);
        HashSet<MissionType> rotatedTypes = new HashSet<>();
        for (int day = 0; day < 8; day++) {
            CampaignSnapshot rotating = new CampaignSnapshot(1, day / 7 + 1, day % 7 + 1,
                    50, 50, 50, 50, 1000L + day, false, "", false,
                    CampaignStrategy.NONE, 0, 0, 0, 10L);
            List<MissionContract> rotatingBoard = CampaignRules.board(rotating);
            assert rotatingBoard.size() == 3;
            assert rotatingBoard.stream().map(MissionContract::missionType).distinct().count() == 3;
            rotatingBoard.forEach(contract -> rotatedTypes.add(contract.missionType()));
        }
        assert rotatedTypes.equals(new HashSet<>(List.of(MissionType.values())));
        List<MissionContract> tutorialBoard = CampaignRules.patrolBoard(initial);
        assert tutorialBoard.stream().allMatch(contract -> contract.missionType() == MissionType.PATROL);
        assert new HashSet<>(tutorialBoard).size() == 3;

        CampaignSnapshot completed = CampaignRules.complete(initial, MissionContract.SUPPLY_ROUTE, 20L);
        assert completed.completedToday();
        assert completed.supply() == 54;
        assert completed.morale() == 51;
        assert CampaignRules.complete(completed, MissionContract.HOLD_EAST_GATE, 30L).equals(completed);

        CampaignSnapshot nextDay = CampaignRules.advanceTo(completed, 1001L, 40L);
        assert nextDay.day() == 2;
        assert !nextDay.completedToday();
        assert nextDay.supply() == 53;

        CampaignSnapshot neglected = CampaignRules.advanceTo(nextDay, 1002L, 50L);
        assert neglected.defense() == 48;
        assert neglected.intelligence() == 49;
        assert neglected.morale() == 49;

        CampaignSnapshot endOfWeek = new CampaignSnapshot(1, 4, 7, 50, 50, 50, 50,
                2000L, true, "test", true, CampaignStrategy.FORTIFY, 2, 1, 1, 0L);
        CampaignSnapshot nextCycle = CampaignRules.advanceTo(endOfWeek, 2001L, 60L);
        assert nextCycle.cycle() == 2 && nextCycle.week() == 1 && nextCycle.day() == 1;
        assert nextCycle.defense() == 49 && nextCycle.supply() == 48 && nextCycle.morale() == 49;
        assert nextCycle.fortifyPoints() == 0 && !nextCycle.weeklyResolved();

        CampaignSnapshot fortified = CampaignRules.resolveWeekly(initial, CampaignStrategy.FORTIFY, 20L);
        assert fortified.weeklyResolved() && fortified.weeklyStrategy() == CampaignStrategy.FORTIFY;
        assert fortified.defense() == 56 && fortified.supply() == 48 && fortified.fortifyPoints() == 1;
        assert CampaignRules.resolveWeekly(fortified, CampaignStrategy.RECON, 30L).equals(fortified);

        CampaignSnapshot unresolvedWeek = new CampaignSnapshot(1, 1, 7, 50, 50, 50, 50,
                3000L, true, "test", false, CampaignStrategy.NONE, 0, 0, 0, 0L);
        CampaignSnapshot penalized = CampaignRules.advanceTo(unresolvedWeek, 3001L, 40L);
        assert penalized.week() == 2 && penalized.day() == 1;
        assert penalized.defense() == 44 && penalized.morale() == 48;

        CampaignSnapshot simulation = CampaignSnapshot.initial(4000L, 0L);
        for (int day = 0; day < 28; day++) {
            if (!simulation.weeklyResolved()) {
                CampaignStrategy strategy = CampaignStrategy.values()[1 + simulation.week() % 3];
                simulation = CampaignRules.resolveWeekly(simulation, strategy, day);
            }
            simulation = CampaignRules.advanceTo(simulation, simulation.epochDay() + 1, day + 1);
        }
        assert simulation.cycle() == 2 && simulation.week() == 1 && simulation.day() == 1;
        assert !simulation.weeklyResolved();
        assert simulation.fortifyPoints() == 0 && simulation.provisionPoints() == 0
                && simulation.reconPoints() == 0;

        CampaignSnapshot failedDefense = CampaignRules.failDefense(initial, 50L);
        assert failedDefense.defense() == 45 && failedDefense.morale() == 47;

        CampaignSnapshot crisisResolved = CampaignRules.adjustMetric(initial, CampaignMetric.SUPPLY, 60, 51L);
        assert crisisResolved.supply() == 100;
        CampaignSnapshot crisisExpired = CampaignRules.adjustMetric(initial, CampaignMetric.INTELLIGENCE, -60, 52L);
        assert crisisExpired.intelligence() == 0;
        assert CampaignRules.adjustMetric(initial, null, 4, 53L).equals(initial);

        assert CampaignWeek.fromWeek(3).extraEnemies() == 1;
        assert CampaignWeek.fromWeek(4).bossHealthMultiplier() == 1.15;

        assert MissionContract.values().length == 37;
        assert MissionContract.parse("deep_field_scout") == MissionContract.DEEP_FIELD_SCOUT;
        assert MissionContract.parse("timber_requisition").objectiveAmount() == 16;
        assert MissionContract.parse("north_ridge_observation").missionType() == MissionType.SCOUT;
        assert MissionContract.parse("eastern_medic_escort").missionType() == MissionType.ESCORT;
        assert MissionContract.parse("wuji_trace_rescue").missionType() == MissionType.RESCUE;
        assert MissionContract.parse("four_way_siege").missionType() == MissionType.DEFENSE;
        assert MissionContract.FOUR_WAY_SIEGE.defenseEntrances() == 4;
        assert MissionContract.FOUR_WAY_SIEGE.defenseWaves() == 3;
        assert BossVariant.fromStrategy(CampaignStrategy.RECON) == BossVariant.HUNTED_COMMANDER;
        assert MissionBalance.sharedObjectiveAmount(16, 1) == 16;
        assert MissionBalance.sharedObjectiveAmount(16, 2) == 24;
        assert MissionBalance.sharedObjectiveAmount(16, 2, 1.25) == 20;
        assert MissionBalance.regularHealth(2.5) == 1.60;
        assert MissionBalance.regularDamage(0.1) == 0.90;
        assert MissionBalance.bossHealth(3.0) == 1.80;
        System.out.println("CampaignRulesTest passed");
    }
}
