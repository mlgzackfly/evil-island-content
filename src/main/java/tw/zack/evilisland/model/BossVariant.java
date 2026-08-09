package tw.zack.evilisland.model;

public enum BossVariant {
    SIEGE_BREAKER("siege_breaker", "破陣刑天", "震地範圍擴大", 1.15, 0.92, 0, 1.30, 1.00, 1.00),
    SUPPLY_RAIDER("supply_raider", "劫糧刑天", "統軍範圍與護衛速度提高", 1.00, 1.00, 2, 1.00, 1.35, 1.00),
    HUNTED_COMMANDER("hunted_commander", "負創刑天", "衝鋒冷卻縮短", 0.88, 1.12, 1, 1.00, 1.00, 0.65);

    private final String id;
    private final String display;
    private final String behavior;
    private final double healthMultiplier;
    private final double damageMultiplier;
    private final int extraZaochi;
    private final double slamRadiusMultiplier;
    private final double commandRadiusMultiplier;
    private final double chargeCooldownMultiplier;

    BossVariant(String id, String display, String behavior, double healthMultiplier, double damageMultiplier,
                int extraZaochi, double slamRadiusMultiplier, double commandRadiusMultiplier,
                double chargeCooldownMultiplier) {
        this.id = id;
        this.display = display;
        this.behavior = behavior;
        this.healthMultiplier = healthMultiplier;
        this.damageMultiplier = damageMultiplier;
        this.extraZaochi = extraZaochi;
        this.slamRadiusMultiplier = slamRadiusMultiplier;
        this.commandRadiusMultiplier = commandRadiusMultiplier;
        this.chargeCooldownMultiplier = chargeCooldownMultiplier;
    }

    public String id() { return id; }
    public String display() { return display; }
    public String behavior() { return behavior; }
    public double healthMultiplier() { return healthMultiplier; }
    public double damageMultiplier() { return damageMultiplier; }
    public int extraZaochi() { return extraZaochi; }
    public double slamRadiusMultiplier() { return slamRadiusMultiplier; }
    public double commandRadiusMultiplier() { return commandRadiusMultiplier; }
    public double chargeCooldownMultiplier() { return chargeCooldownMultiplier; }

    public static BossVariant parse(String value) {
        if (value == null) return null;
        for (BossVariant variant : values()) if (variant.id.equalsIgnoreCase(value)) return variant;
        return null;
    }

    public static BossVariant fromStrategy(CampaignStrategy strategy) {
        return switch (strategy) {
            case PROVISION -> SUPPLY_RAIDER;
            case RECON -> HUNTED_COMMANDER;
            case NONE, FORTIFY -> SIEGE_BREAKER;
        };
    }
}
