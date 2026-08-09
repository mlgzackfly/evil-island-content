package tw.zack.evilisland.model;

import java.util.List;
import java.util.UUID;

public final class LivingEventRulesTest {
    private LivingEventRulesTest() {
    }

    public static void main(String[] args) {
        assert LivingEventType.values().length == 12;
        assert LivingEventArc.values().length == 4;
        assert java.util.Arrays.stream(LivingEventType.values()).allMatch(type -> type.logisticsCost().size() == 2);

        LivingEventType selected = LivingEventRules.select(1, 2, 3, List.of());
        assert selected.availableInWeek(2);
        assert LivingEventRules.select(1, 2, 3, List.of(selected)) != selected;

        LivingEventSnapshot active = snapshot(LivingEventType.WIND_RAID, LivingEventState.ACTIVE, 10L);
        List<MissionContract> base = List.of(MissionContract.EAST_CLEARANCE,
                MissionContract.TIMBER_REQUISITION, MissionContract.NORTH_RIDGE_OBSERVATION);
        List<MissionContract> board = LivingEventRules.missionBoard(base, active);
        assert board.size() == 3;
        assert board.contains(MissionContract.SIGNAL_POST_DEFENSE);
        assert board.stream().map(MissionContract::missionType).distinct().count() == 3;

        LivingEventSnapshot firstExpiry = snapshot(LivingEventType.TIDAL_WARNING, LivingEventState.EXPIRED, 10L);
        LivingEventSnapshot secondExpiry = snapshot(LivingEventType.WIND_RAID, LivingEventState.EXPIRED, 20L);
        LivingEventSnapshot resolved = snapshot(LivingEventType.SHORELINE_BREACH, LivingEventState.RESOLVED, 30L);
        assert LivingEventRules.regionPressure(List.of(firstExpiry, secondExpiry),
                ExplorationSite.DRAGON_COAST, 2) == 2;
        assert LivingEventRules.regionPressure(List.of(firstExpiry, secondExpiry, resolved),
                ExplorationSite.DRAGON_COAST, 2) == 1;
        assert LivingEventRules.missionEnemyModifier(active, MissionContract.SIGNAL_POST_DEFENSE, 0) == 1;
        assert LivingEventRules.missionEnemyModifier(active, MissionContract.SIGNAL_POST_DEFENSE, 3) == 2;
        assert LivingEventRules.missionEnemyModifier(active, MissionContract.EAST_CLEARANCE, 3) == 0;
        assert LivingEventRules.arcProgress(List.of(resolved), LivingEventArc.COASTAL_WARNING, 2) == 1;

        LivingEventSnapshot completed = active.resolve(LivingEventApproach.FIELD, 2, 40L);
        assert completed.state() == LivingEventState.RESOLVED;
        assert completed.participants() == 2;
        assert completed.updatedAt() > active.updatedAt();
        System.out.println("LivingEventRulesTest passed");
    }

    private static LivingEventSnapshot snapshot(LivingEventType type, LivingEventState state, long createdAt) {
        return new LivingEventSnapshot(UUID.nameUUIDFromBytes((type.id() + createdAt).getBytes()), type, state,
                state == LivingEventState.RESOLVED ? LivingEventApproach.FIELD : LivingEventApproach.NONE,
                2, 3, 4, 10L, 13L, state == LivingEventState.RESOLVED ? 1 : 0,
                createdAt, state == LivingEventState.ACTIVE ? 0L : createdAt + 1, createdAt + 2);
    }
}
