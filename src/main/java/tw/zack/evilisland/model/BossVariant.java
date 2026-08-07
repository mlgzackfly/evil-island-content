package tw.zack.evilisland.model;

public enum BossVariant {
    SIEGE_BREAKER("破陣刑天", 1.15, 0.92, 0),
    SUPPLY_RAIDER("劫糧刑天", 1.00, 1.00, 2),
    HUNTED_COMMANDER("負創刑天", 0.88, 1.12, 1);

    private final String display;
    private final double healthMultiplier;
    private final double damageMultiplier;
    private final int extraZaochi;

    BossVariant(String display, double healthMultiplier, double damageMultiplier, int extraZaochi) {
        this.display = display;
        this.healthMultiplier = healthMultiplier;
        this.damageMultiplier = damageMultiplier;
        this.extraZaochi = extraZaochi;
    }

    public String display() { return display; }
    public double healthMultiplier() { return healthMultiplier; }
    public double damageMultiplier() { return damageMultiplier; }
    public int extraZaochi() { return extraZaochi; }

    public static BossVariant fromStrategy(CampaignStrategy strategy) {
        return switch (strategy) {
            case PROVISION -> SUPPLY_RAIDER;
            case RECON -> HUNTED_COMMANDER;
            case NONE, FORTIFY -> SIEGE_BREAKER;
        };
    }
}
