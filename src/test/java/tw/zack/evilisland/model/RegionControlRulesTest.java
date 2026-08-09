package tw.zack.evilisland.model;

public final class RegionControlRulesTest {
    private RegionControlRulesTest() { }

    public static void main(String[] args) {
        assert RegionControlRules.stateAfter(RegionState.TENSE, 20) == RegionState.LOST;
        assert RegionControlRules.stateAfter(RegionState.LOST, 21) == RegionState.RECOVERING;
        assert RegionControlRules.stateAfter(RegionState.RECOVERING, 69) == RegionState.RECOVERING;
        assert RegionControlRules.stateAfter(RegionState.RECOVERING, 70) == RegionState.STABLE;
        assert RegionControlRules.stateAfter(RegionState.STABLE, 60) == RegionState.TENSE;
        assert RegionControlRules.eventDelta(LivingEventState.ACTIVE, LivingEventApproach.NONE) == -5;
        assert RegionControlRules.eventDelta(LivingEventState.EXPIRED, LivingEventApproach.NONE) == -24;
        assert RegionControlRules.eventDelta(LivingEventState.RESOLVED, LivingEventApproach.FIELD)
                > RegionControlRules.eventDelta(LivingEventState.RESOLVED, LivingEventApproach.LOGISTICS);
        assert RegionControlRules.missionDelta(2) > RegionControlRules.missionDelta(1);
        assert RegionControlRules.campCapacity(2) > RegionControlRules.campCapacity(1);
        RegionControlSnapshot initial = RegionControlSnapshot.initial(ExplorationSite.EASTERN_ROUTE, 10L);
        RegionControlSnapshot lost = initial.adjust(-50, 20L);
        assert lost.state() == RegionState.LOST && lost.stability() == 10;
        RegionControlSnapshot recovering = lost.adjust(20, 30L);
        assert recovering.state() == RegionState.RECOVERING && recovering.stability() == 30;
        assert !RegionControlRules.campOperational(recovering.withCamp(1, 0, 40L));
        assert RegionControlRules.campOperational(recovering.withCamp(1, 1, 40L));
        System.out.println("RegionControlRulesTest passed");
    }
}
