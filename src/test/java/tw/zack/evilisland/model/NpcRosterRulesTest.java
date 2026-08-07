package tw.zack.evilisland.model;

public final class NpcRosterRulesTest {
    private NpcRosterRulesTest() {
    }

    public static void main(String[] args) {
        NpcRosterSnapshot initial = new NpcRosterSnapshot(NpcRole.YANGWU, 30, 0L, 1000L);
        NpcRosterSnapshot recovered = NpcRosterRules.normalize(initial, 31000L, 10000L);
        assert recovered.fatigue() == 27;
        assert recovered.updatedAt() == 31000L;

        NpcRosterSnapshot tired = NpcRosterRules.completeMission(recovered, 60, 32000L);
        assert tired.fatigue() == 87;
        assert !tired.available(32000L, 80);

        NpcRosterSnapshot injured = NpcRosterRules.injure(tired, 60000L, 33000L);
        assert injured.injured(40000L);
        assert !injured.available(40000L, 100);

        NpcRosterSnapshot treated = NpcRosterRules.treat(injured, 35, 41000L);
        assert !treated.injured(41000L);
        assert treated.fatigue() == 52;
        assert treated.available(41000L, 80);

        NpcRosterSnapshot capped = NpcRosterRules.completeMission(treated, 1000, 42000L);
        assert capped.fatigue() == 100;
        System.out.println("NpcRosterRulesTest passed");
    }
}
