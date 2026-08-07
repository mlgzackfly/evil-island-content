package tw.zack.evilisland.model;

import java.util.Locale;

public enum ContractResolution {
    NONE("none", "尚未決議"),
    COOPERATE("cooperate", "合作"),
    WITHDRAW("withdraw", "保持距離"),
    CONFLICT("conflict", "衝突");

    private final String id;
    private final String display;

    ContractResolution(String id, String display) {
        this.id = id;
        this.display = display;
    }

    public String id() { return id; }
    public String display() { return display; }

    public static ContractResolution parse(String value) {
        if (value == null) return NONE;
        String normalized = value.toLowerCase(Locale.ROOT);
        for (ContractResolution resolution : values()) {
            if (resolution.id.equals(normalized)) return resolution;
        }
        return NONE;
    }
}
