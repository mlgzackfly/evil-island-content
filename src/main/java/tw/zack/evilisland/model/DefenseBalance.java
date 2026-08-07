package tw.zack.evilisland.model;

public final class DefenseBalance {
    private DefenseBalance() {
    }

    public static int enemyCount(int entrances, int wave, int players) {
        int safeEntrances = Math.max(2, Math.min(4, entrances));
        int safeWave = Math.max(1, wave);
        int safePlayers = Math.max(1, Math.min(2, players));
        int perEntrance = 1 + (safeWave >= 3 ? 1 : 0) + (safePlayers - 1);
        return safeEntrances * perEntrance;
    }

    public static boolean failed(int breaches, int maximumBreaches) {
        return breaches >= Math.max(1, maximumBreaches);
    }

    public static boolean hasNextWave(int currentWave, int totalWaves) {
        return Math.max(1, currentWave) < Math.max(1, totalWaves);
    }
}
