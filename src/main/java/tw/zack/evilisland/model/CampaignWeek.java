package tw.zack.evilisland.model;

public enum CampaignWeek {
    FRONTIER_SETUP(1, "開拓整備", 1, 0, 0, 0, 1.00, 0),
    SUPPLY_PRESSURE(2, "補給吃緊", 2, 0, 0, 0, 1.00, 0),
    ENEMY_MUSTER(3, "敵軍集結", 1, 1, 0, 1, 1.05, 0),
    DECISIVE_ALERT(4, "決戰警戒", 2, 1, 1, 1, 1.15, 1);

    private final int week;
    private final String display;
    private final int supplyDrain;
    private final int defenseDrain;
    private final int moraleDrain;
    private final int extraEnemies;
    private final double bossHealthMultiplier;
    private final int bonusRemains;

    CampaignWeek(int week, String display, int supplyDrain, int defenseDrain, int moraleDrain,
                 int extraEnemies, double bossHealthMultiplier, int bonusRemains) {
        this.week = week;
        this.display = display;
        this.supplyDrain = supplyDrain;
        this.defenseDrain = defenseDrain;
        this.moraleDrain = moraleDrain;
        this.extraEnemies = extraEnemies;
        this.bossHealthMultiplier = bossHealthMultiplier;
        this.bonusRemains = bonusRemains;
    }

    public String display() { return display; }
    public int supplyDrain() { return supplyDrain; }
    public int defenseDrain() { return defenseDrain; }
    public int moraleDrain() { return moraleDrain; }
    public int extraEnemies() { return extraEnemies; }
    public double bossHealthMultiplier() { return bossHealthMultiplier; }
    public int bonusRemains() { return bonusRemains; }

    public static CampaignWeek fromWeek(int week) {
        for (CampaignWeek value : values()) {
            if (value.week == week) return value;
        }
        return FRONTIER_SETUP;
    }
}
