package tw.zack.evilisland.model;

public enum ExpeditionOutcome {
    COMPLETE("complete", "完整撤離"),
    PARTIAL("partial", "帶回部分成果"),
    WITHDRAWN("withdrawn", "主動撤退"),
    ABANDONED("abandoned", "行動中止");

    private final String id;
    private final String display;

    ExpeditionOutcome(String id, String display) {
        this.id = id;
        this.display = display;
    }

    public String id() { return id; }
    public String display() { return display; }

    public ExpeditionPhase terminalPhase() {
        return switch (this) {
            case COMPLETE -> ExpeditionPhase.RESOLVED;
            case PARTIAL -> ExpeditionPhase.PARTIAL;
            case WITHDRAWN -> ExpeditionPhase.WITHDRAWN;
            case ABANDONED -> ExpeditionPhase.ABANDONED;
        };
    }

    public static ExpeditionOutcome parse(String value) {
        if (value == null || value.isBlank()) return null;
        for (ExpeditionOutcome outcome : values()) if (outcome.id.equalsIgnoreCase(value)) return outcome;
        return null;
    }
}
