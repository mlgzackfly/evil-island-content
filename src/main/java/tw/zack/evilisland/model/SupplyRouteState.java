package tw.zack.evilisland.model;

import java.util.Locale;

public enum SupplyRouteState {
    TRANSIT("transit", "運送中"),
    ARRIVED("arrived", "等待接貨"),
    COMPLETED("completed", "已完成"),
    EXPIRED("expired", "已逾期"),
    CANCELLED("cancelled", "已取消");

    private final String id;
    private final String display;

    SupplyRouteState(String id, String display) {
        this.id = id;
        this.display = display;
    }

    public String id() { return id; }
    public String display() { return display; }
    public boolean active() { return this == TRANSIT || this == ARRIVED; }

    public static SupplyRouteState parse(String value) {
        if (value == null) return null;
        String normalized = value.toLowerCase(Locale.ROOT);
        for (SupplyRouteState state : values()) if (state.id.equals(normalized)) return state;
        return null;
    }
}
