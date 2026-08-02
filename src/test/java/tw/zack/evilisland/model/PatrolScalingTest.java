package tw.zack.evilisland.model;

public final class PatrolScalingTest {
    private PatrolScalingTest() {
    }

    public static void main(String[] args) {
        PatrolScaling solo = PatrolScaling.forPlayers(1, 3, 2, 0.35, 0.12, 0.45, 0.15);
        assert solo.playerCount() == 1;
        assert solo.companion();
        assert solo.zaochiCount() == 3;
        assert solo.zaochiHealthMultiplier() == 1.0;
        assert solo.bossHealthMultiplier() == 1.0;

        PatrolScaling duo = PatrolScaling.forPlayers(2, 3, 2, 0.35, 0.12, 0.45, 0.15);
        assert duo.playerCount() == 2;
        assert !duo.companion();
        assert duo.zaochiCount() == 5;
        assert Math.abs(duo.zaochiHealthMultiplier() - 1.35) < 0.0001;
        assert Math.abs(duo.zaochiDamageMultiplier() - 1.12) < 0.0001;
        assert Math.abs(duo.bossHealthMultiplier() - 1.45) < 0.0001;
        assert Math.abs(duo.bossDamageMultiplier() - 1.15) < 0.0001;

        PatrolScaling clamped = PatrolScaling.forPlayers(9, 0, -2, -1.0, -1.0, -1.0, -1.0);
        assert clamped.playerCount() == 2;
        assert clamped.zaochiCount() == 1;
        assert clamped.zaochiHealthMultiplier() == 1.0;

        assert PatrolPhase.parse("patrol") == PatrolPhase.PATROL;
        assert PatrolPhase.parse("BOSS_READY") == PatrolPhase.BOSS_READY;
        assert PatrolPhase.parse("complete_pending") == PatrolPhase.COMPLETE_PENDING;
        assert PatrolPhase.parse("unknown") == null;

        System.out.println("PatrolScalingTest passed");
    }
}
