package tw.zack.evilisland.model;

import java.util.Locale;

public enum CrisisSceneState {
    ACTIVE("active", "危機中"),
    RESOLVED("resolved", "已處理"),
    EXPIRED("expired", "已惡化"),
    CONFLICT("conflict", "外部變動衝突");

    private final String id;
    private final String display;

    CrisisSceneState(String id, String display) {
        this.id = id;
        this.display = display;
    }

    public String id() { return id; }
    public String display() { return display; }

    public static CrisisSceneState parse(String value) {
        if (value == null) return null;
        String normalized = value.toLowerCase(Locale.ROOT);
        for (CrisisSceneState state : values()) {
            if (state.id.equals(normalized)) return state;
        }
        return null;
    }
}
