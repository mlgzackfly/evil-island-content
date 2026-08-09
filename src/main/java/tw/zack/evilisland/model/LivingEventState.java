package tw.zack.evilisland.model;

import java.util.Locale;

public enum LivingEventState {
    ACTIVE("active", "處理中"),
    RESOLVED("resolved", "已處理"),
    EXPIRED("expired", "已惡化");

    private final String id;
    private final String display;

    LivingEventState(String id, String display) {
        this.id = id;
        this.display = display;
    }

    public String id() { return id; }
    public String display() { return display; }

    public static LivingEventState parse(String value) {
        if (value == null) return null;
        String normalized = value.toLowerCase(Locale.ROOT);
        for (LivingEventState state : values()) {
            if (state.id.equals(normalized)) return state;
        }
        return null;
    }
}
