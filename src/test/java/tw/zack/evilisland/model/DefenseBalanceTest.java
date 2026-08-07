package tw.zack.evilisland.model;

public final class DefenseBalanceTest {
    private DefenseBalanceTest() {
    }

    public static void main(String[] args) {
        assert DefenseBalance.enemyCount(2, 1, 1) == 2;
        assert DefenseBalance.enemyCount(4, 1, 2) == 8;
        assert DefenseBalance.enemyCount(4, 3, 1) == 8;
        assert DefenseBalance.enemyCount(4, 3, 2) == 12;
        assert !DefenseBalance.failed(2, 3);
        assert DefenseBalance.failed(3, 3);
        assert DefenseBalance.hasNextWave(2, 3);
        assert !DefenseBalance.hasNextWave(3, 3);
        System.out.println("DefenseBalanceTest passed");
    }
}
