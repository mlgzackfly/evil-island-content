package tw.zack.evilisland.model;

import java.util.Locale;

public enum AcceptanceState {
    PREPARING("preparing"),
    PREVIEW("preview"),
    RESTORED("restored"),
    FAILED("failed");

    private final String id;

    AcceptanceState(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public boolean active() {
        return this == PREPARING || this == PREVIEW;
    }

    public static AcceptanceState parse(String value) {
        if (value == null) return FAILED;
        String normalized = value.toLowerCase(Locale.ROOT);
        for (AcceptanceState state : values()) {
            if (state.id.equals(normalized)) return state;
        }
        return FAILED;
    }
}
