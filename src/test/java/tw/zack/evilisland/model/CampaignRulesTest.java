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

        List<PatrolContract> board = CampaignRules.board(initial);
        assert board.size() == 3;
        assert new HashSet<>(board).size() == 3;

        CampaignSnapshot completed = CampaignRules.complete(initial, PatrolContract.SUPPLY_ROUTE, 20L);
        assert completed.completedToday();
        assert completed.supply() == 54;
        assert completed.morale() == 51;
        assert CampaignRules.complete(completed, PatrolContract.HOLD_EAST_GATE, 30L).equals(completed);

        CampaignSnapshot nextDay = CampaignRules.advanceTo(completed, 1001L, 40L);
        assert nextDay.day() == 2;
        assert !nextDay.completedToday();
        assert nextDay.supply() == 53;

        CampaignSnapshot neglected = CampaignRules.advanceTo(nextDay, 1002L, 50L);
        assert neglected.defense() == 48;
        assert neglected.intelligence() == 49;
        assert neglected.morale() == 49;

        CampaignSnapshot endOfWeek = new CampaignSnapshot(1, 4, 7, 50, 50, 50, 50,
                2000L, true, "test", 0L);
        CampaignSnapshot nextCycle = CampaignRules.advanceTo(endOfWeek, 2001L, 60L);
        assert nextCycle.cycle() == 2 && nextCycle.week() == 1 && nextCycle.day() == 1;
        assert nextCycle.defense() == 49 && nextCycle.supply() == 48 && nextCycle.morale() == 49;

        assert CampaignWeek.fromWeek(3).extraEnemies() == 1;
        assert CampaignWeek.fromWeek(4).bossHealthMultiplier() == 1.15;

        assert PatrolContract.values().length == 12;
        assert PatrolContract.parse("deep_field_scout") == PatrolContract.DEEP_FIELD_SCOUT;
        System.out.println("CampaignRulesTest passed");
    }
}
