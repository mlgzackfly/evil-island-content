package tw.zack.evilisland.model;

import java.util.Locale;

public enum RegionState {
    STABLE("stable", "安定"),
    TENSE("tense", "緊張"),
    LOST("lost", "失守"),
    RECOVERING("recovering", "收復中");

    private final String id;
    private final String display;

    RegionState(String id, String display) {
        this.id = id;
        this.display = display;
    }

    public String id() { return id; }
    public String display() { return display; }

    public static RegionState parse(String value) {
        if (value == null) return null;
        String normalized = value.toLowerCase(Locale.ROOT);
        for (RegionState state : values()) {
            if (state.id.equals(normalized)) return state;
        }
        return null;
    }
}
