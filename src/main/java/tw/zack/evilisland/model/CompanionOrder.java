package tw.zack.evilisland.model;

public enum CompanionOrder {
    FOLLOW("follow", "跟隨"),
    HOLD("hold", "原地待命"),
    EXECUTE("execute", "執行目標");

    private final String id;
    private final String display;

    CompanionOrder(String id, String display) {
        this.id = id;
        this.display = display;
    }

    public String id() { return id; }
    public String display() { return display; }

    public CompanionOrder next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static CompanionOrder parse(String value) {
        if (value == null) return FOLLOW;
        for (CompanionOrder order : values()) if (order.id.equalsIgnoreCase(value)) return order;
        return FOLLOW;
    }
}
