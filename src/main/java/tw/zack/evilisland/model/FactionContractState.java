package tw.zack.evilisland.model;

import java.util.Locale;

public enum FactionContractState {
    ACTIVE("active"),
    READY("ready"),
    CONFLICT("conflict"),
    RESOLVED("resolved");

    private final String id;

    FactionContractState(String id) { this.id = id; }
    public String id() { return id; }

    public static FactionContractState parse(String value) {
        if (value == null) return ACTIVE;
        String normalized = value.toLowerCase(Locale.ROOT);
        for (FactionContractState state : values()) {
            if (state.id.equals(normalized)) return state;
        }
        return ACTIVE;
    }
}
