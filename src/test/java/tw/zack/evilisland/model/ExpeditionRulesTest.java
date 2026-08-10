package tw.zack.evilisland.model;

public final class ExpeditionRulesTest {
    private ExpeditionRulesTest() { }

    public static void main(String[] args) {
        assert ExpeditionRules.operation(0) == ExpeditionOperation.LOST_CONVOY;
        assert ExpeditionRules.operation(4) == ExpeditionOperation.LOST_CONVOY;
        assert ExpeditionRules.operation(-1) == ExpeditionOperation.CASUALTY_EVACUATION;

        assert ExpeditionRules.requiredClues(ExpeditionRoute.OLD_ROAD) == 2;
        assert ExpeditionRules.requiredClues(ExpeditionRoute.RIVERBED) == 3;
        assert ExpeditionRules.requiredClues(ExpeditionOperation.LOST_CONVOY,
                ExpeditionRoute.OLD_ROAD) == 3;
        assert ExpeditionRules.syncWindowMillis(ExpeditionOperation.SUPPLY_NODE_SABOTAGE,
                ExpeditionRoute.RIDGE) == 20_000L;
        assert ExpeditionRules.syncWindowMillis(ExpeditionOperation.LOST_CONVOY,
                ExpeditionRoute.OLD_ROAD) == 20_000L;

        int easy = ExpeditionRules.enemyCount(ExpeditionOperation.CASUALTY_EVACUATION,
                ExpeditionRoute.RIVERBED, 1, 0);
        int hard = ExpeditionRules.enemyCount(ExpeditionOperation.BLOCKADE_INFILTRATION,
                ExpeditionRoute.RIDGE, 2, 1);
        assert easy == 2;
        assert hard == 8;

        assert ExpeditionRules.withdrawalOutcome(ExpeditionPhase.APPROACH, 0, 0)
                == ExpeditionOutcome.WITHDRAWN;
        assert ExpeditionRules.withdrawalOutcome(ExpeditionPhase.INVESTIGATING, 2, 0)
                == ExpeditionOutcome.PARTIAL;
        assert ExpeditionRules.withdrawalOutcome(ExpeditionPhase.OBJECTIVE, 0, 0)
                == ExpeditionOutcome.PARTIAL;

        assert ExpeditionPhase.APPROACH.canAdvanceTo(ExpeditionPhase.INVESTIGATING);
        assert !ExpeditionPhase.APPROACH.canAdvanceTo(ExpeditionPhase.ESCALATION);
        assert !ExpeditionPhase.RESOLVED.running();
        assert ExpeditionRules.regionDelta(ExpeditionOutcome.COMPLETE, 2) == 9;
        assert ExpeditionRules.regionDelta(ExpeditionOutcome.ABANDONED, 1) == -2;
        assert ExpeditionDirector.kitCapacity(1) == 3;
        assert ExpeditionDirector.kitCapacity(2) == 2;
        int twoKits = ExpeditionKit.MEDICAL.mask() | ExpeditionKit.SCOUTING.mask();
        int threeKits = twoKits | ExpeditionKit.PROVISIONS.mask();
        assert ExpeditionDirector.validLoadout(twoKits, 2);
        assert !ExpeditionDirector.validLoadout(threeKits, 2);
        assert ExpeditionDirector.validLoadout(threeKits, 1);
        assert ExpeditionDirector.event(42L, 0) != ExpeditionDirector.event(42L, 1);
        assert ExpeditionDirector.resolve(ExpeditionRouteEvent.WOUNDED_SCOUT, true).scoreDelta() == 2;
        assert ExpeditionDirector.resolve(ExpeditionRouteEvent.ENEMY_PATROL, false).alertDelta() == 1;
        System.out.println("ExpeditionRulesTest passed");
    }
}
