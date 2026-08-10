package tw.zack.evilisland.model;

public enum ExpeditionPhase {
    PREPARING("preparing", "整備"),
    APPROACH("approach", "沿線推進"),
    INVESTIGATING("investigating", "現場調查"),
    OBJECTIVE("objective", "執行目標"),
    ESCALATION("escalation", "敵襲升級"),
    EXTRACTION("extraction", "撤離"),
    RESOLVED("resolved", "完成"),
    PARTIAL("partial", "部分完成"),
    WITHDRAWN("withdrawn", "主動撤退"),
    ABANDONED("abandoned", "行動中止");

    private final String id;
    private final String display;

    ExpeditionPhase(String id, String display) {
        this.id = id;
        this.display = display;
    }

    public String id() { return id; }
    public String display() { return display; }

    public boolean running() {
        return ordinal() <= EXTRACTION.ordinal();
    }

    public boolean canAdvanceTo(ExpeditionPhase next) {
        return running() && next != null && (next.ordinal() == ordinal() + 1
                || (this == OBJECTIVE && next == EXTRACTION) || !next.running());
    }

    public static ExpeditionPhase parse(String value) {
        if (value == null) return null;
        for (ExpeditionPhase phase : values()) if (phase.id.equalsIgnoreCase(value)) return phase;
        return null;
    }
}
